/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.client.healthcheck;

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.MicrometerRegistry;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UrlRequestHealthCheckTest {
    private final Origin someOrigin = newOriginBuilder("localhost", 12345).id("foo").build();

    private CentralisedMetrics metrics;
    private OriginState originState;
    private String requestedUrl;

    @BeforeEach
    public void setUp() {
        metrics = new CentralisedMetrics(new MicrometerRegistry(new SimpleMeterRegistry()));
        originState = null;
        requestedUrl = null;
    }

    @Test
    public void sendsTheHealthCheckRequestToTheGivenUrl() {
        HttpClient client = request -> {
            requestedUrl = request.url().path();
            return respondWith(NOT_FOUND);
        };

        new UrlRequestHealthCheck("/version-foo.txt", metrics)
                .check(client, someOrigin, state -> {
                });

        assertThat(requestedUrl, is("/version-foo.txt"));
    }

    @Test
    public void declaresOriginHealthyOnOkResponseCode() {
        HttpClient client = request -> respondWith(OK);

        new UrlRequestHealthCheck("/version.txt", metrics)
                .check(client, someOrigin, state -> this.originState = state);

        assertThat(originState, is(HEALTHY));
        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthCheckFailures")).size(), is(0));
        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthChecks")).size(), is(1));
    }

    @Test
    public void declaresOriginUnhealthyOnNon200Ok() {
        HttpClient client = request -> respondWith(NOT_FOUND);

        new UrlRequestHealthCheck("/version.txt", metrics)
                .check(client, someOrigin, state -> this.originState = state);

        assertThat(originState, is(UNHEALTHY));
        assertThat(metrics.getRegistry().find("proxy.client.originHealthCheckFailures")
                .tags("originId", someOrigin.id().toString(), "appId", someOrigin.applicationId().toString()).counter().count(), is(1.0));
        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthCheckFailures")).size(), is(1));
        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthChecks")).size(), is(1));
    }

    @Test
    public void declaredOriginUnhealthyOnTransportException() {
        HttpClient client = request -> respondWith(new RuntimeException("health check failure, as expected"));

        new UrlRequestHealthCheck("/version.txt", metrics)
                .check(client, someOrigin, state -> this.originState = state);

        assertThat(originState, is(UNHEALTHY));
        assertThat(metrics.getRegistry().find("proxy.client.originHealthCheckFailures")
                .tags("originId", someOrigin.id().toString(), "appId", someOrigin.applicationId().toString()).counter().count(), is(1.0));

        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthCheckFailures")).size(), is(1));
        assertThat(meters(id -> id.getName().equals("proxy.client.originHealthChecks")).size(), is(1));
    }

    private List<Meter> meters(Predicate<Meter.Id> predicate) {
        return metrics.getRegistry().getMeters().stream()
                .filter(meter -> predicate.test(meter.getId()))
                .collect(toList());
    }

    private static CompletableFuture<HttpResponse> respondWith(Throwable error) {
        CompletableFuture<HttpResponse> f = new CompletableFuture<>();
        f.completeExceptionally(error);
        return f;
    }

    private static CompletableFuture<HttpResponse> respondWith(HttpResponseStatus status) {
        return CompletableFuture.completedFuture(response(status).build());
    }
}
