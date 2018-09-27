package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MappingOperator implements Publisher<Buffer> {
    private final Function<Buffer, Buffer> mapping;
    private final Publisher<Buffer> upstream;
    private AtomicReference<Subscriber<? super Buffer>> subscriber = new AtomicReference<>();
    private Subscription subscription;

    public MappingOperator(Publisher<Buffer> upstream, Function<Buffer, Buffer> mapping) {
        this.upstream = upstream;
        this.mapping = mapping;
    }

    // TODO: calls to subscribe simultaneously from multiple threads:
    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        if (this.subscriber.compareAndSet(null, subscriber)) {
            MapSubscription x = new MapSubscription(mapping, subscriber);
            // Subscribe downstream first, then upstream.
            // Otherwise upstream events may arrive before emitting an onSubscribe event.
            subscriber.onSubscribe(x);
            this.upstream.subscribe(x);
        } else {
            throw new IllegalStateException("Only one subscription is allowed");
        }
    }

    private static class MapSubscription implements Subscription , Subscriber<Buffer>  {
        private final Function<Buffer, Buffer> mapping;
        private final Subscriber<? super Buffer> subscriber;
        private Subscription subscription;

        public MapSubscription(Function<Buffer, Buffer> mapping, Subscriber<? super Buffer> subscriber) {
            this.mapping = mapping;
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (subscription == null) {
                throw new NullPointerException();
            }
            System.out.println("map onSubscribe");
            this.subscription = subscription;
        }

        @Override
        public void request(long n) {
            System.out.println("map request " + n);
            subscription.request(n);
        }

        @Override
        public void cancel() {
            System.out.println("map cancel");
            subscription.cancel();
        }

        @Override
        public void onNext(Buffer buffer) {
            System.out.println("map onNext");
            subscriber.onNext(mapping.apply(buffer));
        }

        @Override
        public void onError(Throwable throwable) {
            System.out.println("map onError");
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            System.out.println("map onComplete");
            subscriber.onComplete();
        }
    }

}
