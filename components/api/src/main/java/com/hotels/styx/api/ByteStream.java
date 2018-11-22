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

import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A stream of Styx byte {@link Buffer} objects constituting a HTTP message body.
 *
 * This {@code ByteStream} class implements a reactive streams {@link Publisher} interface,
 * therefore being interoperable with other conforming libraries such as Reactor and
 * Rx Java 2.0.
 *
 * The class provides a set of operations to transform and inspect the byte stream.
 *
 * The class also provides methods for consuming the stream.
 **
 */
public class ByteStream implements Publisher<Buffer> {
    private final Publisher<Buffer> stream;

    /**
     * Create a new {@code ByteStream} from a reactive streams {@link Publisher}.
     *
     * @param stream a reactive streams {@link Publisher}
     */
    public ByteStream(Publisher<Buffer> stream) {
        this.stream = requireNonNull(stream);
    }

    /**
     * Creates a new {@code ByteStream} from String.
     *
     * @param content content
     * @param charset Character set encoding
     * @return ByteStream
     */
    public static ByteStream from(String content, Charset charset) {
        return new ByteStream(Flux.just(new Buffer(content, charset)));
    }

    /**
     * Creates a new {@code ByteStream} from byte array.
     *
     * @param content content
     * @return ByteStream
     */
    public static ByteStream from(byte[] content) {
        return new ByteStream(Flux.just(new Buffer(Unpooled.copiedBuffer(content))));
    }

    /**
     * Transform the stream by performing a mapping operation on each {@link Buffer} object.
     *
     * The mapping operation automatically maintains the @{link Buffer} reference counts as
     * follows:
     *
     * <ul>
     * <li> When the mapping function returns a new {@link Buffer} instance, the reference count for
     * the old one is automatically decremented.</li>
     * <li> When the mapping function modifies the {@link Buffer} in place, returning the same instance
     * back, the reference count is unchanged.</li>
     * </ul>
     *
     * @param mapping a mapping function
     *
     * @return a new, mapped {@code ByteStream} object
     */
    public ByteStream map(Function<Buffer, Buffer> mapping) {
        return new ByteStream(Flux.from(stream).map(releaseOldBuffers(mapping)));
    }

    private static Function<Buffer, Buffer> releaseOldBuffers(Function<Buffer, Buffer> mapping) {
        return buffer -> {
            Buffer buffer2 = mapping.apply(buffer);
            if (buffer != buffer2) {
                buffer.delegate().release();
            }
            return buffer2;
        };
    }

    /**
     * Transform the stream by dropping all {@link Buffer} objects.
     *
     * The {@code drop} returns a new {@code ByteStream} object with all upstream
     * buffers removed. The {@code drop} automatically decrements the reference
     * counts for each dropped {@link Buffer}.
     *
     * @return an empty {@link ByteStream}
     */
    public ByteStream drop() {
        return new ByteStream(Flux.from(stream)
                .doOnNext(buffer -> buffer.delegate().release())
                .filter(buffer -> false));
    }

    /**
     * Run a provided action at the end of the byte stream.
     *
     * The provided action must accept an {@code Optional<Throwable>} argument,
     * which is be set to {@code Optional.empty} if this stream finished successfully,
     * or an {@code Optional.of(cause)} when this stream terminated with an error.
     *
     * @param action an action function
     *
     * @return an unmodified {@code ByteStream} with an action function attached
     */
    public ByteStream doOnEnd(Consumer<Optional<Throwable>> action) {
        return new ByteStream(Flux.from(this.stream)
                .doOnError(cause -> action.accept(Optional.of(cause)))
                .doOnComplete(() -> action.accept(Optional.empty()))
        );
    }

    /**
     * Run a provided action when a consumer cancels this ByteStream.
     *
     * @param action the action runnable
     * @return a new ByteStream with the action attached
     */
    public ByteStream doOnCancel(Runnable action) {
        return new ByteStream(Flux.from(this.stream).doOnCancel(action));
    }

    /**
     * Consumes the stream by collecting it into an aggregate {@link Buffer} object.
     *
     * The aggregate {@link Buffer} object must be released after use.
     *
     * @param maxContentBytes maximum size for the aggregated buffer
     * @return a future of aggregated buffer
     */
    CompletableFuture<Buffer> aggregate(int maxContentBytes) {
        return new ByteStreamAggregator(this.stream, maxContentBytes)
                .apply();
    }

    /**
     * Consume the {@link ByteStream} by providing a reactive streams {@link Subscriber}.
     *
     * @param subscriber a reactive streams {@link Subscriber}
     */
    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        stream.subscribe(subscriber);
    }

    /**
     * Replaces this {@link ByteStream} with a new {@link ByteStream}.
     *
     * Consumes this stream by safely disposing each {@link Buffer} object
     * and then emits {@link Buffer} objects from the provided
     * {@code byteStream}.
     *
     * @param byteStream a replacement byte stream.
     * @return a {@link ByteStream} object.
     */
    public ByteStream replaceWith(ByteStream byteStream) {
        return this.drop().concat(byteStream);
    }

    /**
     * Concatenates two {@link ByteStream}s.
     *
     * Emits all {@link Buffer} objects from this {@link ByteStream} after which
     * continues emissions from the provided {@code byteStream}.
     *
     * @param byteStream an appended byte stream.
     * @return a {@link ByteStream} object.
     */
    ByteStream concat(ByteStream byteStream) {
        return new ByteStream(Flux.from(this.stream).concatWith(byteStream));
    }
}
