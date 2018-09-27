package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class DiscardOperator implements Subscriber<Buffer> {
    private final AtomicBoolean active = new AtomicBoolean();
    private final Publisher<Buffer> upstream;
    private final AtomicReference<Subscription> subscription = new AtomicReference<>();

    public DiscardOperator(Publisher<Buffer> upstream) {
        this.upstream = requireNonNull(upstream);
    }

    public Publisher<Buffer> apply() {
        if (active.compareAndSet(false, true)) {
            this.upstream.subscribe(this);
        } else {
            throw new IllegalStateException("Secondary subscription!");
        }

        return subscriber -> subscriber.onSubscribe(new Subscription() {
            private volatile boolean completed;

            @Override
            public void request(long l) {
                if (!completed) {
                    subscriber.onComplete();
                    completed = true;
                }
            }

            @Override
            public void cancel() {

            }
        });
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (this.subscription.compareAndSet(null, requireNonNull(subscription))) {
            subscription.request(Long.MAX_VALUE);
        } else {
            subscription.cancel();
            throw new IllegalStateException("Only one subscription is allowed!");
        }
    }

    @Override
    public void onNext(Buffer buffer) {
        buffer.delegate().release();
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}
