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
package com.hotels.styx.admin.dashboard;

import com.codahale.metrics.Counting;
import com.codahale.metrics.MetricFilter;
import com.hotels.styx.api.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.COUNTER;
import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.METER;
import static java.lang.Character.isDigit;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.fill;
import static java.util.Objects.requireNonNull;

/**
 * Response code supplier.
 */
final class ResponseCodeSupplier implements Supplier<Map<String, Integer>> {
    private final Map<String, Integer> responseCodes = new HashMap<>();
    private final int[] aggregates = new int[5];
    private final String[] aggregateKeys = {"1xx", "2xx", "3xx", "4xx", "5xx"};

    private final MetricRegistry metrics;
    private final StatusMetricType statusMetricType;
    private final MetricFilter prefixFilter;
    private final boolean includeNonErrorCodes;

    ResponseCodeSupplier(MetricRegistry metrics, StatusMetricType statusMetricType, String prefix, boolean includeNonErrorCodes) {
        this.metrics = requireNonNull(metrics);
        this.statusMetricType = requireNonNull(statusMetricType);
        this.includeNonErrorCodes = includeNonErrorCodes;
        requireNonNull(prefix);
        this.prefixFilter = (name, metric) -> name.startsWith(prefix);
    }

    public enum StatusMetricType {
        COUNTER, METER
    }

    @Override
    public Map<String, Integer> get() {
        resetAggregates();

        if (statusMetricType == COUNTER) {
            updateFromMetrics(metrics.getCounters(prefixFilter));
        } else if (statusMetricType == METER) {
            updateFromMetrics(metrics.getMeters(prefixFilter));
        }

        addAggregatesToMap(responseCodes);

        return responseCodes;
    }

    private void resetAggregates() {
        fill(aggregates, 0);
    }

    private void updateFromMetrics(Map<String, ? extends Counting> metrics) {
        metrics.forEach((key, metric) -> {
            long count = metric.getCount();

            onMetricCount(key, (int) count);
        });
    }

    private void onMetricCount(String key, Integer count) {
        int lastDot = key.lastIndexOf('.');

        if (lastDot > -1 && lastDot < key.length() - 1) {
            String code = key.substring(lastDot + 1);

            responseCodes.put(code, count);
            incrementAggregates(code, count);
        }
    }

    private void incrementAggregates(String code, Integer count) {
        if (count != null && isPositiveInteger(code)) {
            int codeInt = parseInt(code);
            int index = (codeInt / 100) - 1;

            if (index >= 0 && index < aggregates.length) {
                aggregates[index] += count;
            }
        }
    }

    private boolean isPositiveInteger(String code) {
        for (int i = 0; i < code.length(); i++) {
            if (!isDigit(code.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private void addAggregatesToMap(Map<String, Integer> map) {
        for (int i = 0; i < aggregates.length; i++) {
            int firstDigit = i + 1;

            if (includeNonErrorCodes || firstDigit == 4 || firstDigit == 5) {
                map.put(aggregateKeys[i], aggregates[i]);
            }
        }
    }
}
