/*
  Copyright (C) 2013-2019 Expedia Inc.

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
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.Buffers;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.api.extension.Origin;
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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import reactor.core.publisher.FluxSink;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hotels.styx.api.HttpResponseStatus.statusWithCode;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
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
    private final FluxSink<LiveHttpResponse> sink;
    private final LiveHttpRequest request;

    private final Origin origin;
    private final Long idleTimeoutMillis;
    private Optional<FlowControllingHttpContentProducer> contentProducer = Optional.empty();

    // `toBeClosed` doesn't have to be volatile because all Netty events are guaranteed
    // to be delivered from the same thread.
    private boolean toBeClosed;

    NettyToStyxResponsePropagator(FluxSink<LiveHttpResponse> sink, Origin origin) {
        this(sink, origin, 5L, TimeUnit.SECONDS, null);
    }

    NettyToStyxResponsePropagator(FluxSink<LiveHttpResponse> sink,
                                  Origin origin,
                                  long idleTimeout,
                                  TimeUnit timeUnit,
                                  LiveHttpRequest request) {
        this.sink = sink;
        this.origin = origin;
        this.idleTimeoutMillis = timeUnit.toMillis(idleTimeout);
        this.request = request;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        getContentProducer(ctx).channelException(toStyxException(cause));
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
        TransportLostException cause = new TransportLostException(ctx.channel().remoteAddress(), origin);
        getContentProducer(ctx).channelInactive(cause);
    }

    private void scheduleResourcesTearDown(ChannelHandlerContext ctx) {
        ctx.channel().eventLoop().schedule(
                () -> contentProducer.ifPresent(FlowControllingHttpContentProducer::tearDownResources),
                idleTimeoutMillis,
                MILLISECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        FlowControllingHttpContentProducer producer = getContentProducer(ctx);

        if (msg instanceof io.netty.handler.codec.http.HttpResponse) {
            io.netty.handler.codec.http.HttpResponse nettyResponse = (io.netty.handler.codec.http.HttpResponse) msg;
            if (nettyResponse.getDecoderResult().isFailure()) {
                emitResponseError(new BadHttpResponseException(origin, nettyResponse.getDecoderResult().cause()));
                return;
            }

            ctx.channel().config().setAutoRead(false);
            ctx.channel().read();

            // Can be started with flow controlling disabled
            EventLoop eventLoop = ctx.channel().eventLoop();

            Publisher<Buffer> contentPublisher = new ContentPublisher(eventLoop, getContentProducer(ctx));

            if ("close".equalsIgnoreCase(nettyResponse.headers().get(CONNECTION))) {
                toBeClosed = true;
            }

            LiveHttpResponse response = toStyxResponse(nettyResponse, contentPublisher, origin);
            this.sink.next(response);
        }
        if (msg instanceof HttpContent) {
            ByteBuf content = ((ByteBufHolder) msg).content();
            if (content.isReadable()) {
                producer.newChunk(retain(content));
            }
            if (msg instanceof LastHttpContent) {
                // Note: Netty may send a LastHttpContent as a response to TCP connection close.
                // In this case channelReadComplete event will _not_ follow the LastHttpContent.
                producer.lastHttpContent();
                if (toBeClosed) {
                    ctx.channel().close();
                }
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        FlowControllingHttpContentProducer producer = getContentProducer(ctx);

        if (evt instanceof IdleStateEvent) {
            producer.channelInactive(
                    new ResponseTimeoutException(
                            origin,
                            "idleStateEvent",
                            producer.receivedBytes(),
                            producer.receivedChunks(),
                            producer.emittedBytes(),
                            producer.emittedChunks()));
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    private FlowControllingHttpContentProducer getContentProducer(ChannelHandlerContext ctx) {
        if (!this.contentProducer.isPresent()) {
            this.contentProducer = Optional.of(createProducer(ctx, request));
        }
        return this.contentProducer.get();
    }

    private FlowControllingHttpContentProducer createProducer(ChannelHandlerContext ctx, LiveHttpRequest request) {
        String requestPrefix = request != null ? format("Request(method=%s, url=%s, id=%s)", request.method(), request.url(), request.id()) : "Request NA";
        String loggingPrefix = format("%s -> %s", ctx.channel().remoteAddress(), ctx.channel().localAddress());

        return new FlowControllingHttpContentProducer(
                () -> ctx.channel().read(),
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
            sink.complete();
        }
    }

    private void emitResponseError(Throwable cause) {
        if (responseCompleted.compareAndSet(false, true)) {
            this.sink.error(cause);
        }
    }

    @VisibleForTesting
    static LiveHttpResponse.Builder toStyxResponse(io.netty.handler.codec.http.HttpResponse nettyResponse) {
        LiveHttpResponse.Builder responseBuilder = response(statusWithCode(nettyResponse.getStatus().code()));

        stream(nettyResponse.headers().spliterator(), false)
                .forEach(header -> responseBuilder.addHeader(header.getKey(), header.getValue()));

        return responseBuilder;
    }

    private static LiveHttpResponse toStyxResponse(io.netty.handler.codec.http.HttpResponse nettyResponse, Publisher<Buffer> contentPublisher, Origin origin) {
        try {
            return toStyxResponse(nettyResponse)
                    .body(new ByteStream(contentPublisher))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new BadHttpResponseException(origin, e);
        }
    }

    private static final class BufferSubscriber implements Subscriber<ByteBuf> {

        private final Subscriber<? super Buffer> actual;

        public BufferSubscriber(Subscriber<? super Buffer> actual) {
            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            actual.onSubscribe(s);
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            actual.onNext(Buffers.fromByteBuf(byteBuf));
        }

        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }

    private static final class ContentPublisher implements Publisher<Buffer> {

        private final EventLoop eventLoop;
        private final FlowControllingHttpContentProducer contentProducer;

        public ContentPublisher(EventLoop eventLoop, FlowControllingHttpContentProducer contentProducer) {
            this.eventLoop = eventLoop;
            this.contentProducer = contentProducer;
        }

        @Override
        public void subscribe(Subscriber<? super Buffer> subscriber) {
            BufferSubscriber bufferSubscriber = new BufferSubscriber(subscriber);
            eventLoop.submit(() -> contentProducer.onSubscribed(bufferSubscriber));
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    eventLoop.submit(() -> contentProducer.request(n));
                }

                @Override
                public void cancel() {
                    eventLoop.submit(contentProducer::unsubscribe);
                }
            });
        }
    }
}
