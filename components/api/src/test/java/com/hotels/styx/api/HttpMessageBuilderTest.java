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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.TestSupport.bodyAsString;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.EMPTY_LIST;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class HttpMessageBuilderTest {
    private ConcreteHttpMessageBuilder builder;

    @BeforeMethod
    public void setUp() {
        builder = new ConcreteHttpMessageBuilder();
    }

    @Test
    public void setsBodyFromByteArray() {
        builder.body(bytes("Hello"));

        assertThat(bodyAsString(builder.body()), is("Hello"));
        assertThat(builder.headers().get(CONTENT_LENGTH), is(contentLength("Hello")));
    }

    @Test
    public void setsBodyFromByteBuffer() {
        builder.body(byteBuffer("Hello"));

        assertThat(bodyAsString(builder.body()), is("Hello"));
        assertThat(builder.headers().get(CONTENT_LENGTH), is(contentLength("Hello")));
    }

    @Test
    public void setsBodyFromString() {
        builder.body("Hello");

        assertThat(bodyAsString(builder.body()), is("Hello"));
        assertThat(builder.headers().get(CONTENT_LENGTH), is(contentLength("Hello")));
    }

    @Test
    public void setsBodyFromObservableOfByteBuf() {
        builder.body(byteBufObservable("He", "llo"));

        assertThat(bodyAsString(builder.body()), is("Hello"));
        assertThat(builder.headers().get(CONTENT_LENGTH), is(nullValue()));
    }

    @Test
    public void setsBodyFromOtherBody() {
        builder.body(new HttpMessageBody(byteBufObservable("Hello")));

        assertThat(bodyAsString(builder.body()), is("Hello"));
        assertThat(builder.headers().get(CONTENT_LENGTH), is(nullValue()));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullHeaderName() {
        builder.header(null, "value");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsAddNullHeaderName() {
        builder.addHeader(null, "value");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullHeaderValue() {
        builder.header("name", (String) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsAddNullHeaderValueString() {
        builder.addHeader("name", (String) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsAddNullHeaderValueObject() {
        builder.addHeader("name", (Object) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullHeaderValueIterable() {
        builder.header("name", (Iterable<?>) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsAddNullHeaderValueIterable() {
        builder.addHeader("name", (Iterable<?>) null);
    }

    @Test
    public void stripsNullHeaderValues() {
        builder.header("name", asList("value1", null, "value2"));

        assertThat(builder.headers().getAll("name"), contains("value1", "value2"));
    }

    @Test
    public void stripsNullHeaderAddValues() {
        builder.addHeader("name", asList("value1", null, "value2"));

        assertThat(builder.headers().getAll("name"), contains("value1", "value2"));
    }

    @Test
    public void allowsEmptyHeaderIterable() {
        builder.header("name", EMPTY_LIST);

        assertThat(builder.headers().getAll("name"), is(emptyIterable()));
    }

    @Test
    public void allowsAddEmptyHeaderIterable() {
        builder.addHeader("name", EMPTY_LIST);

        assertThat(builder.headers().getAll("name"), is(emptyIterable()));
    }

    static class ConcreteHttpMessageBuilder extends HttpMessageBuilder<ConcreteHttpMessageBuilder, HttpMessage> {
        {
            headers(new HttpHeaders.Builder());
        }

        @Override
        public HttpMessage build() {
            return null;
        }
    }

    private static String contentLength(String content) {
        return String.valueOf(bytes(content).length);
    }

    private static byte[] bytes(String content) {
        return content.getBytes(UTF_8);
    }

    private static ByteBuffer byteBuffer(String string) {
        return ByteBuffer.wrap(bytes(string));
    }

    private static Observable<ByteBuf> byteBufObservable(String... strings) {
        return Observable.from(stream(strings)
                .map(HttpMessageBuilderTest::buf)
                .collect(toList()));
    }

    private static ByteBuf buf(CharSequence charSequence) {
        return Unpooled.copiedBuffer(charSequence, UTF_8);
    }

}