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
package com.hotels.styx;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import com.hotels.styx.utils.MetricsSnapshot;

import java.util.Objects;
import java.util.Optional;

public class GaugeMatcher extends TypeSafeMatcher<MetricsSnapshot> {
    private final String name;
    private final Object expected;

    private GaugeMatcher(String name, Object expected) {
        super(MetricsSnapshot.class);

        this.name = name;
        this.expected = expected;
    }

    public static GaugeMatcher hasGauge(String name, Object value) {
        return new GaugeMatcher(name, value);
    }

    @Override
    protected boolean matchesSafely(MetricsSnapshot metricsSnapshot) {
        return metricsSnapshot.gaugeValue(name)
                .map(value -> Objects.equals(expected, value))
                .orElse(false);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Gauge ").appendText(name).appendText("=").appendValue(expected);
    }

    @Override
    protected void describeMismatchSafely(MetricsSnapshot item, Description mismatchDescription) {
        mismatchDescription.appendText("Gauge ").appendText(name);

        Optional<Integer> value = item.gaugeValue(name);

        if (value.isPresent()) {
            mismatchDescription.appendText("=").appendValue(value.get());
        } else {
            mismatchDescription.appendText(" does not exist");
        }
    }
}
