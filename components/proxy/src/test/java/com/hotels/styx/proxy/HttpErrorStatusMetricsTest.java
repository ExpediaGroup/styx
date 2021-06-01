/*
  Copyright (C) 2013-2021 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.server.HttpErrorStatusListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.stream.Stream;

import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_CONNECTION_REFUSED;
import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_CONNECTION_TIMED_OUT;
import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_SERVER_TIMED_OUT;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.CONFLICT;
import static com.hotels.styx.api.HttpResponseStatus.EXPECTATION_FAILED;
import static com.hotels.styx.api.HttpResponseStatus.FORBIDDEN;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.GONE;
import static com.hotels.styx.api.HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.LENGTH_REQUIRED;
import static com.hotels.styx.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static com.hotels.styx.api.HttpResponseStatus.NOT_ACCEPTABLE;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.PAYMENT_REQUIRED;
import static com.hotels.styx.api.HttpResponseStatus.PRECONDITION_FAILED;
import static com.hotels.styx.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static com.hotels.styx.api.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_URI_TOO_LONG;
import static com.hotels.styx.api.HttpResponseStatus.UNAUTHORIZED;
import static com.hotels.styx.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.Metrics.formattedExceptionName;
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.TYPE_TAG;
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.STATUS_CODE_TAG;
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.ERROR;
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.EXCEPTION;
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.RESPONSE;
import static java.lang.System.arraycopy;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;

public class HttpErrorStatusMetricsTest {
    private MeterRegistry registry;
    private HttpErrorStatusListener errorListener;

    @BeforeEach
    public void setUp() {
        registry = new SimpleMeterRegistry();
        errorListener = new HttpErrorStatusMetrics(registry);
    }

    @Test
    public void metricsArePreRegistered() {
        assertThat(registry.get(ERROR).counter(), is(notNullValue()));
        assertThat(registry.get(ERROR).counter().count(), is(0.0));

        assertThat(registry.get(RESPONSE).tag(STATUS_CODE_TAG, "200").counter(), is(notNullValue()));
        assertThat(registry.get(RESPONSE).tag(STATUS_CODE_TAG, "200").counter().count(), is(0.0));

        assertThat(registry.get(EXCEPTION).tag(TYPE_TAG, "java_lang_Exception").counter(), is(notNullValue()));
        assertThat(registry.get(EXCEPTION).tag(TYPE_TAG, "java_lang_Exception").counter().count(), is(0.0));
    }

    @ParameterizedTest
    @MethodSource("non500ServerErrors")
    public void exceptionsReportedWithNon500CodesAreNotRecordedAsUnexpectedErrors(HttpResponseStatus status) {
        errorListener.proxyErrorOccurred(status, new CustomException());

        assertThat(count(ERROR), is(0));
    }

    @Test
    public void styxErrorsWithExceptionsPropagateBothStatusCodeAndExceptionClass() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(RESPONSE, STATUS_CODE_TAG, "500"), is(1));
        assertThat(statusCountsExcluding("500"), everyItem(is(0)));
        assertThat(count(EXCEPTION, TYPE_TAG, formattedExceptionName(CustomException.class)), is(1));
        assertThat(count(ERROR), is(1));
    }

    @Test
    public void pluginExceptionsAreNotRecordedAsStyxUnexpectedErrors() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new PluginException("bad"));

        assertThat(count(RESPONSE, STATUS_CODE_TAG, "500"), is(1));
        assertThat(statusCountsExcluding("500"), everyItem(is(0)));
        assertThat(count(ERROR), is(0));
    }

    @Test
    public void supportsExceptionRepetition() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(EXCEPTION, TYPE_TAG, formattedExceptionName(CustomException.class)), is(2));
        assertThat(count(ERROR), is(2));
    }

    @Test
    public void nonErrorStatusesIsNotRecordedForProxyEvenIfExceptionIsSupplied() {
        MeterRegistry registry = mock(MeterRegistry.class);
        HttpErrorStatusMetrics reporter = new HttpErrorStatusMetrics(registry);
        reset(registry);
        reporter.proxyErrorOccurred(OK, new RuntimeException("This shouldn't happen"));
        verifyZeroInteractions(registry);
    }

    @Test
    public void updatesCountersForProxyErrorsWithResponse() {
        LiveHttpRequest request = get("/foo").build();
        errorListener.proxyErrorOccurred(request, InetSocketAddress.createUnresolved("127.0.0.1", 0), INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(RESPONSE, STATUS_CODE_TAG, "500"), is(1));
        assertThat(statusCountsExcluding("500"), everyItem(is(0)));
        assertThat(count(EXCEPTION, TYPE_TAG, formattedExceptionName(CustomException.class)), is(1));
        assertThat(count(ERROR), is(1));
    }

    private int count(String counterName, String... tagKeyValue) {
        return (int) registry.counter(counterName, tagKeyValue).count();
    }

    private static Stream<Arguments> non500ServerErrors() {
        return serverErrors()
                .filter(args -> !INTERNAL_SERVER_ERROR.equals(args.get()[0]));
    }

    private static Stream<Arguments> allErrors() {
        return Stream.concat(clientErrors(), serverErrors());
    }

    private static Object[][] concatenateArrays(Object[][] array1, Object[][] array2) {
        Object[][] concatenated = new Object[array2.length + array1.length][];

        arraycopy(array1, 0, concatenated, 0, array1.length);
        arraycopy(array2, 0, concatenated, array1.length, array2.length);

        return concatenated;
    }

    private static Stream<Arguments> serverErrors() {
        return Stream.of(
                Arguments.of(INTERNAL_SERVER_ERROR),
                Arguments.of(NOT_IMPLEMENTED),
                Arguments.of(BAD_GATEWAY),
                Arguments.of(GATEWAY_TIMEOUT),
                Arguments.of(HTTP_VERSION_NOT_SUPPORTED),
                Arguments.of(ORIGIN_SERVER_TIMED_OUT),
                Arguments.of(ORIGIN_CONNECTION_REFUSED),
                Arguments.of(ORIGIN_CONNECTION_TIMED_OUT)
        );
    }

    private static Stream<Arguments> clientErrors() {
        return Stream.of(
                Arguments.of(BAD_REQUEST),
                Arguments.of(UNAUTHORIZED),
                Arguments.of(PAYMENT_REQUIRED),
                Arguments.of(FORBIDDEN),
                Arguments.of(NOT_FOUND),
                Arguments.of(METHOD_NOT_ALLOWED),
                Arguments.of(NOT_ACCEPTABLE),
                Arguments.of(PROXY_AUTHENTICATION_REQUIRED),
                Arguments.of(REQUEST_TIMEOUT),
                Arguments.of(CONFLICT),
                Arguments.of(GONE),
                Arguments.of(LENGTH_REQUIRED),
                Arguments.of(PRECONDITION_FAILED),
                Arguments.of(REQUEST_ENTITY_TOO_LARGE),
                Arguments.of(REQUEST_URI_TOO_LONG),
                Arguments.of(UNSUPPORTED_MEDIA_TYPE),
                Arguments.of(REQUESTED_RANGE_NOT_SATISFIABLE),
                Arguments.of(EXPECTATION_FAILED)
        );
    }

    private Collection<Integer> statusCountsExcluding(String excluded) {
        return registry.find(RESPONSE).counters().stream()
                .filter(c -> !c.getId().getTag(STATUS_CODE_TAG).equals(excluded))
                .map(Counter::count)
                .map(Double::intValue)
                .collect(toList());
    }

    private static class CustomException extends Exception {
    }
}
