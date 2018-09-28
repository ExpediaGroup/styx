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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static rx.RxReactiveStreams.toObservable;
import static rx.RxReactiveStreams.toPublisher;


public class ByteStreamTest {
    private Buffer buf1;
    private Buffer buf2;
    private Buffer buf3;
    private TestSubscriber<Buffer> testSubscriber;
    private TestSubscriber<String> stringSubscriber;

    @BeforeMethod
    public void setUp() {
        buf1 = new Buffer("a", UTF_8);
        buf2 = new Buffer("b", UTF_8);
        buf3 = new Buffer("c", UTF_8);
        testSubscriber = new TestSubscriber<>(0);
        stringSubscriber = new TestSubscriber<>(0);
    }

    @Test
    public void publishesContent() {
        ByteStream stream = new ByteStream(toPublisher(Observable.just(buf1, buf2, buf3)));

        testSubscriber.requestMore(255);
        toObservable(stream.publisher()).subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(3));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void publisherBackpressure() {
        ByteStream stream = new ByteStream(toPublisher(Observable.just(buf1, buf2, buf3)));
        toObservable(stream.publisher()).subscribe(testSubscriber);

        assertThat(testSubscriber.getOnNextEvents().size(), is(0));

        testSubscriber.requestMore(1);
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));

        testSubscriber.requestMore(1);
        assertThat(testSubscriber.getOnNextEvents().size(), is(2));

        testSubscriber.requestMore(1);
        assertThat(testSubscriber.getOnNextEvents().size(), is(3));
        assertThat(testSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void mapsContent() {
        ByteStream stream = new ByteStream(toPublisher(Observable.just(buf1, buf2, buf3)));

        ByteStream mapped = stream.map(this::toUpperCase);

        toObservable(mapped.publisher())
                .map(this::decodeUtf8String)
                .subscribe(stringSubscriber);

        stringSubscriber.requestMore(100);

        assertThat(stringSubscriber.getOnNextEvents(), contains("A", "B", "C"));
        assertThat(stringSubscriber.getOnErrorEvents().size(), is(0));
        assertThat(stringSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void discardsContent() {
        ByteStream stream = new ByteStream(toPublisher(Observable.just(buf1, buf2, buf3)));

        ByteStream discarded = stream.discard();

        toObservable(discarded.publisher()).subscribe(testSubscriber);
        testSubscriber.requestMore(100);

        assertThat(testSubscriber.getOnNextEvents(), empty());
        assertThat(testSubscriber.getOnErrorEvents(), empty());
        assertThat(testSubscriber.getOnCompletedEvents().size(), is(1));
    }

    @Test
    public void aggregatesContent() throws ExecutionException, InterruptedException {
        ByteStream stream = new ByteStream(toPublisher(Observable.just(buf1, buf2, buf3)));

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
