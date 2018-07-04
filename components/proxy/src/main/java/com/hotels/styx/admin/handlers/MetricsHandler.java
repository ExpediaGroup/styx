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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import rx.Observable;

import java.time.Duration;
import java.util.Optional;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static com.hotels.styx.api.messages.FullHttpResponse.response;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static rx.Observable.just;

/**
 * Handler for showing all registered metrics for styx server. Can cache page content.
 */
public class MetricsHandler extends JsonHandler<MetricRegistry> {
    private static final boolean DO_NOT_SHOW_SAMPLES = false;
    private final CodaHaleMetricRegistry metricRegistry;

    private final ObjectMapper metricSerialiser = new ObjectMapper()
            .registerModule(new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES));

    /**
     * Constructs a new handler.
     *
     * @param metricRegistry  metrics registry
     * @param cacheExpiration duration for which generated page content should be cached
     */
    public MetricsHandler(CodaHaleMetricRegistry metricRegistry, Optional<Duration> cacheExpiration) {
        super(requireNonNull(metricRegistry.getMetricRegistry()), cacheExpiration, new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES));

        this.metricRegistry = metricRegistry;
    }

    @Override
    public Observable<HttpResponse> handle(HttpRequest request) {
        String path = removeFinalSlash(request.path());

        if (path.equals("/admin/metrics")) {
            return super.handle(request);
        }

        String metricName = metricNameFromPath(path);
        boolean pretty = request.queryParam("pretty").isPresent();

        HttpResponse response = metric(metricName)
                .map(metric -> serialiseMetric(metric, pretty))
                .map(body -> response(OK).body(body, UTF_8))
                .map(FullHttpResponse.Builder::build)
                .map(FullHttpResponse::toStreamingResponse)
                .orElseGet(() -> response(NOT_FOUND).build());

        return just(response);
    }

    private String serialiseMetric(Metric metric, boolean pretty) {
        ObjectWriter writer = pretty ? metricSerialiser.writerWithDefaultPrettyPrinter() : metricSerialiser.writer();

        try {
            return writer.writeValueAsString(metric);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Metric> metric(String metricName) {
        return Optional.ofNullable(metricRegistry
                .getMetricRegistry()
                .getMetrics()
                .get(metricName));
    }

    private static String metricNameFromPath(String path) {
        return path.substring("/admin/metrics/".length());
    }

    private static String removeFinalSlash(String path) {
        return path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }
}
