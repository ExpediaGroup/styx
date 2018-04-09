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
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.http.handlers.BaseHttpHandler;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.lang.Boolean.parseBoolean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Returns results of health checks.
 */
public class HealthCheckHandler extends BaseHttpHandler {
    private final HealthCheckRegistry registry;
    private final ExecutorService executorService;

    private final ObjectMapper mapper;

    /**
     * Constructs the handler with specified registry and executor service.
     *
     * @param registry        the registry for all health checks
     * @param executorService the executor used to do non blocking health checks
     */
    public HealthCheckHandler(HealthCheckRegistry registry, ExecutorService executorService) {
        this.registry = checkNotNull(registry);
        this.executorService = checkNotNull(executorService);
        this.mapper = mapper();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new MetricsModule(SECONDS, MILLISECONDS, true));
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        SortedMap<String, HealthCheck.Result> results = runHealthChecks();
        return response(responseStatus(results))
                .disableCaching()
                .contentType(JSON_UTF_8)
                .body(body(request, results))
                .build();
    }

    private String body(HttpRequest request, SortedMap<String, HealthCheck.Result> results) {
        try {
            return writer(request).writeValueAsString(results);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpResponseStatus responseStatus(Map<String, Result> results) {
        HttpResponseStatus status = NOT_IMPLEMENTED;
        if (!results.isEmpty()) {
            status = isAllHealthy(results) ? OK : INTERNAL_SERVER_ERROR;
        }
        return status;
    }

    private ObjectWriter writer(HttpRequest request) {
        return isPrettyPrint(request)
                ? this.mapper.writerWithDefaultPrettyPrinter()
                : this.mapper.writer();
    }

    private boolean isPrettyPrint(HttpRequest request) {
        return parseBoolean(request.queryParam("pretty").orElse("false"));
    }

    private SortedMap<String, HealthCheck.Result> runHealthChecks() {
        return this.executorService == null
                ? this.registry.runHealthChecks()
                : this.registry.runHealthChecks(this.executorService);
    }

    private static boolean isAllHealthy(Map<String, HealthCheck.Result> results) {
        return results.values().stream().allMatch(result -> result != null && result.isHealthy());
    }
}
