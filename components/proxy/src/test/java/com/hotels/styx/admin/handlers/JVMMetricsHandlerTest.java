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

import com.codahale.metrics.Gauge;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.server.HttpInterceptorContext;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.google.common.collect.Iterables.all;
import static com.hotels.styx.admin.handlers.JVMMetricsHandlerTest.StringsContains.containsStrings;
import static com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.support.api.matchers.HttpResponseStatusMatcher.hasStatus;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import com.hotels.styx.api.HttpRequest;

public class JVMMetricsHandlerTest {
    JVMMetricsHandler handler;

    @BeforeMethod
    public void setUp() {
        CodaHaleMetricRegistry metricRegistry = new CodaHaleMetricRegistry();
        metricRegistry.register("irrelevant.gauge", (Gauge<Object>) () -> null);
        metricRegistry.counter("irrelevant.counter");
        metricRegistry.meter("irrelevant.meter");
        metricRegistry.timer("irrelevant.timer");
        metricRegistry.histogram("irrelevant.histogram");

        metricRegistry.register("jvm.foo.gauge", (Gauge<Object>) () -> null);
        metricRegistry.counter("jvm.bar.counter");
        metricRegistry.meter("jvm.baz.meter");
        metricRegistry.timer("jvm.hello.timer");
        metricRegistry.histogram("jvm.world.histogram");

        handler = new JVMMetricsHandler(metricRegistry, Optional.empty());
    }

    @Test
    public void respondsToRequestWithJsonResponse() {
        HttpResponse response = call(get("/jvm").build());
        assertThat(response, hasStatus(OK));
        assertThat(response.contentType().get(), is(APPLICATION_JSON.toString()));
    }

    @Test
    public void doesNotExposeIrrelevantMetrics() {
        HttpResponse response = call(get("/jvm").build());
        assertThat(response, hasBody(not(containsString("irrelevant"))));
    }

    @Test
    public void exposesAllMetricsStartingWithJvm() {
        HttpResponse response = call(get("/jvm").build());
        assertThat(response, hasBody(containsStrings(
                "jvm.foo.gauge",
                "jvm.bar.counter",
                "jvm.baz.meter",
                "jvm.hello.timer",
                "jvm.world.histogram"
        )));
    }

    private HttpResponse call(HttpRequest request) {
        return getFirst(handler.handle(request, HttpInterceptorContext.create()));
    }

    static class StringsContains extends TypeSafeMatcher<String> {

        private final Iterable<String> substrings;

        public StringsContains(String... substrings) {
            this.substrings = asList(substrings);
        }

        @Factory
        public static Matcher<String> containsStrings(String... substrings) {
            return new StringsContains(substrings);
        }

        @Override
        protected boolean matchesSafely(String item) {
            return all(this.substrings, item::contains);
        }

        @Override
        public void describeTo(Description description) {
            description.appendValueList("[", ",", "]", this.substrings);
        }
    }
}
