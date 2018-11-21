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
package com.hotels.styx.api;

import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;

public class EventualTest {

    @Test
    public void createFromPublisher() {
        String value = Mono.from(new Eventual<>(Flux.just("hello"))).block();
        assertEquals(value, "hello");
    }

    @Test
    public void createFromValue() {
        StepVerifier.create(Eventual.of("x"))
                .expectNext("x")
                .verifyComplete();
    }

    @Test
    public void createFromCompletionStage() {
        CompletableFuture<String> future = new CompletableFuture<>();

        Eventual<String> eventual = Eventual.from(future);

        StepVerifier.create(eventual)
                .thenRequest(1)
                .expectNextCount(0)
                .then(() -> future.complete("hello"))
                .expectNext("hello")
                .verifyComplete();
    }

    @Test
    public void createFromError() {
        Eventual<String> eventual = Eventual.error(new RuntimeException("things went wrong"));

        StepVerifier.create(eventual)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    public void mapsValues() {
        StepVerifier.create(new Eventual<>(Flux.just("hello")).map(String::toUpperCase))
                .expectNext("HELLO")
                .verifyComplete();
    }

    @Test
    public void flatMapsValues() {
        Eventual<String> eventual = Eventual.of("hello")
                .flatMap(it -> Eventual.of(it + " world"));

        StepVerifier.create(eventual)
                .expectNext("hello world")
                .verifyComplete();
    }

    @Test
    public void mapsErrors() {
        Eventual<String> eventual = Eventual.<String>error(new RuntimeException("ouch"))
                .onError(it -> Eventual.of("mapped error: " + it.getMessage()));

        StepVerifier.create(eventual)
                .expectNext("mapped error: ouch")
                .verifyComplete();
    }
}
