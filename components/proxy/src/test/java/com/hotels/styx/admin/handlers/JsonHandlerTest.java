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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.Clock;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.api.HttpMessageBodies.bodyAsString;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import com.hotels.styx.api.HttpRequest;

public class JsonHandlerTest {
    long time = currentTimeMillis();

    @Test
    public void returnsConstantValue() {
        JsonHandler<Convertible> handler = new JsonHandler<>(new Convertible("bar", 123), Optional.empty());

        assertThat(response(handler), is("{\"string\":\"bar\",\"integer\":123}"));
    }

    @Test
    public void returnsValueFromSupplier() {
        Supplier<Convertible> supplier = sequentialSupplier(new Convertible("foo", 456), new Convertible("bar", 123));

        JsonHandler<Convertible> handler = new JsonHandler<>(supplier, Optional.empty());

        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":456}"));
        assertThat(response(handler), is("{\"string\":\"bar\",\"integer\":123}"));
    }

    @Test
    public void cachesChangingValue() {
        Convertible value = new Convertible("foo", 123);

        JsonHandler<Object> handler = new JsonHandler<>(() -> value, Optional.of(Duration.ofSeconds(1)), new TestClock());

        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":123}"));

        value.setInteger(456);
        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":123}"));

        time += 1000L;

        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":456}"));
    }

    @Test
    public void cachesSupplierValue() {
        Supplier<Convertible> supplier = sequentialSupplier(new Convertible("foo", 123), new Convertible("bar", 456));

        JsonHandler<Convertible> handler = new JsonHandler<>(supplier, Optional.of(Duration.ofSeconds(1)), new TestClock());

        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":123}"));

        assertThat(response(handler), is("{\"string\":\"foo\",\"integer\":123}"));

        time += 1000L;

        assertThat(response(handler), is("{\"string\":\"bar\",\"integer\":456}"));
    }

    @Test
    public void prettyPrintsOutputWhenPrettyIsSetToTrue() {
        HttpRequest request = get("/?pretty=true").build();

        Supplier<Convertible> supplier = sequentialSupplier(new Convertible("foo", 456));
        JsonHandler<Convertible> handler = new JsonHandler<>(supplier, Optional.empty());

        assertThat(responseFor(handler, request), is("{\n" +
                "  \"string\" : \"foo\",\n" +
                "  \"integer\" : 456\n" +
                "}"));
    }

    private <T> Supplier<T> sequentialSupplier(T... elements) {
        return asList(elements).iterator()::next;
    }

    private String response(JsonHandler<?> handler) {
        return bodyAsString(await(handler.handle(get("/").build(), HttpInterceptorContext.create()).asCompletableFuture()));
    }

    private String responseFor(JsonHandler<?> handler, HttpRequest request) {
        return bodyAsString(await(handler.handle(request, HttpInterceptorContext.create()).asCompletableFuture()));
    }

    private static class Convertible {
        private String string;
        private int integer;

        public Convertible(String string, int integer) {
            this.string = string;
            this.integer = integer;
        }

        @JsonProperty("string")
        public String string() {
            return string;
        }

        @JsonProperty("integer")
        public int integer() {
            return integer;
        }

        public void setString(String string) {
            this.string = string;
        }

        public void setInteger(int integer) {
            this.integer = integer;
        }
    }

    private final class TestClock implements Clock {
        @Override
        public long tickMillis() {
            return time;
        }
    }
}