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
package com.hotels.styx.server.netty.codec;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.UniqueIdSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.ReferenceCountUtil;
import rx.Observable;
import rx.Producer;
import rx.Subscriber;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.HttpHeaderNames.EXPECT;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.api.StyxInternalObservables.fromRxObservable;
import static com.hotels.styx.api.Url.Builder.url;
import static com.hotels.styx.server.UniqueIdSuppliers.UUID_VERSION_ONE_SUPPLIER;
import static com.hotels.styx.server.netty.codec.UnwiseCharsEncoder.IGNORE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * This {@link MessageToMessageDecoder} is responsible for decode {@link io.netty.handler.codec.http.HttpRequest}
 * to {@link com.hotels.styx.api.HttpRequest}'s.
 * <p/>
 * This implementation is {@link Sharable}.
 */
public final class NettyToStyxRequestDecoder extends MessageToMessageDecoder<HttpObject> {
    private final UniqueIdSupplier uniqueIdSupplier;
    private final boolean flowControlEnabled;
    private final UnwiseCharsEncoder unwiseCharEncoder;
    private final boolean secure;
    private FlowControllingHttpContentProducer producer;

    private NettyToStyxRequestDecoder(Builder builder) {
        this.uniqueIdSupplier = builder.uniqueIdSupplier;
        this.flowControlEnabled = builder.flowControlEnabled;
        this.unwiseCharEncoder = builder.unwiseCharEncoder;
        this.secure = builder.secure;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject httpObject, List<Object> out) throws Exception {
        if (httpObject.getDecoderResult().isFailure()) {
            throw new BadRequestException("Error while decoding request: " + httpObject, httpObject.getDecoderResult().cause());
        }

        try {
            if (httpObject instanceof HttpRequest) {
                this.producer = new FlowControllingHttpContentProducer(ctx, this.flowControlEnabled);
                Observable<ByteBuf> contentObservable = Observable.create(contentSubscriber -> {
                    contentSubscriber.setProducer(this.producer);
                    this.producer.subscriptionStart(contentSubscriber);
                });

                HttpRequest request = (HttpRequest) httpObject;
                com.hotels.styx.api.HttpRequest styxRequest = toStyxRequest(ctx, request, contentObservable);
                out.add(styxRequest);
            } else if (httpObject instanceof HttpContent && this.producer != null) {
                this.producer.onNext(content(httpObject));

                if (httpObject instanceof LastHttpContent) {
                    this.producer.onCompleted();
                }
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException(ex.getMessage() + " in " + httpObject, ex);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (this.producer != null) {
            this.producer.notifySubscriber();
        }
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanUp();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cleanUp();
        if (cause instanceof TooLongFrameException) {
            throw new BadRequestException(cause.getMessage(), cause);
        }
        super.exceptionCaught(ctx, cause);
    }

    private void cleanUp() {
        if (producer != null) {
            producer.cleanUp();
        }
    }

    private com.hotels.styx.api.HttpRequest toStyxRequest(ChannelHandlerContext ctx, HttpRequest request, Observable<ByteBuf> contentObservable) {
        validateHostHeader(request);
        return makeAStyxRequestFrom(request, contentObservable)
                .clientAddress(remoteAddress(ctx))
                .removeHeader(EXPECT)
                .build();
    }

    private static void validateHostHeader(HttpRequest request) {
        Iterable<String> hosts = request.headers().getAll(HOST);
        if (size(hosts) != 1 || !isValidHostName(getOnlyElement(hosts))) {
            throw new BadRequestException("Bad Host header. Missing/Mismatch of Host header: " + request);
        }
    }

    private static boolean isValidHostName(String host) {
        try {
            new URL("http://" + host);
        } catch (MalformedURLException e) {
            return false;
        }
        return true;
    }

    private static ByteBuf content(HttpObject httpObject) {
        return ((ByteBufHolder) httpObject).content().retain();
    }


    @VisibleForTesting
    com.hotels.styx.api.HttpRequest.Builder makeAStyxRequestFrom(HttpRequest request, Observable<ByteBuf> content) {
        Url url = url(unwiseCharEncoder.encode(request.getUri()))
                .scheme(secure ? "https" : "http")
                .build();
        com.hotels.styx.api.HttpRequest.Builder requestBuilder = new com.hotels.styx.api.HttpRequest.Builder()
                .method(toStyxMethod(request.method()))
                .url(url)
                .version(toStyxVersion(request.protocolVersion()))
                .id(uniqueIdSupplier.get())
                .body(fromRxObservable(content));

        stream(request.headers().spliterator(), false)
                .forEach(entry -> requestBuilder.addHeader(entry.getKey(), entry.getValue()));

        return requestBuilder;
    }

    private HttpVersion toStyxVersion(io.netty.handler.codec.http.HttpVersion httpVersion) {
        return HttpVersion.httpVersion(httpVersion.toString());
    }

    private com.hotels.styx.api.HttpMethod toStyxMethod(HttpMethod method) {
        return com.hotels.styx.api.HttpMethod.httpMethod(method.name());
    }

    private static Set<Cookie> decodeCookieHeader(String header, HttpRequest request) {
        try {
            return ServerCookieDecoder.LAX.decode(header);
        } catch (Exception e) {
            throw new MalformedCookieHeaderException(e.getMessage() + " in " + request, e);
        }
    }

    private static InetSocketAddress remoteAddress(ChannelHandlerContext ctx) {
        if (ctx.channel() instanceof EmbeddedChannel) {
            return new InetSocketAddress(0);
        }

        return (InetSocketAddress) ctx.channel().remoteAddress();
    }

    private static class FlowControllingHttpContentProducer implements Producer {
        private final ChannelHandlerContext ctx;

        private final Queue<ByteBuf> readQueue = new ArrayDeque<>();
        private boolean completed;
        private Subscriber<? super ByteBuf> contentSubscriber;

        FlowControllingHttpContentProducer(ChannelHandlerContext ctx, boolean flowControlEnabled) {
            this.ctx = ctx;

            if (flowControlEnabled) {
                this.ctx.channel().config().setAutoRead(false);
            }
        }

        @Override
        public void request(long n) {
            this.ctx.channel().read();
        }

        void onNext(ByteBuf content) {
            synchronized (this.readQueue) {
                this.readQueue.add(content);
            }
        }

        void onCompleted() {
            this.completed = true;
            this.ctx.channel().config().setAutoRead(true);
        }

        void notifySubscriber() {
            if (this.contentSubscriber != null) {
                synchronized (this.readQueue) {
                    ByteBuf value;
                    while ((value = this.readQueue.poll()) != null) {
                        this.contentSubscriber.onNext(value);
                    }
                }
                if (this.completed) {
                    this.contentSubscriber.onCompleted();
                }
            }
        }

        void subscriptionStart(Subscriber<? super ByteBuf> subscriber) {
            this.contentSubscriber = subscriber;
            notifySubscriber();
        }

        void cleanUp() {
            synchronized (this.readQueue) {
                ByteBuf value;
                while ((value = this.readQueue.poll()) != null) {
                    ReferenceCountUtil.release(value);
                }
            }
        }
    }

    /**
     * Builder.
     */
    public static final class Builder {
        private boolean secure;
        private boolean flowControlEnabled;
        private UniqueIdSupplier uniqueIdSupplier = UUID_VERSION_ONE_SUPPLIER;
        private UnwiseCharsEncoder unwiseCharEncoder = IGNORE;

        public Builder uniqueIdSupplier(UniqueIdSupplier uniqueIdSupplier) {
            this.uniqueIdSupplier = requireNonNull(uniqueIdSupplier);
            return this;
        }

        public Builder flowControlEnabled(boolean flowControlEnabled) {
            this.flowControlEnabled = flowControlEnabled;
            return this;
        }

        public Builder unwiseCharEncoder(UnwiseCharsEncoder unwiseCharEncoder) {
            this.unwiseCharEncoder = requireNonNull(unwiseCharEncoder);
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public NettyToStyxRequestDecoder build() {
            return new NettyToStyxRequestDecoder(this);
        }
    }
}
