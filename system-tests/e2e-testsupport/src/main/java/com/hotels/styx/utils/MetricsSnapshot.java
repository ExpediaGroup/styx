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
package com.hotels.styx.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.FullHttpClient;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.client.SimpleHttpClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.api.FullHttpRequest.get;
import static com.hotels.styx.common.StyxFutures.await;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@Deprecated
public class MetricsSnapshot {
    private final Map<String, Object> tree;

    private MetricsSnapshot(Map<String, Object> tree) {
        this.tree = tree;
    }

    public static MetricsSnapshot downloadFrom(String host, int port) throws IOException {
        FullHttpClient client = new SimpleHttpClient.Builder().build();
        FullHttpResponse response = await(client.sendRequest(get(format("http://%s:%d/admin/metrics", host, port)).build()));
        return new MetricsSnapshot(decodeToMap(response.bodyAs(UTF_8)));
    }

    public static MetricsSnapshot fromString(String json) throws IOException {
        return new MetricsSnapshot(decodeToMap(json));
    }

    private static Map<String, Object> decodeToMap(String body) throws IOException {
        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };
        return mapper.readValue(body, typeRef);
    }

    public Optional<Integer> gaugeValue(String metricName) {
        return metric("gauges", metricName, "value");
    }

    public Optional<Integer> counter(String metricName) {
        return metric("counters", metricName, "count");
    }

    public Optional<Integer> metric(String... keys) {
        Map<String, Object> current = tree;

        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.get(keys[i]);

            if (current == null) {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(current.get(keys[keys.length - 1]))
                .map(value -> (int) value);
    }

    public Optional<ImmutableMap<String, Object>> getMetric(String metricType, String metricName) {
        return Optional.ofNullable((Map<String, Object>) tree.get(metricType))
                .map(metersMap -> (Map<String, Object>) metersMap.get(metricName))
                .map(ImmutableMap::copyOf);
    }

    @Override
    public String toString() {
        return Joiner.on('\n').join(entries());
    }

    private List<Map.Entry<String, ?>> entries() {
        return tree.entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), "version"))
                .peek(this::assertValueIsMap)
                .flatMap(entry -> castToMap(entry.getValue()).entrySet().stream())
                .sorted(comparing(Map.Entry::getKey))
                .collect(toList());
    }

    private void assertValueIsMap(Map.Entry<String, Object> entry) {
        checkArgument(entry.getValue() instanceof Map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> castToMap(Object value) {
        return (Map<String, ?>) value;
    }

}
