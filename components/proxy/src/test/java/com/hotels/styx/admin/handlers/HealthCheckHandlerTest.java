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
package com.hotels.styx.admin.handlers;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.codahale.metrics.health.HealthCheck.Result.healthy;
import static com.codahale.metrics.health.HealthCheck.Result.unhealthy;
import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.support.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.support.api.matchers.HttpStatusMatcher.hasStatus;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;

public class HealthCheckHandlerTest {

    private HealthCheckRegistry registry;
    private HealthCheckHandler handler;

    @BeforeMethod
    public void createHandler() {
        registry = new HealthCheckRegistry();
        handler = new HealthCheckHandler(registry, newSingleThreadExecutor());
    }

    @Test
    public void returnsA200IfAllHealthChecksAreHealthy() {
        registry.register("fun", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return healthy("whee");
            }
        });

        HttpResponse response = handle(get("/healthcheck").build());
        assertThat(response.headers(), isNotCacheable());
        assertThat(response, hasStatus(OK));
        assertThat(response, hasBody(matchesRegex("\\{\"fun\":" +
                "\\{\"healthy\":true,\"message\":\"whee\",\"timestamp\":\".*\"}}")));
    }

    @Test
    public void returns501IfNoHealthChecksAreRegistered() {
        HttpResponse response = handle(get("/healthcheck").build());
        assertThat(response.headers(), isNotCacheable());
        assertThat(response, hasStatus(NOT_IMPLEMENTED));
    }

    @Test
    public void returnsA500IfAnyHealthChecksAreUnhealthy() {
        registry.register("fun", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return healthy("whee");
            }
        });

        registry.register("notFun", new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return unhealthy("whee");
            }
        });

        HttpResponse response = handle(get("/healthcheck").build());
        assertThat(response.headers(), isNotCacheable());
        assertThat(response, hasStatus(INTERNAL_SERVER_ERROR));
        assertThat(response, hasBody(matchesRegex(
                "\\{\"fun\":" +
                        "\\{\"healthy\":true,\"message\":\"whee\",\"timestamp\":\".*\"},\"notFun\":" +
                        "\\{\"healthy\":false,\"message\":\"whee\",\"timestamp\":\".*\"}}")));
    }

    private HttpResponse handle(HttpRequest request) {
        return getFirst(handler.handle(request));
    }
}
