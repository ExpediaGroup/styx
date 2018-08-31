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
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.client.Operation;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.common.logging.HttpRequestMessageLogger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import rx.Observable;
import rx.Subscriber;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.StyxInternalObservables.toRxObservable;
import static com.hotels.styx.api.extension.service.BackendService.DEFAULT_RESPONSE_TIMEOUT_MILLIS;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * An operation that writes an HTTP request to an origin.
 */
public class HttpRequestOperation implements Operation<NettyConnection, HttpResponse> {
    private static final String IDLE_HANDLER_NAME = "idle-handler";
    private static final Logger LOGGER = getLogger(HttpRequestOperation.class);

    private final HttpRequest request;
    private final Optional<OriginStatsFactory> originStatsFactory;
    private final boolean flowControlEnabled;
    private final int responseTimeoutMillis;
    private final AtomicInteger terminationCount = new AtomicInteger(0);
    private final AtomicInteger executeCount = new AtomicInteger(0);
    private final boolean requestLoggingEnabled;
    private volatile long requestTime;
    private final HttpRequestMessageLogger httpRequestMessageLogger;

    /**
     * Constructs an instance with flow-control disabled and a default response time (1s).
     * @param request            HTTP request
     * @param originStatsFactory OriginStats factory
     */
    @VisibleForTesting
    public HttpRequestOperation(HttpRequest request, OriginStatsFactory originStatsFactory) {
        this(request, originStatsFactory, false, DEFAULT_RESPONSE_TIMEOUT_MILLIS, false, false);
    }

    /**
     * Constructs an instance.
     *
     * @param request               HTTP request
     * @param originStatsFactory    OriginStats factory
     * @param flowControlEnabled    true if flow-control should be enabled
     * @param responseTimeoutMillis response timeout in milliseconds
     * @param requestLoggingEnabled
     */
    public HttpRequestOperation(HttpRequest request, OriginStatsFactory originStatsFactory, boolean flowControlEnabled,
                                int responseTimeoutMillis, boolean requestLoggingEnabled, boolean longFormat) {
        this.request = requireNonNull(request);
        this.originStatsFactory = Optional.ofNullable(originStatsFactory);
        this.flowControlEnabled = flowControlEnabled;
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.requestLoggingEnabled = requestLoggingEnabled;
        this.httpRequestMessageLogger = new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", longFormat);
    }

    @VisibleForTesting
    static DefaultHttpRequest toNettyRequest(HttpRequest request) {
        HttpVersion version = request.version();
        HttpMethod method = request.method();
        String url = request.url().toString();
        DefaultHttpRequest nettyRequest = new DefaultHttpRequest(toNettyVersion(version), toNettyMethod(method), url, false);

        request.headers().forEach((name, value) ->
                nettyRequest.headers().add(name, value));

        return nettyRequest;
    }

    private static io.netty.handler.codec.http.HttpMethod toNettyMethod(HttpMethod method) {
        return io.netty.handler.codec.http.HttpMethod.valueOf(method.name());
    }

    private static io.netty.handler.codec.http.HttpVersion toNettyVersion(HttpVersion version) {
        return HttpVersion.HTTP_1_0.equals(version)
                ? io.netty.handler.codec.http.HttpVersion.HTTP_1_0
                : io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
    }

    private static boolean requestIsOngoing(RequestBodyChunkSubscriber bodyChunkSubscriber) {
        return bodyChunkSubscriber != null && bodyChunkSubscriber.requestIsOngoing();
    }

    @Override
    public Observable<HttpResponse> execute(NettyConnection nettyConnection) {
        AtomicReference<RequestBodyChunkSubscriber> requestRequestBodyChunkSubscriber = new AtomicReference<>();
        requestTime = System.currentTimeMillis();
        executeCount.incrementAndGet();

        Observable<HttpResponse> observable = Observable.create(subscriber -> {
            if (nettyConnection.isConnected()) {
                RequestBodyChunkSubscriber bodyChunkSubscriber = new RequestBodyChunkSubscriber(nettyConnection);
                requestRequestBodyChunkSubscriber.set(bodyChunkSubscriber);
                addProxyBridgeHandlers(nettyConnection, subscriber);
                new WriteRequestToOrigin(subscriber, nettyConnection, request, bodyChunkSubscriber)
                        .write();
                if (requestLoggingEnabled) {
                    httpRequestMessageLogger.logRequest(request, nettyConnection.getOrigin());
                }
            }
        });

        if (requestLoggingEnabled) {
            observable = observable
                    .doOnNext(response -> {
                        httpRequestMessageLogger.logResponse(request, response);
                    });
        }

        return observable.doOnTerminate(() -> {
            if (nettyConnection.isConnected()) {
                removeProxyBridgeHandlers(nettyConnection);
                if (requestIsOngoing(requestRequestBodyChunkSubscriber.get())) {
                    LOGGER.warn("Origin responded too quickly to an ongoing request, or it was cancelled. Connection={}, Request={}.",
                            new Object[]{nettyConnection.channel(), this.request});
                    nettyConnection.close();
                }
            }
        }).map(response -> response.newBuilder().build());
    }

    private void addProxyBridgeHandlers(NettyConnection nettyConnection, Subscriber<? super HttpResponse> observer) {
        Origin origin = nettyConnection.getOrigin();
        Channel channel = nettyConnection.channel();
        channel.pipeline().addLast(IDLE_HANDLER_NAME, new IdleStateHandler(0, 0, responseTimeoutMillis, MILLISECONDS));
        originStatsFactory.ifPresent(
                originStatsFactory -> channel.pipeline()
                        .addLast(RequestsToOriginMetricsCollector.NAME,
                                new RequestsToOriginMetricsCollector(originStatsFactory.originStats(origin))));
        channel.pipeline().addLast(
                NettyToStyxResponsePropagator.NAME,
                new NettyToStyxResponsePropagator(observer, origin, flowControlEnabled, responseTimeoutMillis, MILLISECONDS, request));
    }

    private void removeProxyBridgeHandlers(NettyConnection connection) {
        ChannelPipeline pipeline = connection.channel().pipeline();
        terminationCount.incrementAndGet();

        try {
            pipeline.remove(IDLE_HANDLER_NAME);
            if (originStatsFactory.isPresent()) {
                pipeline.remove(RequestsToOriginMetricsCollector.NAME);
            }
            pipeline.remove(NettyToStyxResponsePropagator.NAME);
        } catch (NoSuchElementException cause) {
            long elapsedTime = System.currentTimeMillis() - requestTime;
            LOGGER.error("Failed to remove pipeline handlers from pooled connection. elapsedTime={}, request={}, terminationCount={}, executionCount={}, cause={}",
                    new Object[]{elapsedTime, request, terminationCount.get(), executeCount.get(), cause});
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(request);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        HttpRequestOperation other = (HttpRequestOperation) obj;
        return Objects.equals(this.request, other.request);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("httpRequest", this.request)
                .toString();
    }

    private static final class WriteRequestToOrigin {
        private final Subscriber<? super HttpResponse> responseFromOriginObserver;
        private final ReadOrCloseChannelListener readOrCloseChannelListener;
        private final NettyConnection nettyConnection;
        private final HttpRequest request;
        private final RequestBodyChunkSubscriber requestBodyChunkSubscriber;

        private WriteRequestToOrigin(Subscriber<? super HttpResponse> responseFromOriginObserver, NettyConnection nettyConnection, HttpRequest request,
                                     RequestBodyChunkSubscriber requestBodyChunkSubscriber) {
            this.responseFromOriginObserver = responseFromOriginObserver;
            this.nettyConnection = nettyConnection;
            this.request = request;
            this.requestBodyChunkSubscriber = requestBodyChunkSubscriber;
            this.readOrCloseChannelListener = new ReadOrCloseChannelListener(nettyConnection, responseFromOriginObserver);
        }

        public void write() {
            Channel originChannel = this.nettyConnection.channel();
            if (originChannel.isActive()) {
                io.netty.handler.codec.http.HttpRequest msg = makeRequest(request);
                originChannel.writeAndFlush(msg)
                        .addListener(readOrCloseChannelListener)
                        .addListener(subscribeToResponseBody());
            } else {
                responseFromOriginObserver.onError(new TransportLostException(originChannel.remoteAddress(), nettyConnection.getOrigin()));
            }
        }

        private ChannelFutureListener subscribeToResponseBody() {
            return future -> {
                if (future.isSuccess()) {
                    toRxObservable(request.body()).subscribe(requestBodyChunkSubscriber);
                } else {
                    LOGGER.error(format("error writing body to origin=%s request=%s", nettyConnection.getOrigin(), request), future.cause());
                    responseFromOriginObserver.onError(new TransportLostException(nettyConnection.channel().remoteAddress(), nettyConnection.getOrigin()));
                }
            };
        }

        private io.netty.handler.codec.http.HttpRequest makeRequest(HttpRequest request) {
            DefaultHttpRequest nettyRequest = toNettyRequest(request);
            Optional<String> host = request.header(HOST);
            if (!host.isPresent()) {
                nettyRequest.headers().set(HOST, nettyConnection.getOrigin().hostAsString());
            }
            return nettyRequest;
        }
    }

    private static class ReadOrCloseChannelListener implements ChannelFutureListener {
        private final NettyConnection nettyConnection;
        private final Subscriber subscriber;

        public ReadOrCloseChannelListener(NettyConnection nettyConnection, Subscriber subscriber) {
            this.nettyConnection = nettyConnection;
            this.subscriber = subscriber;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                future.channel().read();
            } else {
                subscriber.onError(new TransportLostException(nettyConnection.channel().remoteAddress(), nettyConnection.getOrigin()));
            }
        }
    }

    private static final class RequestBodyChunkSubscriber extends Subscriber<ByteBuf> {
        private final Channel channel;
        private final ChannelFutureListener readNext;
        private final ChannelFutureListener readOrClose;
        private volatile boolean completed;

        private RequestBodyChunkSubscriber(NettyConnection nettyConnection) {
            this.channel = nettyConnection.channel();
            this.readOrClose = new ReadOrCloseChannelListener(nettyConnection, this);
            this.readNext = future -> request(1);
        }

        @Override
        public void onStart() {
            super.onStart();
        }

        @Override
        public void onCompleted() {
            completed = true;
            channel.writeAndFlush(EMPTY_LAST_CONTENT);
        }

        @Override
        public void onError(Throwable e) {
            completed = true;
        }

        @Override
        public void onNext(ByteBuf chunk) {
            HttpObject msg = new DefaultHttpContent(chunk);
            channel.writeAndFlush(msg)
                    .addListener(readNext)
                    .addListener(readOrClose);
        }

        public boolean requestIsOngoing() {
            return !completed;
        }
    }
}
