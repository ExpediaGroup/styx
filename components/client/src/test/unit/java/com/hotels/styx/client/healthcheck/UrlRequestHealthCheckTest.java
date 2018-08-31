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
package com.hotels.styx.client.healthcheck;

import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.HEALTHY;
import static com.hotels.styx.client.healthcheck.OriginHealthCheckFunction.OriginState.UNHEALTHY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UrlRequestHealthCheckTest {
    private final Origin someOrigin = newOriginBuilder("localhost", 12345).id("foo").build();

    private MetricRegistry metricRegistry;
    private OriginState originState;
    private String requestedUrl;

    @BeforeMethod
    public void setUp() {
        metricRegistry = new CodaHaleMetricRegistry();
        originState = null;
        requestedUrl = null;
    }

    @Test
    public void sendsTheHealthCheckRequestToTheGivenUrl() {
        FullHttpClient client = request -> {
            requestedUrl = request.url().path();
            return respondWith(NOT_FOUND);
        };

        new UrlRequestHealthCheck("/version-foo.txt", client, metricRegistry)
                .check(someOrigin, state -> {
                });

        assertThat(requestedUrl, is("/version-foo.txt"));
    }

    @Test
    public void declaresOriginHealthyOnOkResponseCode() throws IOException {
        FullHttpClient client = request -> respondWith(OK);

        new UrlRequestHealthCheck("/version.txt", client, metricRegistry)
                .check(someOrigin, state -> this.originState = state);

        assertThat(originState, is(HEALTHY));
        assertThat(metricRegistry.getMeters().size(), is(0));
    }

    @Test
    public void declaresOriginUnhealthyOnNon200Ok() throws IOException {
        FullHttpClient client = request -> respondWith(NOT_FOUND);

        new UrlRequestHealthCheck("/version.txt", client, metricRegistry)
                .check(someOrigin, state -> this.originState = state);

        assertThat(originState, is(UNHEALTHY));
        assertThat(metricRegistry.meter("origins.healthcheck.failure.generic-app").getCount(), is(1L));
        assertThat(metricRegistry.getMeters().size(), is(1));
    }

    @Test
    public void declaredOriginUnhealthyOnTransportException() throws IOException {
        FullHttpClient client = request -> respondWith(new RuntimeException("health check failure, as expected"));

        new UrlRequestHealthCheck("/version.txt", client, metricRegistry)
                .check(someOrigin, state -> this.originState = state);

        assertThat(originState, is(UNHEALTHY));
        assertThat(metricRegistry.meter("origins.healthcheck.failure.generic-app").getCount(), is(1L));
        assertThat(metricRegistry.getMeters().size(), is(1));
    }

    private static CompletableFuture<FullHttpResponse> respondWith(Throwable error) {
        CompletableFuture<FullHttpResponse> f = new CompletableFuture<>();
        f.completeExceptionally(error);
        return f;
    }

    private static CompletableFuture<FullHttpResponse> respondWith(HttpResponseStatus status) {
        return CompletableFuture.completedFuture(response(status).build());
    }
}
