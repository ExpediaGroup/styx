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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.client.BadHttpResponseException;
import com.hotels.styx.client.StyxClientException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static io.netty.util.ReferenceCountUtil.retain;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A netty channel handler that reads from a channel and pass the message to a {@link Subscriber}.
 */
final class NettyToStyxResponsePropagator extends SimpleChannelInboundHandler {
    public static final String NAME = NettyToStyxResponsePropagator.class.getSimpleName();
    private static final Logger LOGGER = getLogger(NettyToStyxResponsePropagator.class);

    private final AtomicBoolean responseCompleted = new AtomicBoolean(false);
    private final Subscriber<? super HttpResponse> responseObserver;
    private final boolean flowControlEnabled;
    private final HttpRequest request;

    private final Origin origin;
    private final Long idleTimeoutMillis;
    private Optional<FlowControllingHttpContentProducer> contentProducer = Optional.empty();

    NettyToStyxResponsePropagator(Subscriber<? super HttpResponse> responseObserver, Origin origin) {
        this(responseObserver, origin, false, 5L, TimeUnit.SECONDS);
    }

    NettyToStyxResponsePropagator(Subscriber<? super HttpResponse> responseObserver,
                                  Origin origin,
                                  boolean flowControlEnabled,
                                  long idleTimeout,
                                  TimeUnit timeUnit) {
        this(responseObserver, origin, flowControlEnabled, idleTimeout, timeUnit, null);
    }

    NettyToStyxResponsePropagator(Subscriber<? super HttpResponse> responseObserver,
                                  Origin origin,
                                  boolean flowControlEnabled,
                                  long idleTimeout,
                                  TimeUnit timeUnit,
                                  HttpRequest request) {
        this.responseObserver = responseObserver;
        this.flowControlEnabled = flowControlEnabled;
        this.origin = origin;
        this.idleTimeoutMillis = timeUnit.toMillis(idleTimeout);
        this.request = request;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ensureContentProducerIsCreated(ctx);
        contentProducer.ifPresent(producer -> producer.channelException(toStyxException(cause)));
    }

    private RuntimeException toStyxException(Throwable cause) {
        if (cause instanceof OutOfMemoryError) {
            return new StyxClientException("Styx Client out of memory. " + cause.getMessage(), cause);
        } else {
            return new BadHttpResponseException(origin, cause);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ensureContentProducerIsCreated(ctx);

        TransportLostException cause = new TransportLostException(ctx.channel().remoteAddress(), origin);
        contentProducer.ifPresent(producer -> producer.channelInactive(cause));
    }

    private void scheduleResourcesTearDown(ChannelHandlerContext ctx) {
        ctx.channel().eventLoop().schedule(
                () -> contentProducer.ifPresent(FlowControllingHttpContentProducer::tearDownResources),
                idleTimeoutMillis,
                MILLISECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        ensureContentProducerIsCreated(ctx);

        if (msg instanceof io.netty.handler.codec.http.HttpResponse) {
            io.netty.handler.codec.http.HttpResponse nettyResponse = (io.netty.handler.codec.http.HttpResponse) msg;
            if (nettyResponse.getDecoderResult().isFailure()) {
                emitResponseError(new BadHttpResponseException(origin, nettyResponse.getDecoderResult().cause()));
                return;
            }

            // Can be started with flow controlling disabled
            EventLoop eventLoop = ctx.channel().eventLoop();

            Observable<ByteBuf> contentObservable = Observable
                    .create(new InitializeNettyContentProducerOnSubscribe(eventLoop, this.contentProducer.get()))
                    .doOnUnsubscribe(() -> {
                        eventLoop.submit(() -> this.contentProducer.ifPresent(FlowControllingHttpContentProducer::unsubscribe));
                    });

            HttpResponse response = toStyxResponse(nettyResponse, contentObservable, origin);
            this.responseObserver.onNext(response);
        }
        if (msg instanceof HttpContent) {
            ByteBuf content = ((ByteBufHolder) msg).content();
            if (content.isReadable()) {
                contentProducer.ifPresent(producer -> producer.newChunk(retain(content)));
            }
            if (msg instanceof LastHttpContent) {
                // Note: Netty may send a LastHttpContent as a response to TCP connection close.
                // In this case channelReadComplete event will _not_ follow the LastHttpContent.
                contentProducer.ifPresent(FlowControllingHttpContentProducer::lastHttpContent);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ensureContentProducerIsCreated(ctx);

        if (evt instanceof IdleStateEvent) {
            contentProducer.ifPresent(producer -> producer.channelInactive(
                    new ResponseTimeoutException(
                            origin,
                            "idleStateEvent",
                            producer.receivedBytes(),
                            producer.receivedChunks(),
                            producer.emittedBytes(),
                            producer.emittedChunks())));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private void ensureContentProducerIsCreated(ChannelHandlerContext ctx) {
        if (!this.contentProducer.isPresent()) {
            this.contentProducer = Optional.of(createProducer(ctx, request));
        }
    }

    private FlowControllingHttpContentProducer createProducer(ChannelHandlerContext ctx, HttpRequest request) {
        String requestPrefix = request != null ? format("Request(method=%s, url=%s, id=%s)", request.method(), request.url(), request.id()) : "Request NA";
        String loggingPrefix = format("%s -> %s", ctx.channel().remoteAddress(), ctx.channel().localAddress());

        Runnable askForMore = this.flowControlEnabled ? () -> ctx.channel().read() : () -> {
        };

        return new FlowControllingHttpContentProducer(
                askForMore,
                () -> {
                    ctx.channel().config().setAutoRead(true);
                    emitResponseCompleted();
                },
                this::emitResponseError,
                () -> scheduleResourcesTearDown(ctx),
                format("%s, %s", loggingPrefix, requestPrefix),
                origin);
    }

    private void emitResponseCompleted() {
        if (responseCompleted.compareAndSet(false, true)) {
            responseObserver.onCompleted();
        }
    }

    private void emitResponseError(Throwable cause) {
        if (responseCompleted.compareAndSet(false, true)) {
            this.responseObserver.onError(cause);
        }
    }

    @VisibleForTesting
    static HttpResponse.Builder toStyxResponse(io.netty.handler.codec.http.HttpResponse nettyResponse) {
        HttpResponse.Builder responseBuilder = response(statusWithCode(nettyResponse.getStatus().code()));

        stream(nettyResponse.headers().spliterator(), false)
                .forEach(header -> responseBuilder.addHeader(header.getKey(), header.getValue()));

        return responseBuilder;
    }

    private static HttpResponse toStyxResponse(io.netty.handler.codec.http.HttpResponse nettyResponse, Observable<ByteBuf> contentObservable, Origin origin) {
        try {
            return toStyxResponse(nettyResponse)
                    .body(fromRxObservable(contentObservable))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new BadHttpResponseException(origin, e);
        }
    }

    private static class InitializeNettyContentProducerOnSubscribe implements Observable.OnSubscribe<ByteBuf> {
        private final EventLoop eventLoop;
        private final FlowControllingHttpContentProducer producer;

        InitializeNettyContentProducerOnSubscribe(EventLoop eventLoop, FlowControllingHttpContentProducer producer) {
            this.eventLoop = eventLoop;
            this.producer = producer;
        }

        @Override
        public void call(Subscriber<? super ByteBuf> subscriber) {
            ResponseContentProducer responseContentProducer = new ResponseContentProducer(eventLoop, this.producer);
            subscriber.setProducer(responseContentProducer);
            this.eventLoop.submit(() -> producer.onSubscribed(subscriber));
        }
    }

    private static class ResponseContentProducer implements Producer {
        private final EventLoop eventLoop;
        private final FlowControllingHttpContentProducer producer;

        ResponseContentProducer(EventLoop eventLoop, FlowControllingHttpContentProducer producer) {
            this.eventLoop = eventLoop;
            this.producer = producer;
        }

        @Override
        public void request(long n) {
            eventLoop.submit(() -> producer.request(n));
        }
    }
}
