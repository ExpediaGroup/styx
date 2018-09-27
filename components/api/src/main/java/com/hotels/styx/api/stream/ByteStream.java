package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ContentEventInternal;
import com.hotels.styx.api.ContentStream;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class ByteStream {
    private Publisher<Buffer> stream;

    public ByteStream(Publisher<Buffer> stream) {
        this.stream = requireNonNull(stream);
    }

    public ByteStream map(Function<Buffer, Buffer> mapping) {
        return new ByteStream(new MappingOperator(this.stream, mapping));
    }

    public ByteStream discard() {
        // Todo: modify the DiscardOperator to make it similar to MappingOperator:
        return new ByteStream(new DiscardOperator(this.stream).apply());
    }


    // TODO: This could return a ByteStream of aggregated content
    public CompletableFuture<Buffer> aggregate(int maxContentBytes) {
        return new AggregateOperator(this.stream, maxContentBytes)
                .apply();
    }

    private static ContentEventInternal toContentEvent(ByteBuf buf) {
        return new ContentEventInternal(new Buffer(buf));
    }

    private static ContentStream.EndOfStreamEvent toEndOfStreamEvent() {
        return new ContentStream.EndOfStreamEvent() {
        };
    }

    private static ContentStream.ErrorEvent toErrorEvent(Throwable cause) {
        return () -> cause;
    }

    public Publisher<Buffer> publisher() {
        return stream;
    }

}
