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

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.exceptions.TransportException;
import com.hotels.styx.common.content.FlowControllerTimer;
import com.hotels.styx.common.content.FlowControllingHttpContentProducer;
import com.hotels.styx.common.content.QueueDrainingPublisher;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import org.reactivestreams.Publisher;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.size;
import static com.hotels.styx.api.HttpHeaderNames.EXPECT;
import static com.hotels.styx.api.HttpHeaderNames.HOST;
import static com.hotels.styx.server.UniqueIdSuppliers.UUID_VERSION_ONE_SUPPLIER;
import static com.hotels.styx.server.netty.codec.UnwiseCharsEncoder.IGNORE;
import static io.netty.util.ReferenceCountUtil.retain;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * This {@link MessageToMessageDecoder} is responsible for decode {@link io.netty.handler.codec.http.HttpRequest}
 * to {@link LiveHttpRequest}'s.
 * <p/>
 * This implementation is {@link Sharable}.
 */
public final class NettyToStyxRequestDecoder extends MessageToMessageDecoder<HttpObject> {

    private static final long DEFAULT_INACTIVITY_TIMEOUT_MS = 60000L;
    private final UniqueIdSupplier uniqueIdSupplier;
    private final UnwiseCharsEncoder unwiseCharEncoder;
    private HttpMessageFormatter httpMessageFormatter;

    private final long inactivityTimeoutMs;
    private Optional<FlowControllingHttpContentProducer> producer = Optional.empty();

    private NettyToStyxRequestDecoder(Builder builder) {
        this.uniqueIdSupplier = builder.uniqueIdSupplier;
        this.unwiseCharEncoder = builder.unwiseCharEncoder;
        this.httpMessageFormatter = builder.httpMessageFormatter;
        this.inactivityTimeoutMs = builder.inactivityTimeoutMs;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg.getDecoderResult().isFailure()) {
            String formattedHttpObject = httpMessageFormatter.formatNettyMessage(msg);
            throw new BadRequestException("Error while decoding request: " + formattedHttpObject,
                    httpMessageFormatter.wrap(msg.getDecoderResult().cause()));
        }

        try {
            if (msg instanceof HttpRequest) {
                ctx.channel().config().setAutoRead(false);
                ctx.channel().read();

                HttpRequest nettyRequest = (HttpRequest) msg;

                this.producer = Optional.of(createProducer(ctx, nettyRequest.uri()));
                Publisher<Buffer> contentPublisher = new QueueDrainingPublisher(this.producer.get());

                LiveHttpRequest styxRequest = toStyxRequest(nettyRequest, contentPublisher);
                out.add(styxRequest);

            }
            if (msg instanceof HttpContent) {
                assert this.producer.isPresent();

                ByteBuf content = ((ByteBufHolder) msg).content();
                if (content.isReadable()) {
                    this.producer.ifPresent(it -> it.newChunk(retain(content)));
                }
                if (msg instanceof LastHttpContent) {
                    this.producer.ifPresent(FlowControllingHttpContentProducer::lastHttpContent);
                }
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException(ex.getMessage() + " in " + msg, ex);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        TransportException cause = new TransportException(
                "Connection to client lost: " + ctx.channel().remoteAddress());
        this.producer.ifPresent(it -> it.channelInactive(cause));
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof TooLongFrameException) {
            this.producer.ifPresent(it -> it.channelException(new BadRequestException(cause.getMessage(), cause)));
        } else {
            this.producer.ifPresent(it -> it.channelException(cause));
        }
        super.exceptionCaught(ctx, cause);
    }

    private FlowControllingHttpContentProducer createProducer(ChannelHandlerContext ctx, String uri) {
        String loggingPrefix = format("Request body. %s [remote: %s, local: %s]", uri, ctx.channel().remoteAddress(), ctx.channel().localAddress());

        FlowControllingHttpContentProducer producer = new FlowControllingHttpContentProducer(
                () -> ctx.channel().read(),
                () -> ctx.channel().config().setAutoRead(true),
                cause -> { },
                format("%s, %s", loggingPrefix, ""),
                null);
        new FlowControllerTimer(inactivityTimeoutMs, ctx.channel().eventLoop(), producer).checkActivity();
        return producer;
    }

    private LiveHttpRequest toStyxRequest(HttpRequest request, Publisher<Buffer> contentPublisher) {
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

    @VisibleForTesting
    LiveHttpRequest.Builder makeAStyxRequestFrom(HttpRequest request, Publisher<Buffer> content) {
        Url url = UrlDecoder.decodeUrl(unwiseCharEncoder, request);
        LiveHttpRequest.Builder requestBuilder = new LiveHttpRequest.Builder()
                .method(toStyxMethod(request.method()))
                .url(url)
                .version(toStyxVersion(request.protocolVersion()))
                .id(uniqueIdSupplier.get())
                .body(new ByteStream(content));

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

    /**
     * Builder.
     */
    public static final class Builder {
        private UniqueIdSupplier uniqueIdSupplier = UUID_VERSION_ONE_SUPPLIER;
        private UnwiseCharsEncoder unwiseCharEncoder = IGNORE;
        private HttpMessageFormatter httpMessageFormatter = new DefaultHttpMessageFormatter();
        private long inactivityTimeoutMs = DEFAULT_INACTIVITY_TIMEOUT_MS;

        public Builder uniqueIdSupplier(UniqueIdSupplier uniqueIdSupplier) {
            this.uniqueIdSupplier = requireNonNull(uniqueIdSupplier);
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

        public Builder inactivityTimeoutMs(long inactivityTimeoutMs) {
            this.inactivityTimeoutMs = inactivityTimeoutMs;
            return this;
        }

        public NettyToStyxRequestDecoder build() {
            return new NettyToStyxRequestDecoder(this);
        }
    }
}
