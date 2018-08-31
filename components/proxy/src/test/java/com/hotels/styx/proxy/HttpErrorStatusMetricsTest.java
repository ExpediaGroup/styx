/*
  Copyright (C) 2013-2018 Expedia Inc.

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

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricFilter;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.PluginException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpRequest.get;
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
import static com.hotels.styx.proxy.HttpErrorStatusMetrics.formattedExceptionName;
import static com.hotels.styx.api.CustomHttpResponseStatus.ORIGIN_CONNECTION_REFUSED;
import static com.hotels.styx.api.CustomHttpResponseStatus.ORIGIN_CONNECTION_TIMED_OUT;
import static com.hotels.styx.api.CustomHttpResponseStatus.ORIGIN_SERVER_TIMED_OUT;
import static java.lang.System.arraycopy;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;

public class HttpErrorStatusMetricsTest {
    private MetricRegistry registry;
    private HttpErrorStatusListener errorListener;

    @BeforeMethod
    public void setUp() {
        registry = new CodaHaleMetricRegistry();
        errorListener = new HttpErrorStatusMetrics(registry);
    }

    @Test(dataProvider = "non500ServerErrors")
    public void exceptionsReportedWithNon500CodesAreNotRecordedAsUnexpectedErrors(HttpResponseStatus status) {
        errorListener.proxyErrorOccurred(status, new CustomException());

        assertThat(meterCount("styx.errors"), is(0));
    }

    @Test
    public void styxErrorsWithExceptionsPropagateBothStatusCodeAndExceptionClass() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count("styx.response.status.500"), is(1));
        assertThat(statusCountsExcluding("styx.response.status.500"), everyItem(is(0)));
        assertThat(count("styx.exception." + formattedExceptionName(CustomException.class)), is(1));
        assertThat(meterCount("styx.errors"), is(1));
    }

    @Test
    public void pluginExceptionsAreNotRecordedAsStyxUnexpectedErrors() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new PluginException("bad"));

        assertThat(count("styx.response.status.500"), is(1));
        assertThat(statusCountsExcluding("styx.response.status.500"), everyItem(is(0)));
        assertThat(meterCount("styx.errors"), is(0));
    }

    @Test
    public void supportsExceptionRepetition() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count("styx.exception." + formattedExceptionName(CustomException.class)), is(2));
        assertThat(meterCount("styx.errors"), is(2));
    }

    @Test
    public void nonErrorStatusesIsNotRecordedForProxyEvenIfExceptionIsSupplied() {
        MetricRegistry registry = mock(MetricRegistry.class);
        HttpErrorStatusMetrics reporter = new HttpErrorStatusMetrics(registry);
        reset(registry);
        reporter.proxyErrorOccurred(OK, new RuntimeException("This shouldn't happen"));
        verifyZeroInteractions(registry);
    }

    @Test
    public void updatesCountersForProxyErrorsWithResponse() {
        HttpRequest request = get("/foo").build();
        errorListener.proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count("styx.response.status.500"), is(1));
        assertThat(statusCountsExcluding("styx.response.status.500"), everyItem(is(0)));
        assertThat(count("styx.exception." + formattedExceptionName(CustomException.class)), is(1));
        assertThat(meterCount("styx.errors"), is(1));
    }

    private int meterCount(String meterName) {
        return (int) registry.meter(meterName).getCount();
    }

    private int count(String counterName) {
        return (int) registry.counter(counterName).getCount();
    }

    @DataProvider(name = "non500ServerErrors")
    private Object[][] non500ServerErrors() {
        return Stream.of(serverErrors())
                .filter(array -> !INTERNAL_SERVER_ERROR.equals(array[0]))
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "allErrors")
    private Object[][] allErrors() {
        return concatenateArrays(clientErrors(), serverErrors());
    }

    private static Object[][] concatenateArrays(Object[][] array1, Object[][] array2) {
        Object[][] concatenated = new Object[array2.length + array1.length][];

        arraycopy(array1, 0, concatenated, 0, array1.length);
        arraycopy(array2, 0, concatenated, array1.length, array2.length);

        return concatenated;
    }

    @DataProvider(name = "serverErrors")
    private Object[][] serverErrors() {
        return new Object[][]{
                {INTERNAL_SERVER_ERROR},
                {NOT_IMPLEMENTED},
                {BAD_GATEWAY},
                {GATEWAY_TIMEOUT},
                {HTTP_VERSION_NOT_SUPPORTED},
                {ORIGIN_SERVER_TIMED_OUT},
                {ORIGIN_CONNECTION_REFUSED},
                {ORIGIN_CONNECTION_TIMED_OUT}
        };
    }

    @DataProvider(name = "clientErrors")
    private Object[][] clientErrors() {
        return new Object[][]{
                {BAD_REQUEST},
                {UNAUTHORIZED},
                {PAYMENT_REQUIRED},
                {FORBIDDEN},
                {NOT_FOUND},
                {METHOD_NOT_ALLOWED},
                {NOT_ACCEPTABLE},
                {PROXY_AUTHENTICATION_REQUIRED},
                {REQUEST_TIMEOUT},
                {CONFLICT},
                {GONE},
                {LENGTH_REQUIRED},
                {PRECONDITION_FAILED},
                {REQUEST_ENTITY_TOO_LARGE},
                {REQUEST_URI_TOO_LONG},
                {UNSUPPORTED_MEDIA_TYPE},
                {REQUESTED_RANGE_NOT_SATISFIABLE},
                {EXPECTATION_FAILED},
        };
    }

    private Collection<Integer> statusCountsExcluding(String excluded) {
        MetricFilter filter = (name, metric) ->
                (name.startsWith("styx.response.status.") || name.startsWith("origins.response.status.")) && !name.equals(excluded);

        return registry.getCounters(filter).values().stream()
                .map(Counter::getCount)
                .map(Long::intValue)
                .collect(toList());
    }

    private static class CustomException extends Exception {
    }
}