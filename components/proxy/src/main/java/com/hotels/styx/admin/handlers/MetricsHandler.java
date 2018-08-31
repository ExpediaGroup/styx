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
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.MapStream.stream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Handler for showing all registered metrics for styx server. Can cache page content.
 */
public class MetricsHandler extends JsonHandler<MetricRegistry> {
    private static final Pattern SPECIFIC_METRICS_PATH_PATTERN = Pattern.compile(".*/metrics/(.+)/?");
    private static final boolean DO_NOT_SHOW_SAMPLES = false;
    private static final String FILTER_PARAM = "filter";
    private static final String PRETTY_PRINT_PARAM = "pretty";

    private final ObjectMapper metricSerialiser = new ObjectMapper()
            .registerModule(new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES));

    private final CodaHaleMetricRegistry metricRegistry;

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
    public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        MetricRequest metricRequest = new MetricRequest(request);

        return metricRequest.fullMetrics()
                ? super.handle(request, context)
                : StyxObservable.of(restrictedMetricsResponse(metricRequest).build().toStreamingResponse());
    }

    private FullHttpResponse.Builder restrictedMetricsResponse(MetricRequest request) {
        Map<String, Metric> fullMetrics = metricRegistry.getMetricRegistry().getMetrics();

        Map<String, Metric> restricted = filter(fullMetrics, (name, metric) -> request.matchesRoot(name));

        return restricted.isEmpty()
                ? response(NOT_FOUND)
                : search(request, restricted);
    }

    private FullHttpResponse.Builder search(MetricRequest request, Map<String, Metric> metrics) {
        Map<String, Metric> searched = filter(metrics, (name, metric) -> request.containsSearchTerm(name));

        String body = serialise(searched, request.prettyPrint);

        return response(OK)
                .body(body, UTF_8)
                .disableCaching();
    }

    private static <K, V> Map<K, V> filter(Map<K, V> map, BiPredicate<K, V> predicate) {
        return stream(map).filter(predicate).toMap();
    }

    private String serialise(Object object, boolean pretty) {
        ObjectWriter writer = pretty ? metricSerialiser.writerWithDefaultPrettyPrinter() : metricSerialiser.writer();

        try {
            return writer.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class MetricRequest {
        private final String root;
        private final String searchTerm;
        private final boolean prettyPrint;
        private final String prefix;

        MetricRequest(HttpRequest request) {
            this.root = metricName(request.path()).orElse(null);
            this.searchTerm = request.queryParam(FILTER_PARAM).orElse(null);
            this.prettyPrint = request.queryParam(PRETTY_PRINT_PARAM).isPresent();
            this.prefix = root + ".";
        }

        boolean fullMetrics() {
            return root == null && searchTerm == null;
        }

        private static Optional<String> metricName(String path) {
            return Optional.of(SPECIFIC_METRICS_PATH_PATTERN.matcher(path))
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1));
        }

        private boolean matchesRoot(String name) {
            return root == null || name.equals(root) || name.startsWith(prefix);
        }

        private boolean containsSearchTerm(String name) {
            return searchTerm == null || name.contains(searchTerm);
        }
    }
}
