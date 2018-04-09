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

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.hotels.styx.api.TestSupport.bodyAsString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;
import static rx.Observable.just;

public class HttpMessageBodyTest {
    @Test
    public void canBeConstructedWithObservableOfContent() {
        HttpMessageBody body = new HttpMessageBody(observable("Hello", ", ", "World!"));

        assertThat(bodyAsString(body), is("Hello, World!"));
    }

    private Function<ByteBuf, String> toStringDecoder(Charset charset) {
        return bytes -> bytes.toString(UTF_8);
    }

    private Function<ByteBuf, String> utf8Decoder = toStringDecoder(Charsets.UTF_8);

    @Test
    public void aggregatesContent() {
        HttpMessageBody body = new HttpMessageBody(observable("foo", "bar"));

        Observable<String> aggregatedBody = body.decode(utf8Decoder, 100);

        assertThat(aggregatedBody.toBlocking().first(), is("foobar"));
    }

    @Test(expectedExceptions = ContentOverflowException.class)
    public void throwsExceptionIfMaximumContentSizeIsExceeded() {
        HttpMessageBody body = new HttpMessageBody(observable("foo", "bar", "boo", "far"));

        body.decode(utf8Decoder, 10).toBlocking().first();
    }

    @Test
    public void releasesAnyAdditionalChunksAfterDecodingFails() throws Exception {
        Throwable cause = null;
        ByteBuf buf1 = buf("foo");
        ByteBuf buf2 = buf("barr");
        ByteBuf additional1 = buf("bar");
        ByteBuf additional2 = buf("bar");

        HttpMessageBody body = new HttpMessageBody(just(buf1, buf2, additional1, additional2));

        assertThat(buf1.refCnt(), is(1));
        assertThat(buf2.refCnt(), is(1));
        assertThat(additional1.refCnt(), is(1));
        assertThat(additional2.refCnt(), is(1));

        try {
            body.decode(bytes -> bytes.toString(UTF_8), 6).toBlocking().single();
        } catch (RuntimeException e) {
            cause = e;
        }

        assertThat(cause, instanceOf(ContentOverflowException.class));
        assertThat(buf1.refCnt(), is(0));
        assertThat(buf2.refCnt(), is(0));
        assertThat(additional1.refCnt(), is(0));
        assertThat(additional2.refCnt(), is(0));
    }

    @Test
    public void releasesContentBuffers() throws ExecutionException, InterruptedException {
        ByteBuf buf1 = buf("foo");
        ByteBuf buf2 = buf("bar");

        HttpMessageBody body = new HttpMessageBody(just(buf1, buf2));

        assertThat(buf1.refCnt(), is(1));
        assertThat(buf2.refCnt(), is(1));

        CompletableFuture<Boolean> future = body.releaseContentBuffers();

        future.get(); // synchronise

        assertThat(buf1.refCnt(), is(0));
        assertThat(buf2.refCnt(), is(0));
    }

    @Test
    public void releasesAggregatedByteBufferAfterDecoding() throws Exception {
        ByteBuf buf1 = buf("foo");
        ByteBuf buf2 = buf("bar");

        HttpMessageBody body = new HttpMessageBody(just(buf1, buf2));

        assertThat(buf1.refCnt(), is(1));
        assertThat(buf2.refCnt(), is(1));

        String result = body.decode(bytes -> bytes.toString(US_ASCII), 100).toBlocking().single();
        assertThat(result, is("foobar"));

        assertThat(buf1.refCnt(), is(0));
        assertThat(buf2.refCnt(), is(0));
    }

    @Test
    public void releasesAggregatedByteBufferWhenDecoderFails() throws Exception {
        ByteBuf buf1 = buf("foo");
        ByteBuf buf2 = buf("bar");

        HttpMessageBody body = new HttpMessageBody(just(buf1, buf2));

        assertThat(buf1.refCnt(), is(1));
        assertThat(buf2.refCnt(), is(1));

        try {
            Function<ByteBuf, String> failingDecoder = (buf) -> {
                throw new RuntimeException("Simulating a decoder failed");
            };
            body.decode(failingDecoder, 100).toBlocking().single();
            fail("No exception thrown");
        } catch (RuntimeException e) {
            // pass
        }

        assertThat(buf1.refCnt(), is(0));
        assertThat(buf2.refCnt(), is(0));
    }

    @Test
    public void releasesAggregatedBytesWhenObservableEmitsAnError() throws Exception {
        ByteBuf buf1 = buf("foo");
        ByteBuf buf2 = buf("bar");

        ReplaySubject<ByteBuf> content = ReplaySubject.create();
        content.onNext(buf1);
        content.onNext(buf2);
        content.onError(new RuntimeException("something went wrong"));
        HttpMessageBody body = new HttpMessageBody(content);

        Observable<String> aggregated = body.decode(bytes -> bytes.toString(UTF_8), 1000);

        TestSubscriber<String> subscriber = new TestSubscriber<>();
        aggregated.subscribe(subscriber);
        assertThat(subscriber.getOnErrorEvents().size(), is(1));

        assertThat(buf1.refCnt(), is(0));
        assertThat(buf2.refCnt(), is(0));
    }

    private static Observable<ByteBuf> observable(String... strings) {
        List<ByteBuf> byteBufs = Stream.of(strings)
                .map(HttpMessageBodyTest::buf)
                .collect(toList());

        return Observable.from(byteBufs);
    }

    private static ByteBuf buf(String string) {
        return Unpooled.copiedBuffer(string, UTF_8);
    }

}