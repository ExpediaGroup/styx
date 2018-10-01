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
package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;

public class MappingOperatorTest {
    private Buffer buffer1;
    private Publisher<Buffer> upstream;

    @BeforeMethod
    public void setUp() {
        buffer1 = new Buffer("y", UTF_8);
        upstream = Flux.just(
                new Buffer("x", UTF_8),
                new Buffer("Y", UTF_8)
        );
    }

    @Test
    public void appliesMappingToContent() {
        Flux<Buffer> consumer = Flux.from(new MappingOperator(upstream, this::toUpperCaseBuffer));

        StepVerifier.create(consumer.map(buffer -> new String(buffer.content(), UTF_8)))
                .expectNext("X", "Y")
                .expectComplete()
                .verify();
    }


    @Test(expectedExceptions = IllegalStateException.class)
    public void allowsOnlyOneSubscription() {
        MappingOperator mapper = new MappingOperator(upstream, this::toUpperCaseBuffer);

        Subscriber subscription1 = mock(Subscriber.class);
        Subscriber subscription2 = mock(Subscriber.class);

        mapper.subscribe(subscription1);
        mapper.subscribe(subscription2);
    }


    @Test
    public void mapsAnEmptyStream() {
        StepVerifier.create(new MappingOperator(Flux.empty(), this::toUpperCaseBuffer))
                .thenRequest(2)
                .verifyComplete();
    }

    @Test
    public void mapsAnEmptyErroringStream() {
        MappingOperator operator = new MappingOperator(
                Flux.<Buffer>empty().concatWith(Flux.error(new RuntimeException(">:-O"))),
                this::toUpperCaseBuffer);

        StepVerifier.create(operator, 2)
                .verifyError();
    }


    @Test
    public void emitsErrors() {
        MappingOperator operator = new MappingOperator(
                Flux.just(buffer1).concatWith(Flux.error(new RuntimeException(";-("))),
                this::toUpperCaseBuffer);

        StepVerifier.create(operator)
                .expectNextCount(1)
                .verifyError();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void checkForNullSubscription() {
        new MappingOperator(
                subscriber -> subscriber.onSubscribe(null),
                this::toUpperCaseBuffer
        ).subscribe(mock(Subscriber.class));
    }

    public Buffer toUpperCaseBuffer(Buffer buffer) {
        String mapped = new String(buffer.content(), UTF_8).toUpperCase();
        return new Buffer(mapped, UTF_8);
    }
}