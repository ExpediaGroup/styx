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
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.common.MapStream;
import rx.Observable;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hotels.styx.api.messages.FullHttpResponse.response;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static rx.Observable.just;

/**
 * Handler for showing all registered metrics for styx server. Can cache page content.
 */
public class MetricsHandler extends JsonHandler<MetricRegistry> {
    private static final Pattern SPECIFIC_METRICS_PATH_PATTERN = Pattern.compile(".*/metrics/(.+)/?");

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
        return metricName(request.path())
                .map(metricName -> specificMetricsResponse(request, metricName))
                .orElseGet(() -> super.handle(request));
    }

    private Observable<HttpResponse> specificMetricsResponse(HttpRequest request, String metricName) {
        boolean pretty = request.queryParam("pretty").isPresent();

        String serialised = serialise(metrics(metricName), pretty);

        return just(response(OK)
                .body(serialised, UTF_8)
                .build()
                .toStreamingResponse());
    }

    private static Optional<String> metricName(String path) {
        Matcher matcher = SPECIFIC_METRICS_PATH_PATTERN.matcher(path);

        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    private String serialise(Object object, boolean pretty) {
        ObjectWriter writer = pretty ? metricSerialiser.writerWithDefaultPrettyPrinter() : metricSerialiser.writer();

        try {
            return writer.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Metric> metrics(String metricNameStart) {
        return MapStream.stream(metricRegistry.getMetricRegistry().getMetrics())
                .filter((name, metric) -> name.equals(metricNameStart) || name.startsWith(metricNameStart))
                .toMap();
    }
}
