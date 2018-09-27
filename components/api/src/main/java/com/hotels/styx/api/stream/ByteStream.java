package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ContentEventInternal;
import com.hotels.styx.api.ContentStream;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

import static io.netty.buffer.ByteBufUtil.getBytes;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.util.ReferenceCountUtil.release;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ByteStream {
    private Publisher<Buffer> stream;

    public ByteStream(Publisher<Buffer> stream) {
        this.stream = requireNonNull(stream);
    }

    // TODO: This could return a ByteStream of aggregated content
    public CompletableFuture<Buffer> aggregate(int maxContentBytes) {
        return new AggregateOperator(this.stream, maxContentBytes)
                .apply();
    }

    // TODO: What does it mean to "remove" a ByteStream?
//    public ContentStream remove() {
//        return new ByteStream(stream
//                .doOnNext(ReferenceCountUtil::release)
//                .ignoreElements());
//    }


    public CompletableFuture<Boolean> discard() {
        new DiscardOperator(this.stream)
                .apply();

        CompletableFuture<Boolean> future = new CompletableFuture<>();

//        stream.doOnNext(ReferenceCountUtil::release)
//                .ignoreElements()
//                .doOnCompleted(() -> future.complete(true))
//                .doOnError(future::completeExceptionally)
//                .subscribe();

        return future;
    }

//    @Override
//    public ContentStream map(Function<Buffer, Buffer> transformation) {
//        return new ByteStream(this.stream
//                .map(Buffer::new)
//                .map(transformation::apply)
//                .map(Buffer::delegate));
//    }

//    public void consume(Consumer<ContentStream.Event> consumer) {
//        stream.subscribe(buf -> consumer.accept(toContentEvent(buf)),
//                error -> consumer.accept(toErrorEvent(error)),
//                () -> consumer.accept(toEndOfStreamEvent()));
//    }

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

}
