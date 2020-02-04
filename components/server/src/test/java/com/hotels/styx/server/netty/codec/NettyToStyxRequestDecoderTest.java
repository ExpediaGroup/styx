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
package com.hotels.styx.server.netty.codec;

import com.google.common.base.Strings;
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.HttpHeader;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.common.format.SanitisedHttpHeaderFormatter;
import com.hotels.styx.common.format.SanitisedHttpMessageFormatter;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.UniqueIdSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import static com.google.common.base.Charsets.US_ASCII;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.server.UniqueIdSuppliers.fixedUniqueIdSupplier;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpMessageToBytes;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequest;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequestAsBuf;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPECT;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED;
import static io.netty.handler.codec.http.HttpHeaders.Values.CONTINUE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NettyToStyxRequestDecoderTest {
    private final UniqueIdSupplier uniqueIdSupplier = fixedUniqueIdSupplier("1");
    private final DefaultHttpContent contentChunkOne = new DefaultHttpContent(copiedBuffer("content chunk 1 ", UTF_8));
    private final DefaultHttpContent contentChunkTwo = new DefaultHttpContent(copiedBuffer("content chunk 2 ", UTF_8));
    private final DefaultLastHttpContent contentChunkThree = new DefaultLastHttpContent(copiedBuffer("content chunk 3", UTF_8));

    private EmbeddedChannel channel;
    private DefaultHttpRequest chunkedRequestHeaders;
    private CountDownLatch bodyCompletedLatch;

    @BeforeEach
    public void setUp() {
        channel = createEmbeddedChannel(newHttpRequestDecoderWithFlowControl());
        chunkedRequestHeaders = new DefaultHttpRequest(HTTP_1_1, POST, "/foo/bar");
        chunkedRequestHeaders.headers().set(TRANSFER_ENCODING, CHUNKED);
        chunkedRequestHeaders.headers().set(HOST, "foo.com");
        bodyCompletedLatch = new CountDownLatch(1);
    }

    @Test
    public void throwsBadRequestExceptionOnInvalidRequests() throws Throwable {
        Exception e = assertThrows(DecoderException.class, () -> channel.writeInbound(perturb(httpRequestAsBuf(GET, "http://foo.com/"))));
        assertThat(e.getCause().getMessage(), matchesPattern("Error while decoding request.*\\R.*"));
    }

    @Test
    public void rejectsRequestsWithBadURL() throws Throwable {
        String badUri = "/no5_such3_file7.pl?\"><script>alert(73541);</script>56519<script>alert(1)</script>0e134";
        Exception e = assertThrows(DecoderException.class, () -> send(httpRequest(GET, badUri)));
        assertEquals(BadRequestException.class, e.getCause().getClass());
    }

    @Test
    public void sanitisesHeadersInBadRequestException() throws Throwable {
        FullHttpRequest originalRequest = newHttpRequest("/uri");
        HttpHeaders originalRequestHeaders = originalRequest.headers();
        originalRequestHeaders.add("Foo", "foo");
        originalRequestHeaders.add("secret-header", "secret");
        originalRequestHeaders.add("Cookie", "Bar=bar;secret-cookie=secret");
        originalRequest.setDecoderResult(DecoderResult.failure(new Exception("It just didn't work")));

        Exception e = assertThrows(DecoderException.class, () -> decode(originalRequest));

        assertEquals(BadRequestException.class, e.getCause().getClass());
        String breMsg = e.getCause().getMessage();
        assertThat(breMsg, containsString("Foo=foo"));
        assertThat(breMsg, containsString("secret-header=****"));
        assertThat(breMsg, containsString("Bar=bar"));
        assertThat(breMsg, containsString("secret-cookie=****"));
        assertThat(breMsg, not(containsString("secret-header=secret")));
        assertThat(breMsg, not(containsString("secret-cookie=secret")));
    }

    @Test
    public void decodesNettyInternalRequestToStyxRequest() throws Exception {
        FullHttpRequest originalRequest = newHttpRequest("/uri");
        HttpHeaders originalRequestHeaders = originalRequest.headers();
        originalRequestHeaders.add("Foo", "Bar");
        originalRequestHeaders.add("Bar", "Bar");
        originalRequestHeaders.add("Host", "foo.com");

        LiveHttpRequest styxRequest = decode(originalRequest);

        assertThat(styxRequest.id().toString(), is("1"));
        assertThat(styxRequest.url().encodedUri(), is(originalRequest.getUri()));
        assertThat(styxRequest.method(), is(HttpMethod.GET));
        assertThatHttpHeadersAreSame(styxRequest.headers(), originalRequestHeaders);
    }

    @Test
    public void removesExpectHeaderBeforePassingThroughTheRequest() throws Exception {
        FullHttpRequest originalRequest = newHttpRequest("/uri");
        originalRequest.headers().set(EXPECT, CONTINUE);
        originalRequest.headers().set(HOST, "foo.com");

        LiveHttpRequest styxRequest = decode(originalRequest);

        assertThat(styxRequest.header(EXPECT).isPresent(), is(false));
    }

    @Test
    public void streamsIncomingHttpContentToTheContentSubscriber() throws Exception {
        channel.writeInbound(chunkedRequestHeaders);
        LiveHttpRequest request = (LiveHttpRequest) channel.readInbound();

        StringBuilder content = subscribeToContent(request.body(), bodyCompletedLatch);

        channel.writeInbound(contentChunkOne);
        channel.writeInbound(contentChunkTwo);
        channel.writeInbound(contentChunkThree);

        bodyCompletedLatch.await();
        assertThat(content.toString(), is("content chunk 1 content chunk 2 content chunk 3"));
    }

    @Test
    public void buffersIncomingDataUntilSubscriberHasSubscribed() throws Exception {
        channel.writeInbound(chunkedRequestHeaders);
        LiveHttpRequest request = (LiveHttpRequest) channel.readInbound();

        channel.writeInbound(contentChunkOne);
        channel.writeInbound(contentChunkTwo);
        channel.writeInbound(contentChunkThree);

        String content = subscribeAndRead(request.body(), channel);
        assertThat(content, is("content chunk 1 content chunk 2 content chunk 3"));
    }

    @Test
    public void completesWhenThereIsNoMoreContentAndSubscriptionOccurs() {
        channel.writeInbound(chunkedRequestHeaders);
        LiveHttpRequest request = channel.readInbound();

        StepVerifier.create(request.body())
                .expectSubscription()
                .then(() -> channel.writeInbound(EMPTY_LAST_CONTENT))
                .verifyComplete();
    }

    @Test
    public void overridesTheHostHeaderWithTheHostAndPortInTheAbsoluteURI() {
        HttpRequest request = newHttpRequest(URI.create("http://example.net/foo").toString());
        request.headers().set(HOST, "www.example.com:8000");

        LiveHttpRequest styxRequest = decode(request);

        assertThat(styxRequest.headers().get(HOST).get(), is("example.net"));
    }

    @Test
    public void keepsTheHostHeaderTheSameForNonAbsoluteURIs() {
        HttpRequest request = newHttpRequest("/foo");
        request.headers().set(HOST, "www.example.com:8000");
        handle(request, newHttpRequestDecoderWithFlowControl());
        assertThat(request.headers().get(HOST), is("www.example.com:8000"));
    }

    @Test
    public void throwsBadRequestExceptionWhenHttp11RequestMessageLacksAHostHeader() throws Throwable {
        HttpRequest request = newHttpRequest("/foo");
        Exception e = assertThrows(DecoderException.class, () -> handle(request, newHttpRequestDecoderWithFlowControl()));
        assertEquals(BadRequestException.class, e.getCause().getClass());
    }

    @Test
    public void throwsBadRequestExceptionWhenRequestMessageContainsMoreThanOneHostHeader() throws Throwable {
        HttpRequest request = newHttpRequest("/foo");
        request.headers().add(HOST, "example.com");
        request.headers().add(HOST, "example.it");
        Exception e = assertThrows(DecoderException.class, () -> handle(request, newHttpRequestDecoderWithFlowControl()));
        assertEquals(BadRequestException.class, e.getCause().getClass());
    }

    @Test
    public void callsTheEscaperForUnwiseChars() {
        UnwiseCharsEncoder encoder = mock(UnwiseCharsEncoder.class);
        NettyToStyxRequestDecoder decoder = new NettyToStyxRequestDecoder.Builder()
                .uniqueIdSupplier(uniqueIdSupplier)
                .unwiseCharEncoder(encoder)
                .build();

        HttpRequest request = newHttpRequest("/foo");
        when(encoder.encode("/foo")).thenReturn("/foo");
        request.headers().add(HOST, "example.com");
        handle(request, decoder);
        verify(encoder).encode("/foo");
    }

    @Test
    public void throwsBadRequestForTooLongFrames() throws Throwable {
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "http://foo.com/");
        request.headers().set(HOST, "http://foo.com/");
        request.headers().set("foo", Strings.repeat("x", 10000));
        ByteBuf byteBuf = httpMessageToBytes(request);

        Exception e = assertThrows(DecoderException.class, () -> channel.writeInbound(byteBuf));
        assertEquals(BadRequestException.class, e.getCause().getClass());
    }

    @Test
    public void canHandleNettyCookies() {
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "http://foo.com/");
        request.headers().set(HOST, "http://foo.com/");
        request.headers().set("Cookie", "ABC01=\"1\"; ABC02=1; guid=xxxxx-xxx-xxx-xxx-xxxxxxx");

        NettyToStyxRequestDecoder decoder = new NettyToStyxRequestDecoder.Builder()
                .uniqueIdSupplier(uniqueIdSupplier)
                .build();

        LiveHttpRequest styxRequest = decoder.makeAStyxRequestFrom(request, Flux.empty())
                .build();

        LiveHttpRequest expected = new LiveHttpRequest.Builder(
                HttpMethod.GET, "http://foo.com/")
                .cookies(
                        requestCookie("ABC01", "\"1\""),
                        requestCookie("ABC02", "1"),
                        requestCookie("guid", "xxxxx-xxx-xxx-xxx-xxxxxxx")
                )
                .build();
        assertThat(newHashSet(styxRequest.cookies()), is(newHashSet(expected.cookies())));
    }

    @Test
    public void acceptsMalformedCookiesWithRelaxedValidation() {
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "http://foo.com/");
        request.headers().set(HOST, "http://foo.com/");
        request.headers().set("Cookie", "ABC01=\"1\"; ABC02=1; guid=a,b");

        NettyToStyxRequestDecoder decoder = new NettyToStyxRequestDecoder.Builder()
                .uniqueIdSupplier(uniqueIdSupplier)
                .build();

        LiveHttpRequest styxRequest = decoder.makeAStyxRequestFrom(request, Flux.empty())
                .build();

        LiveHttpRequest expected = new LiveHttpRequest.Builder(
                HttpMethod.GET, "http://foo.com/")
                .cookies(
                        requestCookie("ABC01", "\"1\""),
                        requestCookie("ABC02", "1"),
                        requestCookie("guid", "a,b")
                )
                .build();
        assertThat(newHashSet(styxRequest.cookies()), is(newHashSet(expected.cookies())));
    }

    @Test
    public void shouldReleaseAlreadyReadBufferInCaseOfError() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHttpRequestDecoderWithFlowControl());
        channel.writeInbound(newPostRequest("/post"));

        HttpContent httpContentOne = newHttpContent("first chunk");
        channel.writeInbound(httpContentOne);

        channel.pipeline().fireExceptionCaught(new RuntimeException("Some Error"));
        assertThat(httpContentOne.refCnt(), Matchers.is(0));
    }

    @Test
    public void shouldReleaseAlreadyReadBufferInCaseOfChannelGetsInactive() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(newHttpRequestDecoderWithFlowControl());
        channel.writeInbound(newPostRequest("/post"));

        HttpContent httpContentOne = newHttpContent("first chunk");
        channel.writeInbound(httpContentOne);

        channel.pipeline().fireChannelInactive();

        assertThat(httpContentOne.refCnt(), Matchers.is(0));
    }

    private FullHttpResponse send(HttpRequest request) {
        channel.writeInbound(httpMessageToBytes(request));
        return (FullHttpResponse) channel.readOutbound();
    }

    private void assertThatHttpHeadersAreSame(Iterable<HttpHeader> headers, HttpHeaders headers1) {
        assertThat(newArrayList(headers).size(), is(headers1.size()));
        for (HttpHeader header : headers) {
            assertThat(header.value(), is(headers1.get(header.name())));
        }
    }

    private static HttpRequest newPostRequest(String path) {
        HttpRequest chunkedRequestHeaders = new DefaultHttpRequest(HTTP_1_1, POST, "/foo/bar");
        chunkedRequestHeaders.headers().set(TRANSFER_ENCODING, CHUNKED);
        chunkedRequestHeaders.headers().set(HOST, "foo.com");
        return chunkedRequestHeaders;
    }

    private static HttpContent newHttpContent(String content) {
        return new DefaultHttpContent(copiedBuffer(content, UTF_8));
    }

    private String subscribeAndRead(ByteStream content, EmbeddedChannel channel) throws InterruptedException {
        CountDownLatch bodyCompletedLatch = new CountDownLatch(1);

        StringBuilder contentBuilder = subscribeToContent(content, bodyCompletedLatch);
        channel.runPendingTasks();
        bodyCompletedLatch.await();

        return contentBuilder.toString();
    }

    private static StringBuilder subscribeToContent(ByteStream contentStream, CountDownLatch onCompleteLatch) {
        StringBuilder builder = new StringBuilder();
        contentStream.subscribe(new BaseSubscriber<Buffer>() {
            @Override
            public void hookOnComplete() {
                // no-op
                onCompleteLatch.countDown();
            }

            @Override
            public void hookOnNext(Buffer buffer) {
                builder.append(new String(buffer.content(), UTF_8));
            }
        });
        return builder;
    }

    private EmbeddedChannel createEmbeddedChannel(NettyToStyxRequestDecoder nettyToStyxRequestDecoder) {
        return new EmbeddedChannel(
                new io.netty.handler.codec.http.HttpRequestDecoder(),
                nettyToStyxRequestDecoder);
    }

    private static ByteBuf perturb(ByteBuf request) {
        String asString = request.toString(US_ASCII);
        asString = asString.replace("GET", "GET TTTTTTTTTTTTY");
        return copiedBuffer(asString.getBytes(US_ASCII));
    }

    private static FullHttpRequest newHttpRequest(String uri) {
        return new DefaultFullHttpRequest(HTTP_1_1, GET, uri);
    }

    private LiveHttpRequest decode(HttpRequest request) {
        HttpRequestRecorder requestRecorder = new HttpRequestRecorder();
        HttpMessageFormatter formatter = new SanitisedHttpMessageFormatter(new SanitisedHttpHeaderFormatter(
                Arrays.asList("secret-header"), Arrays.asList("secret-cookie")
        ));
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxRequestDecoder.Builder()
                .uniqueIdSupplier(uniqueIdSupplier)
                .httpMessageFormatter(formatter)
                .build(), requestRecorder);
        channel.writeInbound(request);
        return requestRecorder.styxRequest;
    }

    public static NettyToStyxRequestDecoder newHttpRequestDecoderWithFlowControl() {
        return new NettyToStyxRequestDecoder.Builder()
                .build();
    }

    private static class HttpRequestRecorder extends SimpleChannelInboundHandler<LiveHttpRequest> {
        private LiveHttpRequest styxRequest;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, LiveHttpRequest styxRequest) throws Exception {
            this.styxRequest = styxRequest;
        }
    }

    private HttpResponse handle(HttpRequest request, NettyToStyxRequestDecoder nettyToStyxRequestDecoder) {
        EmbeddedChannel channel = createEmbeddedChannel(nettyToStyxRequestDecoder);
        channel.writeInbound(request);
        return (HttpResponse) channel.readOutbound();
    }
}
