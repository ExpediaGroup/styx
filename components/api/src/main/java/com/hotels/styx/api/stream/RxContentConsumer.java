package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import rx.Observable;
import rx.Observer;
import rx.observables.SyncOnSubscribe;

import java.util.concurrent.ConcurrentLinkedDeque;

public class RxContentConsumer {
    private final Publisher<Buffer> publisher;

    public RxContentConsumer(Publisher<Buffer> publisher) {
        this.publisher = publisher;
    }

    Observable<Buffer> consume() {

        return Observable.create(new SyncOnSubscribe<ProducerState, Buffer>() {
            @Override
            protected ProducerState generateState() {
                ProducerState state = new ProducerState(publisher, new MySubscriber(), 0);
                state.init();
                return state;
            }

            @Override
            protected ProducerState next(ProducerState state, Observer<? super Buffer> observer) {
                // Request in effect.
                return state.request(observer);
            }

        });
    }

    static class MySubscriber implements Subscriber<Buffer> {
        private final ConcurrentLinkedDeque<Observer<? super Buffer>> queue = new ConcurrentLinkedDeque<>();
        private Subscription subscription;
        private Observer<? super Buffer> lastObserver;

        @Override
        public void onSubscribe(Subscription subscription) {
//            System.out.println("onSubscribe");
            this.subscription = subscription;
        }

        @Override
        public void onNext(Buffer buffer) {
//            System.out.println("onNext");
            lastObserver = queue.removeFirst();
            lastObserver.onNext(buffer);
        }

        @Override
        public void onError(Throwable cause) {
//            System.out.println("onError");
            lastObserver.onError(cause);
            lastObserver = null;
        }

        @Override
        public void onComplete() {
//            System.out.println("onComplete");
            lastObserver.onCompleted();
            lastObserver = null;
        }

        void request(Observer<? super Buffer> observer) {
            queue.push(observer);
            subscription.request(1);
        }
    }


    private static class ProducerState {
        private final Publisher<Buffer> publisher;
        private final MySubscriber subscriber;
        private final long requested;

        ProducerState(Publisher<Buffer> publisher, MySubscriber subscriber, long n) {
            this.publisher = publisher;
            this.subscriber = subscriber;
            this.requested = n;
        }

        ProducerState request(Observer<? super Buffer> observer) {
            subscriber.request(observer);
            return new ProducerState(this.publisher, this.subscriber, this.requested + 1);
        }

        void init() {
            this.publisher.subscribe(this.subscriber);
        }
    }

}
