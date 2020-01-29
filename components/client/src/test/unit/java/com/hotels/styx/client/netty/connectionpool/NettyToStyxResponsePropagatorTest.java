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
package com.hotels.styx.client.netty.connectionpool;

import com.google.common.base.Throwables;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.exceptions.ContentTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.StyxClientException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.netty.connectionpool.NettyToStyxResponsePropagator.toStyxResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static io.netty.handler.timeout.IdleStateEvent.ALL_IDLE_STATE_EVENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NettyToStyxResponsePropagatorTest {
    private static final String FIRST_CHUNK = "first chunk";
    private static final String SECOND_CHUNK = "second chunk";
    private ByteBuf firstContentChunk = copiedBuffer(FIRST_CHUNK, UTF_8);
    private ByteBuf secondContentChunk = copiedBuffer(SECOND_CHUNK, UTF_8);
    private FluxSink<LiveHttpResponse> responseSubscriber;
    private DefaultHttpResponse httpResponseHeaders = new DefaultHttpResponse(HTTP_1_1, OK);
    private DefaultHttpContent httpContentOne = new DefaultHttpContent(firstContentChunk);
    private DefaultHttpContent httpContentTwo = new DefaultHttpContent(secondContentChunk);
    static final Origin SOME_ORIGIN = newOriginBuilder("localhost", 12345).applicationId(GENERIC_APP).build();


    @BeforeEach
    public void setUp() {
        responseSubscriber = mock(FluxSink.class);
    }

    @Test
    public void notifiesSubscriberForNettyPipelineExceptions() {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.pipeline().fireExceptionCaught(new RuntimeException("Error"));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseSubscriber, times(1)).error(captor.capture());
        assertThat(captor.getValue(), is(instanceOf(BadHttpResponseException.class)));
    }

    @Test
    public void propagatesExceptionWhenThereIsDecodeErrorInReceivedResponse() throws Exception {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(newCorruptedResponse());

        verify(responseSubscriber).error(any(BadHttpResponseException.class));
    }

    @Test
    public void notifiesSubscriberWhenChannelBecomesInactive() throws Exception {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.pipeline().fireChannelInactive();

        verify(responseSubscriber).error(any(TransportLostException.class));
    }

    @Test
    public void doesNotPropagateErrorsTwice() throws Exception {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(httpResponseHeaders);

        LiveHttpResponse response = verifyNextCalledOnResponseSubscriber();

        StepVerifier.create(response.body())
                .then(channel::runPendingTasks) // Execute onSubscribe in FSM
                .then(() -> channel.pipeline().fireExceptionCaught(new RuntimeException())) // Will emit BadHttpResponseException
                .then(() -> channel.pipeline().fireChannelInactive()) // Will emit TransportLostException
                .expectError(BadHttpResponseException.class)
                .verify();

        verify(responseSubscriber, atMostOnce()).error(any());
    }


    @Test
    public void handlesIdleStateEvent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);

        LiveHttpResponse response = verifyNextCalledOnResponseSubscriber();

        StepVerifier.create(response.body())
                .then(channel::runPendingTasks) // Execute onSubscribe in FSM
                .then(() -> channel.pipeline().fireUserEventTriggered(ALL_IDLE_STATE_EVENT))
                .expectError(ContentTimeoutException.class)
                .verify();
    }


    @Test
    public void pushesContentWhenObserverSubscribes() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(httpContentOne);
        channel.writeInbound(httpContentTwo);

        LiveHttpResponse response = verifyNextCalledOnResponseSubscriber();

        StepVerifier.create(response.body())
                .then(channel::runPendingTasks)
                .assertNext(buf -> assertEquals(FIRST_CHUNK, new String(buf.content())))
                .assertNext(buf -> assertEquals(SECOND_CHUNK, new String(buf.content())))
                .thenCancel();
    }


    @Test
    public void doesNotCompleteResponseObservableIfContentHasNotBeenSubscribed() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(httpContentOne);
        channel.writeInbound(httpContentTwo);
        channel.writeInbound(EMPTY_LAST_CONTENT);

        verifyNextCalledOnResponseSubscriber();
        verify(responseSubscriber, never()).error(any());
        verify(responseSubscriber, never()).complete();
    }

    @Test
    public void completesContentObservableOnLastHttpContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));

        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(EMPTY_LAST_CONTENT);

        LiveHttpResponse response = verifyNextCalledOnResponseSubscriber();

        StepVerifier.create(response.body())
                .then(channel::runPendingTasks)
                .verifyComplete();
    }

    @Test
    public void shouldConvertNettyCookieHeaderToStyxCookies() {
        DefaultHttpResponse nettyResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        nettyResponse.headers().add("Set-Cookie", "SESSID=sessId; Domain=.foo.com; Path=/; HttpOnly");
        LiveHttpResponse styxResponse = toStyxResponse(nettyResponse).build();

        assertThat(styxResponse.header("Set-Cookie"), isValue("SESSID=sessId; Domain=.foo.com; Path=/; HttpOnly"));
        assertThat(styxResponse.cookie("SESSID"), equalTo(
                Optional.of(responseCookie("SESSID", "sessId")
                        .domain(".foo.com")
                        .path("/")
                        .httpOnly(true)
                        .build())));
    }

    @Test
    public void shouldLeaveIntactQuotedCookieValues() {
        DefaultHttpResponse nettyResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        nettyResponse.headers().add("Set-Cookie", "SESSID=\"sessId\"; Domain=.foo.com; Path=/; HttpOnly");
        LiveHttpResponse styxResponse = toStyxResponse(nettyResponse).build();

        assertThat(styxResponse.header("Set-Cookie"), isValue("SESSID=\"sessId\"; Domain=.foo.com; Path=/; HttpOnly"));
        assertThat(styxResponse.cookie("SESSID"), equalTo(
                Optional.of(responseCookie("SESSID", "\"sessId\"")
                        .domain(".foo.com")
                        .path("/")
                        .httpOnly(true)
                        .build())));
    }

    @Test
    public void shouldReleaseAlreadyReadBufferInCaseOfError() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

        HttpContent httpContentOne = newHttpContent("first chunk");
        channel.writeInbound(httpContentOne);

        channel.pipeline().fireExceptionCaught(new RuntimeException("Some Error"));

        assertThat(httpContentOne.refCnt(), is(0));
    }

    @Test
    public void mapsOutOfDirectMemoryExceptionsToStyxClientException() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

        channel.pipeline().fireExceptionCaught(newOutOfDirectMemoryError("Simulated out of direct memory error in a test."));

        verify(responseSubscriber).error(any(StyxClientException.class));
    }

    private OutOfDirectMemoryError newOutOfDirectMemoryError(String message) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        // OutOfDirectMemoryError has a package-private constructor.
        // Use reflection to instantiate it for testing purposes.
        Constructor<OutOfDirectMemoryError> constructor = null;
        try {
            constructor = OutOfDirectMemoryError.class.getDeclaredConstructor(String.class);
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
        constructor.setAccessible(true);
        return constructor.newInstance(message);
    }

    @Test
    public void shouldReleaseAlreadyReadBufferInCaseOfChannelGetsInactive() throws Exception {
        FluxSink subscriber = mock(FluxSink.class);
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(subscriber, SOME_ORIGIN));
        channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

        HttpContent httpContentOne = newHttpContent("first chunk");
        channel.writeInbound(httpContentOne);

        channel.pipeline().fireChannelInactive();

        assertThat(httpContentOne.refCnt(), is(0));
    }

    @Test
    public void closesConnectionWhenConnectionCloseHeaderIsPresent() {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        DefaultHttpResponse responseHeaders = new DefaultHttpResponse(HTTP_1_1, OK);
        responseHeaders.headers().add(CONNECTION, CLOSE);

        channel.writeInbound(responseHeaders);
        channel.writeInbound(newHttpContent("one"));
        channel.writeInbound(EMPTY_LAST_CONTENT);

        assertThat(channel.isOpen(), is(false));
        assertThat(channel.isActive(), is(false));
    }

    @Test
    public void ignoresCaseOfConnectionCloseHeader() {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        DefaultHttpResponse responseHeaders = new DefaultHttpResponse(HTTP_1_1, OK);
        responseHeaders.headers().add("CONNECTION", "CLOSE");

        channel.writeInbound(responseHeaders);
        channel.writeInbound(newHttpContent("one"));
        channel.writeInbound(EMPTY_LAST_CONTENT);

        assertThat(channel.isOpen(), is(false));
        assertThat(channel.isActive(), is(false));
    }

    private static io.netty.handler.codec.http.HttpResponse newCorruptedResponse() {
        io.netty.handler.codec.http.HttpResponse corruptedResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        corruptedResponse.setDecoderResult(DecoderResult.failure(new RuntimeException("decoding failed")));
        return corruptedResponse;
    }

    private static HttpContent newHttpContent(String content) {
        return new DefaultHttpContent(copiedBuffer(content, UTF_8));
    }

    private LiveHttpResponse verifyNextCalledOnResponseSubscriber() {
        ArgumentCaptor<LiveHttpResponse> responseArg = ArgumentCaptor.forClass(LiveHttpResponse.class);
        verify(responseSubscriber).next(responseArg.capture());
        return responseArg.getValue();
    }
}
