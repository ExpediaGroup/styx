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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class ByteStreamTest {
    private Buffer buf1;
    private Buffer buf2;
    private Buffer buf3;

    @BeforeMethod
    public void setUp() {
        buf1 = new Buffer("a", UTF_8);
        buf2 = new Buffer("b", UTF_8);
        buf3 = new Buffer("c", UTF_8);
    }

    @Test
    public void publishesContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        StepVerifier.create(stream.publisher())
                .expectNext(buf1)
                .expectNext(buf2)
                .expectNext(buf3)
                .verifyComplete();
    }

    @Test
    public void publisherBackpressure() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        StepVerifier.create(stream.publisher(), 0)
                .expectSubscription()
                .thenRequest(1)
                .expectNext(buf1)
                .thenRequest(2)
                .expectNext(buf2, buf3)
                .verifyComplete();
    }

    @Test
    public void mapsContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream mapped = stream.map(this::toUpperCase);

        StepVerifier.create(Flux.from(mapped.publisher()).map(this::decodeUtf8String))
                .expectSubscription()
                .expectNext("A", "B", "C")
                .verifyComplete();
    }

    @Test
    public void discardsContent() {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        ByteStream discarded = stream.discard();

        StepVerifier.create(discarded.publisher())
                .expectSubscription()
                .verifyComplete();
    }

    @Test
    public void aggregatesContent() throws ExecutionException, InterruptedException {
        ByteStream stream = new ByteStream(Flux.just(buf1, buf2, buf3));

        Buffer aggregated = stream.aggregate(100).get();
        assertThat(decodeUtf8String(aggregated), is("abc"));
    }


    private String decodeUtf8String(Buffer buffer) {
        return new String(buffer.content(), UTF_8);
    }

    private Buffer toUpperCase(Buffer buffer) {
        return new Buffer(decodeUtf8String(buffer).toUpperCase(), UTF_8);
    }

    @Test(enabled = false)
    public void flatMapscontent() {
    }

    @Test(enabled = false)
    public void peeksContent() {
    }

}