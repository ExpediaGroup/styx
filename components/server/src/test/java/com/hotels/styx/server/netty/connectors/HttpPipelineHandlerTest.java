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
package com.hotels.styx.server.netty.connectors;

import com.google.common.collect.ObjectArrays;
import com.hotels.styx.api.ContentOverflowException;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.server.HttpErrorStatusListener;
import com.hotels.styx.server.RequestStatsCollector;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.client.StyxClientException;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.RequestTimeoutException;
import com.hotels.styx.server.netty.codec.NettyToStyxRequestDecoder;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler.HttpResponseWriterFactory;
import com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;
import rx.subjects.PublishSubject;
import com.hotels.styx.api.HttpRequest;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.toArray;
import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderValues.CLOSE;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State.ACCEPTING_REQUESTS;
import static com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State.SENDING_RESPONSE;
import static com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State.SENDING_RESPONSE_CLIENT_CLOSED;
import static com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State.TERMINATED;
import static com.hotels.styx.server.netty.connectors.HttpPipelineHandler.State.WAITING_FOR_RESPONSE;
import static com.hotels.styx.server.netty.connectors.ResponseEnhancer.DO_NOT_MODIFY_RESPONSE;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpMessageToBytes;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequest;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequestAsBuf;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class HttpPipelineHandlerTest {
    private final HttpHandler respondingHandler = (request, context) -> StyxObservable.of(response(OK).build());
    private final HttpHandler doNotRespondHandler = (request, context) -> fromRxObservable(Observable.never());

    private HttpErrorStatusListener errorListener;
    private CodaHaleMetricRegistry metrics;

    // Cannot use lambda expression below, because spy() does not understand them.
    private final HttpHandler respondingPipeline = spy(new HttpHandler() {
        @Override
        public StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
            return respondingHandler.handle(request, context);
        }
    });

    private ChannelHandlerContext ctx;
    private PublishSubject<HttpResponse> responseObservable;
    private PublishSubject<HttpResponse> responseObservable2;
    private CompletableFuture<Void> writerFuture;
    private HttpResponseWriter responseWriter;
    private HttpPipelineHandler handler;
    private HttpHandler pipeline;
    private HttpResponseWriterFactory responseWriterFactory;
    private RequestStatsCollector statsCollector;
    private HttpRequest request;
    private HttpRequest request2;
    private HttpResponse response;
    private HttpResponse response2;
    private AtomicBoolean responseUnsubscribed;
    private AtomicBoolean responseUnsubscribed2;

    private ResponseEnhancer responseEnhancer;

    @BeforeMethod
    public void setUp() throws Exception {
        statsCollector = mock(RequestStatsCollector.class);
        errorListener = mock(HttpErrorStatusListener.class);
        ctx = mockCtx();
        responseObservable = PublishSubject.create();
        responseUnsubscribed = new AtomicBoolean(false);

        writerFuture = new CompletableFuture<>();

        responseWriter = mock(HttpResponseWriter.class);
        when(responseWriter.write(any(HttpResponse.class))).thenReturn(writerFuture);

        responseWriterFactory = mock(HttpResponseWriterFactory.class);
        when(responseWriterFactory.create(anyObject()))
                .thenReturn(responseWriter);

        pipeline = mock(HttpHandler.class);
        when(pipeline.handle(anyObject(), any(HttpInterceptor.Context.class))).thenReturn(fromRxObservable(responseObservable.doOnUnsubscribe(() -> responseUnsubscribed.set(true))));

        request = get("/foo").id("REQUEST-1-ID").build();
        response = response().build();

        responseEnhancer = mock(ResponseEnhancer.class);
        when(responseEnhancer.enhance(any(HttpResponse.Builder.class), any(HttpRequest.class))).thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[0]);

        setupHandlerTo(ACCEPTING_REQUESTS);
    }

    private void setUpFor2Requests() throws Exception {
        responseObservable2 = PublishSubject.create();
        responseUnsubscribed2 = new AtomicBoolean(false);
        response2 = response().build();

        CompletableFuture<Void> writerFuture2 = new CompletableFuture<>();

        HttpResponseWriter responseWriter2 = mock(HttpResponseWriter.class);
        when(responseWriter2.write(any(HttpResponse.class))).thenReturn(writerFuture2);

        responseWriterFactory = mock(HttpResponseWriterFactory.class);
        when(responseWriterFactory.create(anyObject()))
                .thenReturn(responseWriter)
                .thenReturn(responseWriter2);

        pipeline = mock(HttpHandler.class);
        when(pipeline.handle(anyObject(), any(HttpInterceptor.Context.class)))
                .thenReturn(fromRxObservable(responseObservable.doOnUnsubscribe(() -> responseUnsubscribed.set(true))))
                .thenReturn(fromRxObservable(responseObservable2.doOnUnsubscribe(() -> responseUnsubscribed2.set(true))));

        request2 = get("/bar").id("REQUEST-2-ID").build();

        setupHandlerTo(ACCEPTING_REQUESTS);
    }

    private HttpPipelineHandler createHandler(HttpHandler pipeline) throws Exception {
        metrics = new CodaHaleMetricRegistry();
        HttpPipelineHandler handler = handlerWithMocks(pipeline)
                .responseWriterFactory(responseWriterFactory)
                .build();

        handler.channelActive(ctx);

        return handler;
    }

    private void setupHandlerTo(State targetState) throws Exception {
        handler = createHandler(pipeline);

        if (targetState == WAITING_FOR_RESPONSE) {
            handler.channelRead0(ctx, request);
            assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        } else if (targetState == SENDING_RESPONSE) {
            handler.channelRead0(ctx, request);
            assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
            responseObservable.onNext(response);
            assertThat(handler.state(), is(SENDING_RESPONSE));
        }
    }

    @Test
    public void mapsWrappedBadRequestExceptionToBadRequest400ResponseCode() {
        EmbeddedChannel channel = buildEmbeddedChannel(handlerWithMocks());

        String badUri = "/no5_such3_file7.pl?\"><script>alert(73541);</script>56519<script>alert(1)</script>0e134";

        channel.writeInbound(httpMessageToBytes(httpRequest(GET, badUri)));
        DefaultHttpResponse response = (DefaultHttpResponse) channel.readOutbound();

        assertThat(response.getStatus(), is(io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST));
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(null));
        verify(errorListener, only()).proxyErrorOccurred(eq(BAD_REQUEST), any(BadRequestException.class));
    }

    @Test
    public void mapsUnrecoverableInternalErrorsToInternalServerError500ResponseCode() {
        HttpHandler handler = (request, context) -> {
            throw new RuntimeException("Forced exception for testing");
        };
        EmbeddedChannel channel = buildEmbeddedChannel(handlerWithMocks(handler));

        channel.writeInbound(httpRequestAsBuf(GET, "http://foo.com/"));
        DefaultHttpResponse response = (DefaultHttpResponse) channel.readOutbound();

        assertThat(response.status(), is(io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR));
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), any(HttpRequest.class));
        verify(errorListener, only()).proxyErrorOccurred(any(HttpRequest.class), eq(INTERNAL_SERVER_ERROR), any(RuntimeException.class));
    }

    @Test
    public void ignoresConnectionResetException() {
        HttpPipelineHandler pipelineHandler = handlerWithMocks(respondingPipeline).buildForIoExceptionTest();
        EmbeddedChannel channel = buildEmbeddedChannel(pipelineHandler);

        channel.writeInbound(httpRequestAsBuf(GET, "http://foo.com/"));
        assertThat(channel.outboundMessages(), is(empty()));
        verifyZeroInteractions(errorListener);
    }

    @Test
    public void updatesRequestsOngoingCountOnChannelReadEvent() throws Exception {
        HttpPipelineHandler pipelineHandler = handlerWithMocks(doNotRespondHandler)
                .responseEnhancer(DO_NOT_MODIFY_RESPONSE)
                .progressListener(new RequestStatsCollector(metrics))
                .build();

        ChannelHandlerContext ctx = mockCtx();
        pipelineHandler.channelActive(ctx);
        pipelineHandler.channelRead0(ctx, get("/foo").build());

        assertThat(metrics.counter("outstanding").getCount(), is(1L));
    }

    @Test
    public void decrementsRequestsOngoingCountOnResponseCompleted() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();

        HttpPipelineHandler adapter = handlerWithMocks(respondingPipeline)
                .responseEnhancer(DO_NOT_MODIFY_RESPONSE)
                .responseWriterFactory(responseWriterFactory(future))
                .build();

        adapter.channelActive(ctx);
        adapter.channelRead0(ctx, request);
        future.complete(null);

        verify(statsCollector).onComplete(eq(request.id()), eq(200));
    }

    @Test
    public void decrementsRequestsOngoingCountOnChannelInactiveWhenRequestIsOngoing() throws Exception {
        HttpPipelineHandler adapter = handlerWithMocks(doNotRespondHandler)
                .responseEnhancer(DO_NOT_MODIFY_RESPONSE)
                .progressListener(new RequestStatsCollector(metrics))
                .build();
        ChannelHandlerContext ctx = mockCtx();

        adapter.channelActive(ctx);
        adapter.channelRead0(ctx, get("/foo").build());
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        adapter.channelInactive(ctx);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
    }

    @Test
    public void allowsResponseObservableToCompleteAfterAfterDisconnect() throws Exception {
        handler.channelActive(ctx);
        handler.channelRead0(ctx, request);
        verify(statsCollector).onRequest(eq(request.id()));

        responseObservable.onNext(response);

        // When a remote peer (client) has received a response in full,
        // and it has closed the TCP connection:
        handler.channelInactive(ctx);

        // ... only after that the response observable completes:
        responseObservable.onCompleted();

        // ... then treat it like a successfully sent response:
        writerFuture.complete(null);
        verify(statsCollector, never()).onTerminate(eq(request.id()));
        verify(statsCollector).onComplete(eq(request.id()), eq(200));
    }

    @Test
    public void responseFailureInSendingResponseClientConnectedState() throws Exception {
        RuntimeException cause = new RuntimeException("Something went wrong");

        handler.channelActive(ctx);
        handler.channelRead0(ctx, request);
        verify(statsCollector).onRequest(eq(request.id()));

        responseObservable.onNext(response);

        // Remote peer (client) has closed the connection for whatever reason:
        handler.channelInactive(ctx);

        // ... the PipelineHandler is now in SENDING_RESPONSE_CLIENT_DISCONNECTED state,
        // and response writer indicates a failure:
        writerFuture.completeExceptionally(cause);
        verify(statsCollector).onTerminate(eq(request.id()));
        verify(statsCollector, never()).onComplete(eq(request.id()), eq(200));
        assertThat(metrics.counter("outstanding").getCount(), is(0L));
        assertThat(metrics.counter("requests.cancelled.responseWriteError").getCount(), is(1L));

        assertThat(responseUnsubscribed.get(), is(true));
    }

    @Test
    public void channelExceptionAfterClientClosed() throws Exception {
        RuntimeException cause = new RuntimeException("Something went wrong");

        handler.channelActive(ctx);
        handler.channelRead0(ctx, request);
        verify(statsCollector).onRequest(eq(request.id()));

        responseObservable.onNext(response);

        // Remote peer (client) has closed the connection for whatever reason:
        handler.channelInactive(ctx);

        // ... but the channel exception occurs while response writer has finished:
        handler.exceptionCaught(ctx, cause);

        // Then, just log the error,
        verify(errorListener).proxyingFailure(eq(request), eq(response), eq(cause));

        // and allow response writer event (success/failure) to conclude the state machine cycle.
        assertThat(handler.state(), is(SENDING_RESPONSE_CLIENT_CLOSED));
    }

    @Test
    public void decrementsRequestsOngoingOnExceptionCaught() throws Exception {
        HttpPipelineHandler adapter = handlerWithMocks(doNotRespondHandler)
                .progressListener(new RequestStatsCollector(metrics))
                .build();

        ChannelHandlerContext ctx = mockCtx();
        adapter.channelActive(ctx);

        HttpRequest request = get("/foo").build();
        adapter.channelRead0(ctx, request);
        assertThat(metrics.counter("outstanding").getCount(), is(1L));

        adapter.exceptionCaught(ctx, new Throwable("Exception"));
        assertThat(metrics.counter("outstanding").getCount(), is(0L));

        adapter.channelInactive(ctx);
        assertThat(metrics.counter("outstanding").getCount(), is(0L));

        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(request));
    }

    @Test
    public void proxiesRequestAndResponse() throws Exception {
        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        verify(statsCollector).onRequest(request.id());

        responseObservable.onNext(response);
        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));

        writerFuture.complete(null);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));
        verify(statsCollector).onComplete(request.id(), response.status().code());
        verify(ctx.channel(), never()).close();
        verify(ctx, never()).close();
    }

    @Test
    public void prematureRequestIsNotProxiedUntilPreviousResponseIsSuccessfullyWritten() throws Exception {
        setUpFor2Requests();

        // Receive first request
        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        verify(statsCollector).onRequest(request.id());

        // Response arrives, together with response onComplete event.
        // The response onComplete arrives *before* response has been written.
        responseObservable.onNext(response);
        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));

        // Receive second request while still sending previous response.
        handler.channelRead0(ctx, request2);

        // Ensure state machine remains SENDING_RESPONSE, and
        // the second request is NOT yet written.
        assertThat(handler.state(), is(SENDING_RESPONSE));
        verify(ctx.channel(), never()).close();
        verify(ctx, never()).close();
        requestProxiedOnlyOnce(pipeline, request);
    }

    private static void requestProxiedOnlyOnce(HttpHandler pipeline, HttpRequest request) {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(pipeline).handle(captor.capture(), any(HttpInterceptor.Context.class));
        assertThat(captor.getValue().id(), is(request.id()));
    }

    @Test
    public void proxiesPrematureRequestAfterPreviousResponseIsSuccessfullyWritten() throws Exception {
        setUpFor2Requests();

        // Receive first request.
        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        verify(statsCollector).onRequest(request.id());

        // Response arrives, together with response onComplete event.
        // The response onComplete arrives *before* response has been written.
        responseObservable.onNext(response);
        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));
        verify(responseWriter).write(any(HttpResponse.class));

        // Receive second request.
        handler.channelRead0(ctx, request2);
        assertThat(handler.state(), is(SENDING_RESPONSE));

        // Ensure that second request is proxied after the response for the
        // first is successfully completed.
        writerFuture.complete(null);
        requestProxiedTwice(pipeline, request, request2);
    }

    private static void requestProxiedTwice(HttpHandler pipeline, HttpRequest request1, HttpRequest request2) {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(pipeline, times(2)).handle(captor.capture(), any(HttpInterceptor.Context.class));
        assertThat(captor.getAllValues().get(0).id(), is(request1.id()));
        assertThat(captor.getAllValues().get(1).id(), is(request2.id()));
    }

    @Test
    public void closesConnectionWhenMultiplePrematureRequestsAreDetectedInSendingResponseState() throws Exception {
        setUpFor2Requests();

        // Receive request.
        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        verify(statsCollector).onRequest(request.id());

        // Response arrives.
        responseObservable.onNext(response);
        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));
        verify(responseWriter).write(any(HttpResponse.class));

        // Receive second request, while still responding to the previous request.
        // This request will be queued.
        handler.channelRead0(ctx, request2);

        // Receive third request, while one pending request is already queued.
        handler.channelRead0(ctx, request2);

        // Assert that the third request triggers an error.
        assertThat(metrics.counter("requests.cancelled.spuriousRequest").getCount(), is(1L));
        assertThat(writerFuture.isCancelled(), is(true));
        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());

        assertThat(handler.state(), is(TERMINATED));
        verify(ctx).close();
    }

    @Test
    public void pluginRespondsImmediatelyWithoutProxying() throws Exception {
        // Plugin intercepts the request and generates a response. This scenario is different
        // from others because the response emitted "in-place" within the same call stack as
        // the channel read event is being processed.

        setupIdleHandlerWithPluginResponse();
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(SENDING_RESPONSE));
        verify(statsCollector).onRequest(request.id());

        writerFuture.complete(null);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));
        verify(statsCollector).onComplete(request.id(), response.status().code());
        verify(ctx, never()).close();
        verify(ctx.channel(), never()).close();
    }

    private void setupIdleHandlerWithPluginResponse() throws Exception {
        pipeline = mock(HttpHandler.class);
        when(pipeline.handle(anyObject(), any(HttpInterceptor.Context.class))).thenReturn(StyxObservable.of(response));
        handler = createHandler(pipeline);
    }

    @Test
    public void closesTheConnectionAfterProxyingWhenConnectionHeaderHasValueClose() throws Exception {
        HttpRequest oneShotRequest = get("/closeAfterThis").header(CONNECTION, CLOSE).build();

        handler.channelRead0(ctx, oneShotRequest);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
        verify(statsCollector).onRequest(oneShotRequest.id());

        responseObservable.onNext(response);
        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));

        writerFuture.complete(null);
        assertThat(handler.state(), is(TERMINATED));
        verify(statsCollector).onComplete(oneShotRequest.id(), response.status().code());
        verify(ctx).close();
    }

    @Test
    public void transitionsToTerminatedWhenChannelInactivatesInAcceptingRequestsState() throws Exception {
        // In IDLE state,
        // Inbound TCP gets closed
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelInactive(ctx);
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void ioExceptionCaughtInAcceptingRequestsState() throws Exception {
        // In IDLE state,
        // An IO-exception bubbles up the Netty pipeline.
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.exceptionCaught(ctx, new IOException("Error occurred"));
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void nonIoExceptionCaughtInAcceptingRequestsStateChannelRemainsActive() throws Exception {
        // In IDLE state,
        // A non-IO exception bubbles up the Netty pipeline.
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));
        activateChannel(ctx);

        handler.exceptionCaught(ctx, new RuntimeException("Error occurred"));
        verify(errorListener).proxyErrorOccurred(eq(INTERNAL_SERVER_ERROR), anyObject());

        writerFuture.complete(null);
        verify(ctx).close();
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void pluginPipelineThrowsAnExceptionInAcceptingRequestsState() throws Exception {
        Throwable cause = new RuntimeException("Simulated Styx plugin exception");

        pipeline = mock(HttpHandler.class);
        when(pipeline.handle(anyObject(), any(HttpInterceptor.Context.class))).thenThrow(cause);
        handler = createHandler(pipeline);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelRead0(ctx, request);

        verify(responseWriter).write(FullHttpResponse.response(INTERNAL_SERVER_ERROR)
                .header(CONTENT_LENGTH, 29)
                .body("Site temporarily unavailable.", UTF_8)
                .build()
                .toStreamingResponse());

        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(request));
        verify(errorListener).proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, cause);
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void requestTimeoutExceptionOccursInAcceptingRequestsStateAndTcpConnectionRemainsActive() throws Exception {
        RuntimeException cause = new RequestTimeoutException("timeout occurred");
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(REQUEST_TIMEOUT, cause);
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(null));
        verify(responseWriter).write(response(REQUEST_TIMEOUT)
                .header(CONTENT_LENGTH, 15)
                .build());
    }

    @Test
    public void tooLongFrameExceptionOccursInIdleStateAndTcpConnectionRemainsActive() throws Exception {
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new DecoderException("timeout occurred", new BadRequestException("bad request", new TooLongFrameException("too long frame")));
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(REQUEST_ENTITY_TOO_LARGE, cause);
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(request));
        verify(responseWriter).write(response(REQUEST_ENTITY_TOO_LARGE)
                .header(CONTENT_LENGTH, 24)
                .build());
    }

    @Test
    public void badRequestExceptionExceptionOccursInIdleStateAndTcpConnectionRemainsActive() throws Exception {
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new DecoderException("timeout occurred", new BadRequestException("bad request", new RuntimeException("random bad request failure")));
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(BAD_REQUEST, cause);
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(request));
        verify(responseWriter).write(response(BAD_REQUEST)
                .header(CONTENT_LENGTH, 11)
                .build());
    }

    @Test
    public void responseObservableEmitsErrorInWaitingForResponseState() throws Exception {
        // In Waiting For Response state,
        // Response observable emits error.
        setupHandlerTo(WAITING_FOR_RESPONSE);

        responseObservable.onError(new RuntimeException("Request Send Error"));

        assertThat(responseUnsubscribed.get(), is(true));
        writerFuture.complete(null);
        verify(statsCollector).onComplete(request.id(), 500);

        // NOTE: channel closure is not verified. This is because cannot mock channel future.
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void responseObservableEmitsContentOverflowExceptionInWaitingForResponseState() throws Exception {
        // In Waiting For Response state,
        // When response observable emits ContentOverflowException.
        // Then respond with BAD_GATEWAY and close the channel
        setupHandlerTo(WAITING_FOR_RESPONSE);

        responseObservable.onError(new ContentOverflowException("Request Send Error"));

        assertThat(responseUnsubscribed.get(), is(true));
        verify(responseWriter).write(FullHttpResponse.response(BAD_GATEWAY)
                .header(CONTENT_LENGTH, "29")
                .body("Site temporarily unavailable.", UTF_8)
                .build()
                .toStreamingResponse());
        verify(responseEnhancer).enhance(any(HttpResponse.Builder.class), eq(request));

        writerFuture.complete(null);
        verify(statsCollector).onComplete(request.id(), 502);
        verify(errorListener).proxyErrorOccurred(any(HttpRequest.class), eq(BAD_GATEWAY), any(RuntimeException.class));

        // NOTE: channel closure is not verified. This is because cannot mock channel future.
        verify(ctx).close();
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void mapsStyxClientExceptionToInternalServerErrorInWaitingForResponseState() throws Exception {
        // In Waiting For Response state,
        // The response observable emits a StyxClientException.
        // Then, respond with INTERNAL_SERVER_ERROR and close the channel.
        setupHandlerTo(WAITING_FOR_RESPONSE);

        responseObservable.onError(new StyxClientException("Client error occurred", new RuntimeException("Something went wrong")));

        assertThat(responseUnsubscribed.get(), is(true));
        verify(responseWriter).write(FullHttpResponse.response(INTERNAL_SERVER_ERROR)
                .header(CONTENT_LENGTH, "29")
                .body("Site temporarily unavailable.", UTF_8)
                .build()
                .toStreamingResponse());

        writerFuture.complete(null);
        verify(statsCollector).onComplete(request.id(), 500);
        verify(errorListener).proxyErrorOccurred(any(HttpRequest.class), eq(INTERNAL_SERVER_ERROR), any(RuntimeException.class));

        // NOTE: channel closure is not verified. This is because cannot mock channel future.
        verify(ctx).close();
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void channelInactiveEventInWaitingForResponseState() throws Exception {
        // In Waiting For Response state,
        // Inbound TCP connection gets closed
        setupHandlerTo(WAITING_FOR_RESPONSE);

        handler.channelInactive(ctx);

        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void ioExceptionInWaitingForResponseState() throws Exception {
        // In Waiting for Response state,
        // IO exception bubbles up the Netty pipeline.
        setupHandlerTo(WAITING_FOR_RESPONSE);

        handler.exceptionCaught(ctx, new IOException("TCP connection broke"));

        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void nonIoExceptionInWaitingForResponseStateAndTcpConnectionIsActive() throws Exception {
        // In waiting for Response state,
        // A non-IO exception bubbles up the Netty pipeline.
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new RuntimeException("Someting went wrong in the netty pipeline");
        handler.exceptionCaught(ctx, cause);

        assertThat(responseUnsubscribed.get(), is(true));
        verify(errorListener).proxyErrorOccurred(INTERNAL_SERVER_ERROR, cause);
        responseWriter.write(response(INTERNAL_SERVER_ERROR).build());
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void requestTimeoutExceptionOccursInWaitingForResponseStateAndTcpConnectionRemainsActive() throws Exception {
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new RequestTimeoutException("timeout occurred");
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(REQUEST_TIMEOUT, cause);
        responseWriter.write(response(REQUEST_TIMEOUT).build());
    }

    @Test
    public void tooLongFrameExceptionOccursInWaitingForResponseStateAndTcpConnectionRemainsActive() throws Exception {
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new DecoderException("timeout occurred", new BadRequestException("bad request", new TooLongFrameException("too long frame")));
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(REQUEST_ENTITY_TOO_LARGE, cause);
        responseWriter.write(response(REQUEST_ENTITY_TOO_LARGE).build());
    }

    @Test
    public void badRequestExceptionExceptionOccursInWaitingForResponseStateAndTcpConnectionRemainsActive() throws Exception {
        setupHandlerTo(WAITING_FOR_RESPONSE);

        RuntimeException cause = new DecoderException("timeout occurred", new BadRequestException("bad request", new RuntimeException("random bad request failure")));
        handler.exceptionCaught(ctx, cause);

        verify(errorListener).proxyErrorOccurred(BAD_REQUEST, cause);
        responseWriter.write(response(BAD_REQUEST).build());
    }

    @Test
    public void responseObservableEmitsErrorInSendingResponseState() throws Exception {
        // In Sending Response state,
        // The response observable emits a response, which is being written out.
        // Then, response observable emits an error.
        // Because we have started to write out the response, let's write out a log message, and pretend as if nothing happened.
        setupHandlerTo(SENDING_RESPONSE);

        responseObservable.onError(new RuntimeException("Spurious error occurred"));

        assertThat(handler.state(), is(SENDING_RESPONSE));

        verify(errorListener).proxyingFailure(any(HttpRequest.class), any(HttpResponse.class), any(Throwable.class));
    }

    @Test
    public void responseWriteFailsInSendingResponseState() throws Exception {
        // In Sending Response state,
        // The HttpResponseWriter future completes with an error.
        RuntimeException cause = new RuntimeException("Response write failed");
        setupHandlerTo(SENDING_RESPONSE);

        writerFuture.completeExceptionally(cause);

        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());
        verify(errorListener).proxyWriteFailure(any(HttpRequest.class), eq(response(OK).build()), any(RuntimeException.class));

        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void ioExceptionInSendingResponseState() throws Exception {
        // In Sending Response state,
        // An IO exception bubbles up the Netty pipeline
        setupHandlerTo(SENDING_RESPONSE);

        handler.exceptionCaught(ctx, new IOException("something went wrong"));
        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void anotherxceptionInSendingResponseState() throws Exception {
        // In Sending Response state,
        // An non-IO exception bubbles up the Netty pipeline
        setupHandlerTo(SENDING_RESPONSE);

        handler.exceptionCaught(ctx, new RuntimeException("something went wrong"));
        assertThat(responseUnsubscribed.get(), is(true));
        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
    }

    @Test
    public void discardsOnCompleteEventsForEarlierRequestsInWaitingForResponseState() throws Exception {
        setUpFor2Requests();

        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable.onNext(response);
        assertThat(handler.state(), is(SENDING_RESPONSE));

        writerFuture.complete(null);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelRead0(ctx, request2);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable.onCompleted();
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
    }

    @Test
    public void discardsOnCompleteEventsForEarlierRequestsInSendingResponseState() throws Exception {
        setUpFor2Requests();

        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable.onNext(response);
        assertThat(handler.state(), is(SENDING_RESPONSE));

        writerFuture.complete(null);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelRead0(ctx, request2);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable2.onNext(response2);
        assertThat(handler.state(), is(SENDING_RESPONSE));

        responseObservable.onCompleted();
        assertThat(handler.state(), is(SENDING_RESPONSE));
    }

    @Test
    public void discardsOnErrorForEarlierRequestsInWaitingForResponseState() throws Exception {
        setUpFor2Requests();

        handler.channelRead0(ctx, request);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable.onNext(response);
        assertThat(handler.state(), is(SENDING_RESPONSE));

        writerFuture.complete(null);
        assertThat(handler.state(), is(ACCEPTING_REQUESTS));

        handler.channelRead0(ctx, request2);
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));

        responseObservable.onError(new RuntimeException("Simulated Exception - something went wrong!"));
        assertThat(handler.state(), is(WAITING_FOR_RESPONSE));
    }

    private static HttpResponseWriterFactory responseWriterFactory(CompletableFuture<Void> future) {
        HttpResponseWriterFactory writerFactory = mock(HttpResponseWriterFactory.class);
        HttpResponseWriter responseWriter = mock(HttpResponseWriter.class);
        when(writerFactory.create(any(ChannelHandlerContext.class))).thenReturn(responseWriter);
        when(responseWriter.write(any(HttpResponse.class))).thenReturn(future);
        return writerFactory;
    }

    private static void activateChannel(ChannelHandlerContext ctx) {
        when(ctx.channel().isActive()).thenReturn(true);
    }

    @Test
    public void cancelsOngoingRequestWhenSpuriousRequestArrivesInWaitingForResponseState() throws Exception {
        // - writes EMPTY_LAST_CONTENT and closes the channel
        // - logs an error message
        // - cancels the ongoing request on the HTTP pipeline
        LoggingTestSupport logger = new LoggingTestSupport(HttpPipelineHandler.class);

        HttpRequest spurious = get("/bar").build();
        setupHandlerTo(WAITING_FOR_RESPONSE);

        handler.channelRead0(ctx, spurious);

        verify(statsCollector).onTerminate(request.id());
        assertThat(handler.state(), is(TERMINATED));
        assertThat(logger.lastMessage(), is(
                loggingEvent(WARN, "message='Spurious request received while handling another request'.*")));
        assertThat(responseUnsubscribed.get(), is(true));
    }

    private HttpPipelineHandler.Builder handlerWithMocks() {
        return handlerWithMocks(pipeline);
    }

    private HttpPipelineHandler.Builder handlerWithMocks(HttpHandler pipeline) {
        return new HttpPipelineHandler.Builder(pipeline)
                .errorStatusListener(errorListener)
                .responseEnhancer(responseEnhancer)
                .progressListener(statsCollector)
                .metricRegistry(metrics);
    }

    private static ChannelHandlerContext mockCtx() {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ChannelFuture future = channelFutureOk();
        Channel channel = channel();
        when(channel.writeAndFlush(any(ObjectArrays.class))).thenReturn(future);
        when(ctx.writeAndFlush(any(Object.class))).thenReturn(future);
        when(ctx.channel()).thenReturn(channel);

        SocketAddress localAddress = mock(SocketAddress.class);
        when(localAddress.toString()).thenReturn("localhost:1");
        when(ctx.channel().localAddress()).thenReturn(localAddress);

        SocketAddress remoteAddress = mock(SocketAddress.class);
        when(remoteAddress.toString()).thenReturn("localhost:2");
        when(ctx.channel().remoteAddress()).thenReturn(remoteAddress);

        return ctx;
    }

    private static Channel channel() {
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        return channel;
    }

    private static ChannelFuture channelFutureOk() {
        ChannelFuture future = mock(ChannelFuture.class);
        when(future.isSuccess()).thenReturn(true);
        return future;
    }

    private static EmbeddedChannel buildEmbeddedChannel(HttpPipelineHandler.Builder builder) {
        return buildEmbeddedChannel(builder.build());
    }

    private static EmbeddedChannel buildEmbeddedChannel(ChannelHandler... lastHandlers) {
        Iterable<ChannelHandler> commonHandlers = asList(
                new HttpRequestDecoder(),
                new HttpObjectAggregator(6000),
                new NettyToStyxRequestDecoder.Builder().build());

        return new EmbeddedChannel(toArray(concat(commonHandlers, asList(lastHandlers)), ChannelHandler.class));
    }
}
