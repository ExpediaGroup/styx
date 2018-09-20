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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hotels.styx.api.FlowControlDisableOperator.disableFlowControl;
import static io.netty.buffer.ByteBufUtil.getBytes;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.util.ReferenceCountUtil.release;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class RxContentStream implements ContentStream {
    private Observable<ByteBuf> stream;

    public RxContentStream() {
        this.stream = Observable.empty();
    }

    public RxContentStream(Observable<ByteBuf> stream) {
        this.stream = requireNonNull(stream);
    }

    public RxContentStream(StyxObservable<ByteBuf> from) {
        this(((StyxCoreObservable<ByteBuf>) from).delegate());
    }

    public Observable<ByteBuf> rxObservable() {
        return this.stream;
    }

    public Observable<byte[]> aggregate(int maxContentBytes) {
        CompositeByteBuf byteBufs = compositeBuffer();

        return stream
                .lift(disableFlowControl())
                .doOnError(e -> byteBufs.release())
                .collect(() -> byteBufs, (composite, part) -> {
                    long newSize = composite.readableBytes() + part.readableBytes();

                    if (newSize > maxContentBytes) {
                        release(composite);
                        release(part);

                        throw new ContentOverflowException(format("Maximum content size exceeded. Maximum size allowed is %d bytes.", maxContentBytes));
                    }
                    composite.addComponent(part);
                    composite.writerIndex(composite.writerIndex() + part.readableBytes());
                })
                .map(RxContentStream::decodeAndRelease);
    }

    private static byte[] decodeAndRelease(CompositeByteBuf aggregate) {
        try {
            return getBytes(aggregate);
        } finally {
            aggregate.release();
        }
    }

    public ContentStream remove() {
        return new RxContentStream(stream
                .doOnNext(ReferenceCountUtil::release)
                .ignoreElements());
    }

    public CompletableFuture<Boolean> discard() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        stream.doOnNext(ReferenceCountUtil::release)
                .ignoreElements()
                .doOnCompleted(() -> future.complete(true))
                .doOnError(future::completeExceptionally)
                .subscribe();

        return future;
    }

    @Override
    public ContentStream map(Function<Buffer, Buffer> transformation) {
        return new RxContentStream(this.stream
                .map(Buffer::new)
                .map(transformation::apply)
                .map(buffer -> buffer.delegate));
    }

    public void consume(Consumer<Event> consumer) {
        stream.subscribe(buf -> consumer.accept(toContentEvent(buf)),
                error -> consumer.accept(toErrorEvent(error)),
                () -> consumer.accept(toEndOfStreamEvent()));
    }

    private static ContentEventInternal toContentEvent(ByteBuf buf) {
        return new ContentEventInternal(new Buffer(buf));
    }

    private static EndOfStreamEvent toEndOfStreamEvent() {
        return new EndOfStreamEvent() {
        };
    }

    private static ErrorEvent toErrorEvent(Throwable cause) {
        return () -> cause;
    }


}
