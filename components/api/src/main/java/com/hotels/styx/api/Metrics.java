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
package com.hotels.styx.api;

import java.util.StringJoiner;

public final class Metrics {

    private Metrics() {
    }

    public static String name(String... parts) {
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            if (part != null && part.length() > 0) {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }

    /* MetricRegistry is currently abstract */

//    public static MetricRegistry scope(MeterRegistry delegate, String prefix) {
//        MetricRegistry scoped = new MetricRegistry(delegate);
//        scoped.config().scopePrefix(prefix);
//        return scoped;
//    }

//    public static MetricRegistry scope(MeterRegistry delegate, Iterable<Tag> tags) {
//        MetricRegistry scoped = new MetricRegistry(delegate);
//        scoped.config().scopeTags(tags);
//        return scoped;
//    }

//    public static MetricRegistry scope(MeterRegistry delegate, String prefix, Iterable<Tag> tags) {
//        MetricRegistry scoped = new MetricRegistry(delegate);
//        scoped.config().scopePrefix(prefix);
//        scoped.config().scopeTags(tags);
//        return scoped;
//    }

}