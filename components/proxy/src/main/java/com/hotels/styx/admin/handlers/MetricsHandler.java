/*
  Copyright (C) 2013-2020 Expedia Inc.

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
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.infrastructure.configuration.json.mixins.CodaHaleMetricRegistryMixin;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.common.MapStream.stream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Handler for showing all registered metrics for styx server. Can cache page content.
 */
public class MetricsHandler implements WebServiceHandler {
    private static final Pattern SPECIFIC_METRICS_PATH_PATTERN = Pattern.compile(".*/metrics/(.+)/?");
    private static final boolean DO_NOT_SHOW_SAMPLES = false;
    private static final String FILTER_PARAM = "filter";
    private static final String PRETTY_PRINT_PARAM = "pretty";

    private final ObjectMapper metricSerialiser = new ObjectMapper()
            .registerModule(new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES))
            .addMixIn(CodaHaleMetricRegistry.class, CodaHaleMetricRegistryMixin.class);

    private final MetricRegistry metricRegistry;
    private final UrlPatternRouter urlMatcher;

    /**
     * Constructs a new handler.
     *
     * @param metricRegistry  metrics registry
     * @param cacheExpiration duration for which generated page content should be cached
     */
    public MetricsHandler(MetricRegistry metricRegistry, Optional<Duration> cacheExpiration) {
        this.urlMatcher = new UrlPatternRouter.Builder()
                .get(".*/metrics", new RootMetricsHandler(
                        metricRegistry,
                        cacheExpiration,
                        new MetricsModule(SECONDS, MILLISECONDS, DO_NOT_SHOW_SAMPLES),
                        new FullMetricsModule()))
                .get(".*/metrics/.*", (request, context) -> Eventual.of(filteredMetricResponse(request)))
                .build();

        this.metricRegistry = metricRegistry;
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        return this.urlMatcher.handle(request, context);
    }

    private static boolean matchesRoot(String metricName, String root) {
        return root == null || metricName.equals(root) || metricName.startsWith(root + ".");
    }

    private static boolean containsSearchTerm(String name, String searchTerm) {
        return searchTerm == null || name.contains(searchTerm);
    }

    private HttpResponse filteredMetricResponse(HttpRequest request) {
        String root = Optional.of(SPECIFIC_METRICS_PATH_PATTERN.matcher(request.path()))
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .orElse(null);

        boolean prettyPrint = request.queryParam(PRETTY_PRINT_PARAM).isPresent();
        String searchTerm = request.queryParam(FILTER_PARAM).orElse(null);

        Map<String, Metric> result = stream(metricRegistry.getMetrics())
                .filter((name, metric) -> matchesRoot(name, root))
                .toMap();

        if (result.isEmpty()) {
            return response(NOT_FOUND).build();
        } else {
            return response(OK)
                    .body(serialise(
                            stream(result)
                                    .filter((name, metric) -> containsSearchTerm(name, searchTerm))
                                    .toMap(), prettyPrint), UTF_8)
                    .disableCaching()
                    .build();
        }
    }

    private String serialise(Object object, boolean pretty) {
        ObjectWriter writer = pretty ? metricSerialiser.writerWithDefaultPrettyPrinter() : metricSerialiser.writer();

        try {
            return writer.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class RootMetricsHandler extends JsonHandler<MetricRegistry> {
        public RootMetricsHandler(MetricRegistry data, Optional<Duration> cacheExpiration, Module... modules) {
            super(data, cacheExpiration, modules);
        }
    }

    private static class FullMetricsModule extends SimpleModule {
        FullMetricsModule() {
            setMixInAnnotation(CodaHaleMetricRegistry.class, CodaHaleMetricRegistryMixin.class);
        }
    }


}
