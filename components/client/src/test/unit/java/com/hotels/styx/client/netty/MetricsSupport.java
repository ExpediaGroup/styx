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
package com.hotels.styx.client.netty;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Meter;
import com.google.common.base.Joiner;
import com.hotels.styx.api.MetricRegistry;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.singleton;

/**
 * Support class for metrics.
 */
public class MetricsSupport {
    public static String name(List<String> components, String name) {
        return Joiner.on(".").join(concat(components, singleton(name)));
    }

    /**
     * A matcher that checks whether a metric is not updated.
     */
    public static final class IsNotUpdated extends TypeSafeMatcher<MetricRegistry> {

        private final Set<String> excludedNames;

        private IsNotUpdated(Set<String> excludedNames) {
            this.excludedNames = excludedNames;
        }

        @Factory
        public static <T> Matcher<MetricRegistry> hasNotReceivedUpdatesExcept(String... updatedMetrics) {
            return new IsNotUpdated(newHashSet(updatedMetrics));
        }

        @Override
        protected boolean matchesSafely(MetricRegistry metricRegistry) {
            Set<String> updated = updatedMetrics(metricRegistry);
            return updated.size() == 0;
        }

        @Override
        public void describeTo(Description description) {
            StringBuilder sb = new StringBuilder();
            sb.append("not updated");

            if (excludedNames.size() > 0) {
                sb.append(" except {");
                sb.append(Joiner.on(", ").join(excludedNames));
                sb.append("}");
            }

            description.appendText(sb.toString());
        }

        @Override
        protected void describeMismatchSafely(MetricRegistry item, Description mismatchDescription) {
            String description = "following metrics have been updated: {"
                    + Joiner.on(", ").join(updatedMetrics(item))
                    + "}";

            mismatchDescription.appendText(description);
            super.describeMismatchSafely(item, mismatchDescription);
        }

        private Set<String> updatedMetrics(MetricRegistry registry) {
            Map<String, Meter> meterMap = registry.getMeters((name, metric) -> {
                if (!excludedNames.contains(name)) {
                    if (metric instanceof Counting) {
                        return ((Counting) metric).getCount() != 0;
                    }
                }
                return false;
            });
            return meterMap.keySet();
        }
    }
}
