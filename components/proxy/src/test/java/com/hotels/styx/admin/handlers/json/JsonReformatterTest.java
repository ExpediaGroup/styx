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
package com.hotels.styx.admin.handlers.json;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonReformatterTest {
    @Test
    public void splitsUpDotsIfTheyAreCommonBetweenKeysInSameObject() {
        String before = "{\"foo.one\":1, \"foo.two\":2}";
        String after = JsonReformatter.reformat(before);

        String actual = removeWhiteSpace(after);

        assertThat(actual, is("{\"foo\":{\"one\":1,\"two\":2}}"));
    }

    @Test
    public void splitsUpDotsIfTheyAreCommonBetweenKeysInSameObjectWhenThereIsAnotherRootValue() {
        String before = "{\"foo.one\":1, \"foo.two\":2, \"bar\":3}";
        String after = JsonReformatter.reformat(before);

        String actual = removeWhiteSpace(after);

        assertThat(actual, is("{\"bar\":3,\"foo\":{\"one\":1,\"two\":2}}"));
    }

    @Test
    public void doesNotSplitUpDotsIfTheyAreNotCommonBetweenKeysInSameObject() {
        String before = "{\"foo.one\":1, \"bar.two\":2}";
        String after = JsonReformatter.reformat(before);

        String actual = removeWhiteSpace(after);

        assertThat(actual, is("{\"bar.two\":2,\"foo.one\":1}"));
    }

    @Test
    public void doesNotSplitUpDotsIfTheyAreNotCommonBetweenKeysInSameObjectWhenThereIsAnotherRootValue() {
        String before = "{\"foo.one\":1, \"bar.two\":2, \"baz\":3}";
        String after = JsonReformatter.reformat(before);

        String actual = removeWhiteSpace(after);

        assertThat(actual, is("{\"bar.two\":2,\"baz\":3,\"foo.one\":1}"));
    }

    @Test
    public void createsMetricsJsonCorrectly() throws JsonProcessingException {
        MetricRegistry registry = new MetricRegistry();

        registry.counter("styx.origins.status.200").inc();
        registry.counter("styx.origins.status.500").inc(2);
        registry.counter("styx.error").inc(3);
        registry.counter("foo.bar").inc(4);

        registry.register("styx.mygauge", (Gauge<Integer>) () -> 123);

        String before = new ObjectMapper()
                .registerModule(new MetricsModule(SECONDS, MILLISECONDS, false))
                .writeValueAsString(registry);

        String after = JsonReformatter.reformat(before);

        assertThat(after, is("{\n" +
                "  \"counters\":{\n" +
                "    \"foo.bar.count\":4,\n" +
                "    \"styx\":{\n" +
                "      \"error.count\":3,\n" +
                "      \"origins.status\":{\n" +
                "          \"200.count\":1,\n" +
                "          \"500.count\":2\n" +
                "        }\n" +
                "    }\n" +
                "  },\n" +
                "  \"gauges.styx.mygauge.value\":123,\n" +
                "  \"histograms\":{\n" +
                "\n" +
                "  },\n" +
                "  \"meters\":{\n" +
                "\n" +
                "  },\n" +
                "  \"timers\":{\n" +
                "\n" +
                "  },\n" +
                "  \"version\":\"3.1.3\"\n" +
                "}"));
    }

    private static String removeWhiteSpace(String text) {
        return text.replaceAll("\\s+", "");
    }
}