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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Buffers;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Requests;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.common.logging.HttpRequestMessageLogger;
import com.hotels.styx.debug.RequestDebugger;
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
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * An operation that writes an HTTP request to an origin.
 */
public class HttpRequestOperation {
    private static final String IDLE_HANDLER_NAME = "idle-handler";
    private static final Logger LOGGER = getLogger(HttpRequestOperation.class);

    private final LiveHttpRequest request;
    private final Optional<OriginStatsFactory> originStatsFactory;
    private final int responseTimeoutMillis;
    private final AtomicInteger terminationCount = new AtomicInteger(0);
    private final AtomicInteger executeCount = new AtomicInteger(0);
    private final boolean requestLoggingEnabled;
    private volatile long requestTime;
    private final HttpRequestMessageLogger httpRequestMessageLogger;

    /**
     * Constructs an instance.
     *  @param request               HTTP request
     * @param originStatsFactory    OriginStats factory
     * @param responseTimeoutMillis response timeout in milliseconds
     * @param requestLoggingEnabled
     */
    public HttpRequestOperation(LiveHttpRequest request, OriginStatsFactory originStatsFactory,
                                int responseTimeoutMillis, boolean requestLoggingEnabled, boolean longFormat, HttpMessageFormatter httpMessageFormatter) {
        this.request = requireNonNull(request);
        this.originStatsFactory = Optional.ofNullable(originStatsFactory);
        this.responseTimeoutMillis = responseTimeoutMillis;
        this.requestLoggingEnabled = requestLoggingEnabled;
        this.httpRequestMessageLogger = new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", longFormat, httpMessageFormatter);
    }

    @VisibleForTesting
    static DefaultHttpRequest toNettyRequest(LiveHttpRequest request) {
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

    public Flux<LiveHttpResponse> execute(NettyConnection nettyConnection) {
        AtomicReference<RequestBodyChunkSubscriber> requestRequestBodyChunkSubscriber = new AtomicReference<>();
        requestTime = System.currentTimeMillis();
        executeCount.incrementAndGet();

        if (RequestDebugger.shouldDebugRequest(request)) {
            LOGGER.info(">>> Writing to connection: " + nettyConnection);
        }

        Flux<LiveHttpResponse> responseFlux = Flux.create(sink -> {
            if (nettyConnection.isConnected()) {
                RequestBodyChunkSubscriber bodyChunkSubscriber = new RequestBodyChunkSubscriber(request, nettyConnection);
                requestRequestBodyChunkSubscriber.set(bodyChunkSubscriber);
                addProxyBridgeHandlers(nettyConnection, sink);
                new WriteRequestToOrigin(sink, nettyConnection, request, bodyChunkSubscriber)
                        .write();
                if (requestLoggingEnabled) {
                    httpRequestMessageLogger.logRequest(request, nettyConnection.getOrigin());
                }
            }
        });

        if (requestLoggingEnabled) {
            responseFlux = responseFlux
                    .doOnNext(response -> {
                        httpRequestMessageLogger.logResponse(request, response);
                    });
        }
        return responseFlux.map(response ->
                        Requests.doFinally(response, cause -> {
                            if (nettyConnection.isConnected()) {
                                removeProxyBridgeHandlers(nettyConnection);

                                if (requestIsOngoing(requestRequestBodyChunkSubscriber.get())) {
                                    LOGGER.warn("Origin responded too quickly to an ongoing request, or it was cancelled. Connection={}, Request={}.",
                                            new Object[]{nettyConnection.channel(), this.request});
                                    nettyConnection.close();
                                }
                            }
                        }));
    }

    private void addProxyBridgeHandlers(NettyConnection nettyConnection, FluxSink<LiveHttpResponse> sink) {
        Origin origin = nettyConnection.getOrigin();
        Channel channel = nettyConnection.channel();
        channel.pipeline().addLast(IDLE_HANDLER_NAME, new IdleStateHandler(0, 0, responseTimeoutMillis, MILLISECONDS));
        originStatsFactory.ifPresent(
                originStatsFactory -> channel.pipeline()
                        .addLast(RequestsToOriginMetricsCollector.NAME,
                                new RequestsToOriginMetricsCollector(originStatsFactory.originStats(origin))));
        channel.pipeline().addLast(
                NettyToStyxResponsePropagator.NAME,
                new NettyToStyxResponsePropagator(sink, origin, responseTimeoutMillis, MILLISECONDS, request));
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
        private final FluxSink<LiveHttpResponse> responseFromOriginFlux;
        private final NettyConnection nettyConnection;
        private final LiveHttpRequest request;
        private final RequestBodyChunkSubscriber requestBodyChunkSubscriber;

        private WriteRequestToOrigin(FluxSink<LiveHttpResponse> responseFromOriginFlux, NettyConnection nettyConnection, LiveHttpRequest request,
                                     RequestBodyChunkSubscriber requestBodyChunkSubscriber) {
            this.responseFromOriginFlux = responseFromOriginFlux;
            this.nettyConnection = nettyConnection;
            this.request = request;
            this.requestBodyChunkSubscriber = requestBodyChunkSubscriber;
        }

        public void write() {
            Channel originChannel = this.nettyConnection.channel();
            if (originChannel.isActive()) {
                io.netty.handler.codec.http.HttpRequest httpRequest = makeRequest(request);
                originChannel.writeAndFlush(httpRequest)
                    .addListener(subscribeToRequestBody());
            } else {
                responseFromOriginFlux.error(new TransportLostException(originChannel.remoteAddress(), nettyConnection.getOrigin()));
            }
        }

        private ChannelFutureListener subscribeToRequestBody() {
            return headersFuture -> {
                if (headersFuture.isSuccess()) {
                    headersFuture.channel().read();
                    Flux.from(request.body())
                            .map(Buffers::toByteBuf)
                            .subscribe(requestBodyChunkSubscriber);
                } else {
                    String channelIdentifier = String.format("%s -> %s", nettyConnection.channel().localAddress(), nettyConnection.channel().remoteAddress());
                    LOGGER.error(format("Failed to send request headers. origin=%s connection=%s request=%s",
                            nettyConnection.getOrigin(), channelIdentifier, request), headersFuture.cause());
                    responseFromOriginFlux.error(new TransportLostException(nettyConnection.channel().remoteAddress(), nettyConnection.getOrigin()));
                }
            };
        }

        private io.netty.handler.codec.http.HttpRequest makeRequest(LiveHttpRequest request) {
            DefaultHttpRequest nettyRequest = toNettyRequest(request);
            Optional<String> host = request.header(HOST);
            if (!host.isPresent()) {
                nettyRequest.headers().set(HOST, nettyConnection.getOrigin().hostAndPortString());
            }
            return nettyRequest;
        }
    }

    private static final class RequestBodyChunkSubscriber extends BaseSubscriber<ByteBuf> {
        private final NettyConnection nettyConnection;
        private final LiveHttpRequest request;
        private final Channel channel;
        private volatile boolean completed;

        private RequestBodyChunkSubscriber(LiveHttpRequest request, NettyConnection nettyConnection) {
            this.request = request;
            this.channel = nettyConnection.channel();
            this.nettyConnection = nettyConnection;
        }

        @Override
        public void hookOnComplete() {
            channel.writeAndFlush(EMPTY_LAST_CONTENT)
                    .addListener(future -> completed = true);
        }

        @Override
        public void hookOnError(Throwable e) {
            completed = true;
        }

        @Override
        public void hookOnNext(ByteBuf chunk) {
            HttpObject msg = new DefaultHttpContent(chunk);
            channel.writeAndFlush(msg)
                    .addListener((ChannelFuture future) -> {
                        request(1);
                        if (future.isSuccess()) {
                            future.channel().read();
                        } else {
                            String channelIdentifier = String.format("%s -> %s", nettyConnection.channel().localAddress(), nettyConnection.channel().remoteAddress());
                            LOGGER.error(format("Failed to send request body data. origin=%s connection=%s request=%s",
                                    nettyConnection.getOrigin(), channelIdentifier, request), future.cause());
                            this.onError(new TransportLostException(nettyConnection.channel().remoteAddress(), nettyConnection.getOrigin()));
                        }
                    });
        }

        boolean requestIsOngoing() {
            return !completed;
        }
    }
}
