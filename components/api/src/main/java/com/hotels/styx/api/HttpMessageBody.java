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
package com.hotels.styx.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.ReferenceCountUtil;
import rx.Observable;
import rx.Subscriber;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.hotels.styx.api.FlowControlDisableOperator.disableFlowControl;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static rx.Observable.empty;

/**
 * Represent the http body of an {@link HttpMessage}.
 */
public final class HttpMessageBody {
    public static final HttpMessageBody NO_BODY = new HttpMessageBody(empty());

    private final Observable<ByteBuf> content;

    /**
     * Construct a message body with content from byte buffers.
     *
     * @param content an observable that provides byte buffers
     */
    public HttpMessageBody(Observable<ByteBuf> content) {
        this.content = checkNotNull(content);
    }

    /**
     * Returns the content observable. This will be empty if a file is being used instead.
     *
     * @return the content observable
     */
    public Observable<ByteBuf> content() {
        return content;
    }

    /**
     * Decodes HTTP message body content to a business domain object.
     *
     * Aggregates streaming HTTP content to a single buffer. A given decoder function is then applied on
     * the aggregated buffer, which is finally released.
     *
     * @param decoder         A function that decodes HTTP content ByteBuf into a business domain object.
     *
     * @param maxContentBytes maximum content size that should be allowed. Exceeding this will cause an exception to be
     *                        thrown
     *
     * @param <T>             Type of the business domain object.
     *
     * @return an aggregate of the content
     */
    public <T> Observable<T> decode(Function<ByteBuf, T> decoder, int maxContentBytes) {
        CompositeByteBuf byteBufs = compositeBuffer();

        return content
                .lift(disableFlowControl())
                .doOnError(e -> byteBufs.release())
                .collect(() -> byteBufs, (composite, part) -> {
                    long newSize = composite.readableBytes() + part.readableBytes();

                    if (newSize > maxContentBytes) {
                        ReferenceCountUtil.release(composite);
                        ReferenceCountUtil.release(part);

                        throw new ContentOverflowException(format("Maximum content size exceeded. Maximum size allowed is %d bytes.", maxContentBytes));
                    }

                    composite.addComponent(part);
                    composite.writerIndex(composite.writerIndex() + part.readableBytes());
                })
                .map((CompositeByteBuf aggregate) -> decodeAndRelease(decoder, aggregate));
    }

    private static <T> T decodeAndRelease(Function<ByteBuf, T> decoder, CompositeByteBuf aggregate) {
        try {
            return decoder.apply(aggregate);
        } finally {
            aggregate.release();
        }
    }

    /**
     * Aggregates HTTP message body content.
     *
     * Aggregates streaming HTTP content to a single continuous byte buffer.
     *
     * Aggregated content is is a reference counted ByteBuf object. It must be released after use.
     *
     * @param maxContentBytes maximum content size that should be allowed. Exceeding this will cause an exception to be
     *                        thrown
     *
     * @return an aggregate of the content
     */

    public Observable<ByteBuf> aggregate(int maxContentBytes) {
        CompositeByteBuf byteBufs = compositeBuffer();

        return content
                .lift(disableFlowControl())
                .doOnError(e -> byteBufs.release())
                .collect(() -> byteBufs, (composite, part) -> {
                    long newSize = composite.readableBytes() + part.readableBytes();

                    if (newSize > maxContentBytes) {
                        ReferenceCountUtil.release(composite);
                        ReferenceCountUtil.release(part);

                        throw new ContentOverflowException(format("Maximum content size exceeded. Maximum size allowed is %d bytes.", maxContentBytes));
                    }

                    composite.addComponent(part);
                    composite.writerIndex(composite.writerIndex() + part.readableBytes());
                })
                .map(composite -> composite);
    }

    /**
     * Releases the buffers containing the body content. This is a non-blocking method.
     *
     * @return future to indicate when this is finished, or to allow synchronisation for testing
     */
    public CompletableFuture<Boolean> releaseContentBuffers() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        content.subscribe(new Subscriber<ByteBuf>() {
            @Override
            public void onCompleted() {
                future.complete(true);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(ByteBuf byteBuf) {
                byteBuf.release();
            }
        });

        return future;
    }

    public static Function<ByteBuf, String> utf8String() {
        // CHECKSTYLE:OFF
        return bytes -> bytes.toString(UTF_8);
        // CHECKSTYLE:ON
    }
}
