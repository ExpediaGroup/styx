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
package com.hotels.styx.client.netty.connectionpool;

import com.google.common.base.Throwables;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.StyxClientException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Subscriber;
import rx.observers.TestSubscriber;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.common.HostAndPorts.localhost;
import static com.hotels.styx.client.netty.connectionpool.NettyToStyxResponsePropagator.toStyxResponse;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static io.netty.handler.timeout.IdleStateEvent.ALL_IDLE_STATE_EVENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NettyToStyxResponsePropagatorTest {
    private ByteBuf firstContentChunk = copiedBuffer("first chunk", UTF_8);
    private ByteBuf secondContentChunk = copiedBuffer("second chunk", UTF_8);
    private TestSubscriber<HttpResponse> responseSubscriber;
    private DefaultHttpResponse httpResponseHeaders = new DefaultHttpResponse(HTTP_1_1, OK);
    private DefaultHttpContent httpContentOne = new DefaultHttpContent(firstContentChunk);
    private DefaultHttpContent httpContentTwo = new DefaultHttpContent(secondContentChunk);
    static final Origin SOME_ORIGIN = newOriginBuilder(localhost(12345)).applicationId(GENERIC_APP).build();


    @BeforeMethod
    public void setUp() {
        responseSubscriber = new TestSubscriber<>();
    }

    @Test
    public void notifiesSubscriberForNettyPipelineExceptions() {
        Subscriber<HttpResponse> subscriber = mock(Subscriber.class);
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(subscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.pipeline().fireExceptionCaught(new RuntimeException("Error"));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber, times(1)).onError(captor.capture());
        assertThat(captor.getValue(), is(instanceOf(BadHttpResponseException.class)));
    }

    @Test
    public void propagatesExceptionWhenThereIsDecodeErrorInReceivedResponse() throws Exception {
        Subscriber subscriber = mock(Subscriber.class);
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(subscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(newCorruptedResponse());

        verify(subscriber).onError(any(BadHttpResponseException.class));
    }

    @Test
    public void notifiesSubscriberWhenChannelBecomesInactive() throws Exception {
        Subscriber subscriber = mock(Subscriber.class);
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(subscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.pipeline().fireChannelInactive();

        verify(subscriber).onError(any(TransportLostException.class));
    }

    @Test
    public void ignoresChannelInactiveEventAfterResponseIsCompleted() throws Exception {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(newHttpContent("one"));

        HttpResponse response = responseSubscriber.getOnNextEvents().get(0);
        subscribeToContent(response);

        channel.writeInbound(EMPTY_LAST_CONTENT);
        assertThat(responseSubscriber.getOnCompletedEvents().size(), is(1));

        channel.pipeline().fireChannelInactive();
        assertThat(responseSubscriber.getOnErrorEvents().size(), is(0));
    }

    @Test
    public void doesNotPropagateErrorsTwice() throws Exception {
        NettyToStyxResponsePropagator handler = new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(newHttpContent("one"));

        HttpResponse response = responseSubscriber.getOnNextEvents().get(0);
        subscribeToContent(response);

        channel.pipeline().fireExceptionCaught(new RuntimeException("Simulated exception: something went horribly wrong!"));
        assertThat(responseSubscriber.getOnErrorEvents().size(), is(1));

        channel.pipeline().fireChannelInactive();
        assertThat(responseSubscriber.getOnErrorEvents().size(), is(1));
    }


    @Test
    public void handlesIdleStateEvent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(newHttpContent("one"));

        channel.pipeline().fireUserEventTriggered(ALL_IDLE_STATE_EVENT);

        List<Throwable> errors = responseSubscriber.getOnErrorEvents();
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), is(instanceOf(ResponseTimeoutException.class)));
    }


    @Test
    public void pushesHttpContentOnChannelReadComplete() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));

        channel.writeInbound(httpResponseHeaders);
        HttpResponse response = onNextEvent(responseSubscriber, 0);
        TestSubscriber<ByteBuf> contentSubscriber = subscribeToContent(response);

        HttpContent contentOne = newHttpContent("one");
        channel.writeInbound(contentOne);
        HttpContent contentTwo = newHttpContent("two");
        channel.writeInbound(contentTwo);

        channel.pipeline().fireChannelReadComplete();

        assertNoErrors(contentSubscriber);
        assertThat(asString(onNextEvent(contentSubscriber, 0)), is(asString(contentOne.content())));
        assertThat(asString(onNextEvent(contentSubscriber, 1)), is(asString(contentTwo.content())));
    }


    @Test
    public void pushesContentWhenObserverSubscribes() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(httpContentOne);
        channel.writeInbound(httpContentTwo);

        assertThat(onCompletedEvents(responseSubscriber), is(0));
        assertThat(onNextEvents(responseSubscriber), is(1));
        HttpResponse response = onNextEvent(responseSubscriber, 0);

        TestSubscriber<ByteBuf> contentSubscriber = subscribeToContent(response);
        channel.runPendingTasks();

        assertNoErrors(contentSubscriber);
        assertThat(onNextEvents(contentSubscriber), is(2));
        assertThat(contentChunk(contentSubscriber, 0), is(asString(httpContentOne.content())));
        assertThat(contentChunk(contentSubscriber, 1), is(asString(httpContentTwo.content())));
    }


    @Test
    public void doesNotCompleteResponseObservableIfContentHasNotBeenSubscribed() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(httpContentOne);
        channel.writeInbound(httpContentTwo);
        channel.writeInbound(EMPTY_LAST_CONTENT);

        assertNoErrors(responseSubscriber);
        assertThat(onNextEvents(responseSubscriber), is(1));
        assertThat(onCompletedEvents(responseSubscriber), is(0));
    }

    @Test
    public void completesContentObservableOnLastHttpContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));

        channel.writeInbound(httpResponseHeaders);
        channel.writeInbound(EMPTY_LAST_CONTENT);

        HttpResponse response = onNextEvent(responseSubscriber, 0);
        TestSubscriber<ByteBuf> contentSubscriber = subscribeToContent(response);
        channel.runPendingTasks();

        assertNoErrors(contentSubscriber);
        assertThat(onCompletedEvents(contentSubscriber), is(1));
    }

    @Test
    public void shouldConvertNettyCookieHeaderToStyxCookies() {
        DefaultHttpResponse nettyResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        nettyResponse.headers().add("Set-Cookie", "SESSID=sessId; Domain=.foo.com; Path=/; HttpOnly");
        HttpResponse styxResponse = toStyxResponse(nettyResponse).build();

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
        HttpResponse styxResponse = toStyxResponse(nettyResponse).build();

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
    public void mapsOutOfDirectMemoryExceptionsToResourceExhaustedException() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

        channel.pipeline().fireExceptionCaught(newOutOfDirectMemoryError("Simulated out of direct memory error in a test."));

        assertThat(responseSubscriber.getOnErrorEvents().get(0), is(instanceOf(StyxClientException.class)));
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
        EmbeddedChannel channel = new EmbeddedChannel(new NettyToStyxResponsePropagator(responseSubscriber, SOME_ORIGIN));
        channel.writeInbound(new DefaultHttpResponse(HTTP_1_1, OK));

        HttpContent httpContentOne = newHttpContent("first chunk");
        channel.writeInbound(httpContentOne);

        channel.pipeline().fireChannelInactive();

        assertThat(httpContentOne.refCnt(), is(0));
    }

    private static io.netty.handler.codec.http.HttpResponse newCorruptedResponse() {
        io.netty.handler.codec.http.HttpResponse corruptedResponse = new DefaultHttpResponse(HTTP_1_1, OK);
        corruptedResponse.setDecoderResult(DecoderResult.failure(new RuntimeException("decoding failed")));
        return corruptedResponse;
    }

    private static HttpContent newHttpContent(String content) {
        return new DefaultHttpContent(copiedBuffer(content, UTF_8));
    }


    private TestSubscriber<ByteBuf> subscribeToContent(HttpResponse response) {
        TestSubscriber<ByteBuf> contentSubscriber = new TestSubscriber<>();
        toRxObservable(response.body()).subscribe(contentSubscriber);
        return contentSubscriber;
    }

    private void assertNoErrors(TestSubscriber subscriber) {
        String message = "\nExpected no errors, but got at least: \n";
        if (subscriber.getOnErrorEvents().size() > 0) {
            message = message + stackTrace((Throwable) subscriber.getOnErrorEvents().get(0));
        }
        assertThat(message, subscriber.getOnErrorEvents().size(), is(0));
    }

    private String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private <T> int onNextEvents(TestSubscriber<T> subscriber) {
        return subscriber.getOnNextEvents().size();
    }

    private <T> int onCompletedEvents(TestSubscriber<T> subscriber) {
        return subscriber.getOnCompletedEvents().size();
    }

    private <T> T onNextEvent(TestSubscriber<T> subscriber, int i) {
        return subscriber.getOnNextEvents().get(i);
    }

    private String contentChunk(TestSubscriber<ByteBuf> contentSubscriber, int i) {
        return asString(onNextEvent(contentSubscriber, i));
    }

    private String asString(ByteBuf firstContentChunk) {
        return firstContentChunk.toString(UTF_8);
    }

}
