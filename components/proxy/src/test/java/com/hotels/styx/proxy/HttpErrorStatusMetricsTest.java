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
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import com.hotels.styx.api.exceptions.OriginUnreachableException;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.plugins.spi.PluginException;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.connectionpool.MaxPendingConnectionTimeoutException;
import com.hotels.styx.client.connectionpool.MaxPendingConnectionsExceededException;
import com.hotels.styx.metrics.CentralisedMetrics;
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
import java.util.Optional;
import java.util.stream.Stream;

import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_CONNECTION_REFUSED;
import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_CONNECTION_TIMED_OUT;
import static com.hotels.styx.CustomHttpResponseStatus.ORIGIN_SERVER_TIMED_OUT;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class HttpErrorStatusMetricsTest {
    public static final String ERROR = "proxy.unexpectedError";

    private MeterRegistry registry;
    private HttpErrorStatusListener errorListener;
    private CentralisedMetrics metrics;

    @BeforeEach
    public void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new CentralisedMetrics(registry);
        errorListener = new HttpErrorStatusMetrics(metrics);
    }

    @Test
    public void metricsArePreRegistered() {
        assertThat(registry.get(ERROR).counter(), is(notNullValue()));
        assertThat(registry.get(ERROR).counter().count(), is(0.0));
    }

    @ParameterizedTest
    @MethodSource("non500ServerErrors")
    public void exceptionsReportedWithNon500CodesAreNotRecordedAsUnexpectedErrors(HttpResponseStatus status) {
        errorListener.proxyErrorOccurred(status, new CustomException());

        assertThat(count(ERROR), is(0));
    }

    @Test
    public void recordsError() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(ERROR), is(1));
    }

    @Test
    public void pluginExceptionsAreNotRecordedAsStyxUnexpectedErrors() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new PluginException("bad"));

        assertThat(count(ERROR), is(0));
    }

    @Test
    public void supportsExceptionRepetition() {
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());
        errorListener.proxyErrorOccurred(INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(ERROR), is(2));
    }

    @Test
    public void nonErrorStatusesIsNotRecordedForProxyEvenIfExceptionIsSupplied() {
        HttpErrorStatusMetrics reporter = new HttpErrorStatusMetrics(metrics);
        reporter.proxyErrorOccurred(OK, new RuntimeException("This shouldn't happen"));

        assertThat(count(ERROR), is(0));
    }

    @Test
    public void updatesCountersForProxyErrorsWithResponse() {
        LiveHttpRequest request = get("/foo").build();
        errorListener.proxyErrorOccurred(request, InetSocketAddress.createUnresolved("127.0.0.1", 0), INTERNAL_SERVER_ERROR, new CustomException());

        assertThat(count(ERROR), is(1));
    }

    @ParameterizedTest
    @MethodSource("backendFaultArgs")
    public void countsBackendFaults(Exception t, String metricTag) {
        errorListener.proxyErrorOccurred(BAD_GATEWAY, t);

        assertThat(
                counterWithMatchingTag("faultType", metricTag)
                        .map(Counter::count),

                isValue(1.0));
    }

    private Optional<Counter> counterWithMatchingTag(String tagKey, String tagValue) {
        return registry.getMeters().stream().filter(meter -> meter instanceof Counter)
                .filter(
                        meter -> meter.getId().getTags().stream().anyMatch(
                                tag -> tag.getKey().equals(tagKey) && tag.getValue().equals(tagValue)
                        )
                ).map(meter -> (Counter) meter)
                .findFirst();
    }

    private int count(String counterName, String... tagKeyValue) {
        return (int) registry.counter(counterName, tagKeyValue).count();
    }

    private static Stream<Arguments> non500ServerErrors() {
        return serverErrors()
                .filter(args -> !INTERNAL_SERVER_ERROR.equals(args.get()[0]));
    }

    private static Stream<Arguments> backendFaultArgs() {
        Id appId = Id.id("fakeApp");
        Id originId = Id.id("fakeApp1");
        Origin origin = Origin.newOriginBuilder("fakeHost", 9999)
                .applicationId(appId)
                .id(originId.toString())
                .build();

        Exception cause = new Exception();

        return Stream.of(
                Arguments.of(new NoAvailableHostsException(appId), "noHostsLiveForApplication"),
                Arguments.of(new OriginUnreachableException(origin, cause), "cannotConnect"),
                Arguments.of(new BadHttpResponseException(origin, cause), "badHttpResponse"),
                Arguments.of(new MaxPendingConnectionTimeoutException(origin, 1), "connectionsHeldTooLong"),
                Arguments.of(new MaxPendingConnectionsExceededException(origin, 1, 1), "tooManyConnections"),
                Arguments.of(new ResponseTimeoutException(origin, "test", 1, 1, 1, 1), "responseTooSlow")
        );
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

    private static class CustomException extends Exception {
    }
}
