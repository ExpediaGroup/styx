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
package com.hotels.styx.metrics;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.client.SimpleHttpClient;
import com.hotels.styx.support.Meter;
import com.hotels.styx.utils.MetricsSnapshot;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.common.StyxFutures.await;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A metrics snapshot. Intended to replace {@link MetricsSnapshot}.
 * <p>
 * Currently does not provide Timers or Histograms, this functionality will be added later.
 */
public class StyxMetrics {
    private static final MetricType<Meter> METER = new MetricType<>("meters", StyxMetrics::toMeter);
    private static final MetricType<Long> COUNTER = new MetricType<>("counters", StyxMetrics::toCount);
    private static final MetricType<Object> GAUGE = new MetricType<>("gauges", StyxMetrics::toGauge);

    private final Map<String, Object> tree;
    private final Predicate<String> nameFilter;

    public StyxMetrics(Map<String, Object> tree) {
        this.tree = requireNonNull(tree);
        this.nameFilter = name -> true;
    }

    private StyxMetrics(StyxMetrics styxMetrics, Predicate<String> nameFilter) {
        this.tree = styxMetrics.tree;
        this.nameFilter = requireNonNull(nameFilter);
    }

    public static StyxMetrics downloadFrom(String host, int port) throws IOException {
        return fromJson(downloadJsonString(host, port));
    }

    static StyxMetrics fromJson(String json) throws IOException {
        return new StyxMetrics(decodeToMap(json));
    }

    public StyxMetrics filterByName(String regex) {
        Pattern pattern = Pattern.compile(regex);

        return new StyxMetrics(this, name -> pattern.matcher(name).matches());
    }

    public List<String> metricNames() {
        return tree.values().stream()
                .filter(metrics -> metrics instanceof Map)
                .map(metrics -> (Map<String, Object>) metrics)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .filter(nameFilter)
                .collect(toList());
    }

    public Optional<Meter> meter(String name) {
        return metric(METER, name);
    }

    public Optional<Long> counter(String name) {
        return metric(COUNTER, name);
    }

    public <T> Optional<T> gauge(String name, Class<T> type) {
        return metric(GAUGE, name).map(type::cast);
    }

    private <T> Optional<T> metric(MetricType<T> metricType, String name) {
        return Optional.ofNullable(metrics(metricType).get(name));
    }

    public Map<String, Meter> meters() {
        return metrics(METER);
    }

    public Map<String, Long> counters() {
        return metrics(COUNTER);
    }

    public Map<String, Object> gauges() {
        return metrics(GAUGE);
    }

    private <T> Map<String, T> metrics(MetricType<T> type) {
        return metricsByType(type.label).entrySet().stream()
                .filter(entry -> nameFilter.test(entry.getKey()))
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> type.constructor.apply((Map<String, Object>) entry.getValue())
                ));
    }

    private Map<String, Object> metricsByType(String label) {
        return Optional.ofNullable(tree.get(label))
                .map(meters -> (Map<String, Object>) meters)
                .orElse(emptyMap());
    }

    private static Meter toMeter(Map<String, Object> meter) {
        int count = (Integer) meter.get("count");
        double m1Rate = (Double) meter.get("m1_rate");
        double m5Rate = (Double) meter.get("m5_rate");
        double m15Rate = (Double) meter.get("m15_rate");
        double meanRate = (Double) meter.get("mean_rate");
        String units = (String) meter.get("units");

        return new Meter(count, m1Rate, m5Rate, m15Rate, meanRate, units);
    }

    private static Long toCount(Map<String, Object> map) {
        Object count = map.get("count");

        return ((Number) count).longValue();
    }

    private static Object toGauge(Map<String, Object> map) {
        return map.get("value");
    }

    private static String downloadJsonString(String host, int port) {
        FullHttpClient client = new SimpleHttpClient.Builder().build();
        FullHttpResponse response = await(client.sendRequest(get(format("http://%s:%d/admin/metrics", host, port)).build()));
        return response.bodyAs(UTF_8);
    }

    private static Map<String, Object> decodeToMap(String body) throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        return mapper.readValue(body, typeRef);
    }

    @Override
    public String toString() {
        return Joiner.on('\n').join(entries());
    }

    private List<Map.Entry<String, ?>> entries() {
        return tree.entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), "version"))
                .peek(StyxMetrics::assertValueIsMap)
                .flatMap(entry -> castToMap(entry.getValue()).entrySet().stream())
                .sorted(comparing(Map.Entry::getKey))
                .collect(toList());
    }

    private static void assertValueIsMap(Map.Entry<String, Object> entry) {
        checkArgument(entry.getValue() instanceof Map);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castToMap(Object value) {
        return (Map<String, ?>) value;
    }

    private static class MetricType<E> {
        private final String label;
        private final Function<Map<String, Object>, E> constructor;

        MetricType(String label, Function<Map<String, Object>, E> constructor) {
            this.label = label;
            this.constructor = constructor;
        }
    }
}
