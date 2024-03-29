/*
  Copyright (C) 2013-2023 Expedia Inc.

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
import com.hotels.styx.api.Buffers;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.HttpMethod;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.Url;
import com.hotels.styx.common.format.DefaultHttpMessageFormatter;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.server.BadRequestException;
import com.hotels.styx.server.UniqueIdSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.HttpHeaderNames.EXPECT;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.server.UniqueIdSuppliers.UUID_VERSION_ONE_SUPPLIER;
import static com.hotels.styx.server.netty.codec.UnwiseCharsEncoder.IGNORE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * This {@link MessageToMessageDecoder} is responsible for decode {@link io.netty.handler.codec.http.HttpRequest}
 * to {@link LiveHttpRequest}'s.
 * <p/>
 * This implementation is {@link Sharable}.
 */
public final class NettyToStyxRequestDecoder extends MessageToMessageDecoder<HttpObject> {
    private final UniqueIdSupplier uniqueIdSupplier;
    private final boolean flowControlEnabled;
    private final UnwiseCharsEncoder unwiseCharEncoder;
    private final HttpMessageFormatter httpMessageFormatter;
    private FlowControllingHttpContentBuffer contentBuffer;

    private NettyToStyxRequestDecoder(Builder builder) {
        this.uniqueIdSupplier = builder.uniqueIdSupplier;
        this.flowControlEnabled = builder.flowControlEnabled;
        this.unwiseCharEncoder = builder.unwiseCharEncoder;
        this.httpMessageFormatter = builder.httpMessageFormatter;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject httpObject, List<Object> out) {
        if (httpObject.decoderResult().isFailure()) {
            String formattedHttpObject = httpMessageFormatter.formatNettyMessage(httpObject);
            throw new BadRequestException("Error while decoding request: " + formattedHttpObject,
                httpMessageFormatter.wrap(httpObject.decoderResult().cause()));
        }

        try {
            if (httpObject instanceof HttpRequest) {
                contentBuffer = new FlowControllingHttpContentBuffer();
                if (flowControlEnabled) {
                    ctx.channel().config().setAutoRead(false);
                }
                Flux<ByteBuf> contentPublisher = Flux.create(contentBuffer::subscriptionStart)
                    .doOnRequest(i -> ctx.channel().read())
                    .doFinally(e -> ctx.channel().config().setAutoRead(true));

                HttpRequest request = (HttpRequest) httpObject;
                LiveHttpRequest styxRequest = toStyxRequest(request, contentPublisher);
                out.add(styxRequest);
            } else if (httpObject instanceof HttpContent && contentBuffer != null) {
                contentBuffer.addChunk(content(httpObject));

                if (httpObject instanceof LastHttpContent) {
                    contentBuffer.completed();
                    ctx.channel().config().setAutoRead(true);
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
        if (contentBuffer != null) {
            contentBuffer.notifySubscriber();
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
        if (contentBuffer != null) {
            contentBuffer.cleanUp();
        }
    }

    private LiveHttpRequest toStyxRequest(HttpRequest request, Flux<ByteBuf> contentPublisher) {
        validateHostHeader(request);
        return makeAStyxRequestFrom(request, contentPublisher)
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
    LiveHttpRequest.Builder makeAStyxRequestFrom(HttpRequest request, Flux<ByteBuf> content) {
        Url url = UrlDecoder.decodeUrl(unwiseCharEncoder, request);
        LiveHttpRequest.Builder requestBuilder = new LiveHttpRequest.Builder()
            .method(toStyxMethod(request.method()))
            .url(url)
            .version(toStyxVersion(request.protocolVersion()))
            .id(uniqueIdSupplier.get())
            .body(new ByteStream(content.map(Buffers::fromByteBuf)));

        stream(request.headers().spliterator(), false)
            .forEach(entry -> requestBuilder.addHeader(entry.getKey(), entry.getValue()));

        return requestBuilder;
    }

    private HttpVersion toStyxVersion(io.netty.handler.codec.http.HttpVersion httpVersion) {
        return HttpVersion.httpVersion(httpVersion.toString());
    }

    private HttpMethod toStyxMethod(io.netty.handler.codec.http.HttpMethod method) {
        return HttpMethod.httpMethod(method.name());
    }

    private static class FlowControllingHttpContentBuffer {
        private final Queue<ByteBuf> readQueue = new ArrayDeque<>();
        private volatile boolean completed;
        private volatile FluxSink<ByteBuf> sink;

        void addChunk(ByteBuf content) {
            synchronized (this.readQueue) {
                this.readQueue.add(content);
            }
        }

        void completed() {
            this.completed = true;
        }

        void notifySubscriber() {
            if (this.sink != null) {
                synchronized (this.readQueue) {
                    ByteBuf value;
                    while ((value = this.readQueue.poll()) != null) {
                        this.sink.next(value);
                    }
                }
                if (this.completed) {
                    this.sink.complete();
                }
            }
        }

        void subscriptionStart(FluxSink<ByteBuf> subscriber) {
            this.sink = subscriber;
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
        private boolean flowControlEnabled;
        private UniqueIdSupplier uniqueIdSupplier = UUID_VERSION_ONE_SUPPLIER;
        private UnwiseCharsEncoder unwiseCharEncoder = IGNORE;
        private HttpMessageFormatter httpMessageFormatter = new DefaultHttpMessageFormatter();

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

        public Builder httpMessageFormatter(HttpMessageFormatter httpMessageFormatter) {
            this.httpMessageFormatter = httpMessageFormatter;
            return this;
        }

        public NettyToStyxRequestDecoder build() {
            return new NettyToStyxRequestDecoder(this);
        }
    }
}
