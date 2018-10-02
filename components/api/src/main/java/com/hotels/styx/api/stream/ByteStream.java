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
package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Byte stream class.
 */
public class ByteStream implements Publisher<Buffer> {
    private final CompletableFuture<Void> endOfStream = new CompletableFuture<>();
    private final Publisher<Buffer> stream;

    public ByteStream(Publisher<Buffer> stream) {
        this.stream = Flux.from(requireNonNull(stream))
                        .doOnError(endOfStream::completeExceptionally)
                        .doOnComplete(() -> endOfStream.complete(null));
    }

    public ByteStream map(Function<Buffer, Buffer> mapping) {
        return new ByteStream(Flux.from(stream).map(refCountWrapper(mapping)));
    }

    private static Function<Buffer, Buffer> refCountWrapper(Function<Buffer, Buffer> mapping) {
        return buffer -> {
            Buffer buffer2 = mapping.apply(buffer);
            if (buffer != buffer2) {
                buffer.delegate().release();
            }
            return buffer2;
        };
    }

    public ByteStream drop() {
        return new ByteStream(Flux.from(stream)
                .doOnNext(buffer -> buffer.delegate().release())
                .filter(buffer -> false));
    }

    public CompletableFuture<Buffer> aggregate(int maxContentBytes) {
        return new AggregateOperator(this.stream, maxContentBytes)
                .apply();
    }

    public CompletableFuture<Void> endOfStream() {
        return endOfStream;
    }

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        stream.subscribe(subscriber);
    }
}
