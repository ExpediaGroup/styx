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
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Producer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Todo: Javadoc: RxContentConsumer consumes content.
 */
public class RxContentConsumer {
    private final Publisher<Buffer> publisher;
    private static final Logger LOGGER = LoggerFactory.getLogger(RxContentConsumer.class);

    public RxContentConsumer(Publisher<Buffer> publisher) {
        this.publisher = publisher;
    }

    Observable<Buffer> consume() {
        InternalSubscription subscription = new InternalSubscription(this.publisher);
        return Observable.create(subscription);
    }

    static class InternalSubscription implements Observable.OnSubscribe<Buffer>, Producer, Subscriber<Buffer>, Consumer<Event> {

        private final EventProcessor eventProcessor = new EventProcessor(this);
        private rx.Subscriber<? super Buffer> downstreamSubscriber;
        private Subscription upstreamSubscription;
        private Publisher<Buffer> publisher;
        private final ConcurrentLinkedDeque<Buffer> queue = new ConcurrentLinkedDeque<>();
        private Throwable errorCause;

        public InternalSubscription(Publisher<Buffer> publisher) {
            this.publisher = publisher;
        }

        enum State {
            INITIAL, BUFFERING, EMITTING, COMPLETED, FINISHING
        }

        private volatile State state = State.INITIAL;
        private volatile long requests;

        @Override
        public void accept(Event event) {
            LOGGER.debug("Accept: {}: {}", state, event.toString());
            // Runs the state machine
            if (state == State.INITIAL) {
                if (event instanceof DownstreamSubscribedEvent) {
                    downstreamSubscriber = ((DownstreamSubscribedEvent) event).subscriber;
                    publisher.subscribe(this);
                    ((DownstreamSubscribedEvent) event).subscriber.setProducer(this);
                    if (upstreamSubscription != null) {
                        transitionTo(State.BUFFERING);
                    }
                } else if (event instanceof UpstreamSubscribed) {
                    upstreamSubscription = ((UpstreamSubscribed) event).subscription;

                    if (downstreamSubscriber != null) {
                        transitionTo(State.BUFFERING);
                    }
                }
            } else if (state == State.BUFFERING) {
                if (event instanceof DownstreamRequestedEvent) {
                    long n = ((DownstreamRequestedEvent) event).n;
                    if (n > 0) {
                        upstreamSubscription.request(n);
                        requests += n;
                        state = State.EMITTING;
                    }
                } else if (event instanceof UpstreamOnNextEvent) {
                    queue.add(((UpstreamOnNextEvent) event).buffer);
                } else if (event instanceof UpstreamOnCompleteEvent) {
                    if (queue.isEmpty()) {
                        downstreamSubscriber.onCompleted();
                        state = State.COMPLETED;
                    } else {
                        state = State.FINISHING;
                    }
                } else if (event instanceof UpstreamOnErrorEvent) {
                    if (queue.isEmpty()) {
                        downstreamSubscriber.onError(((UpstreamOnErrorEvent) event).cause);
                        state = State.COMPLETED;
                    } else {
                        state = State.FINISHING;
                    }
                }
            } else if (state == State.EMITTING) {
                if (event instanceof DownstreamRequestedEvent) {
                    upstreamSubscription.request(((DownstreamRequestedEvent) event).n);
                    requests += ((DownstreamRequestedEvent) event).n;
                    if (requests == 0) {
                        transitionTo(State.BUFFERING);
                    }
                } else if (event instanceof UpstreamOnNextEvent) {
                    queue.add(((UpstreamOnNextEvent) event).buffer);
                    emit();
                    if (requests == 0) {
                        transitionTo(State.BUFFERING);
                    }
                } else if (event instanceof UpstreamOnCompleteEvent) {
                    downstreamSubscriber.onCompleted();
                    transitionTo(State.COMPLETED);
                } else if (event instanceof UpstreamOnErrorEvent) {
                    errorCause = ((UpstreamOnErrorEvent) event).cause;
                    downstreamSubscriber.onError(errorCause);
                    transitionTo(State.COMPLETED);
                }
            } else if (state == State.FINISHING) {
                if (event instanceof DownstreamRequestedEvent) {
                    upstreamSubscription.request(((DownstreamRequestedEvent) event).n);
                    requests += ((DownstreamRequestedEvent) event).n;

                    if (queue.isEmpty() && errorCause == null) {
                        downstreamSubscriber.onCompleted();
                        transitionTo(State.COMPLETED);
                    } else if (queue.isEmpty()) {
                        downstreamSubscriber.onError(errorCause);
                        transitionTo(State.COMPLETED);
                    }
                } else {
                    // Unexpected event!
                    LOGGER.warn("Unexpected event in FINISHING state: {}", event);
                }
            }
        }

        private void emit() {
            while (requests > 0 && !queue.isEmpty()) {
                Buffer b = queue.poll();
                downstreamSubscriber.onNext(b);
                requests--;
            }
        }


        void transitionTo(State nextState) {
            state = nextState;
        }

        @Override
        public void call(rx.Subscriber<? super Buffer> subscriber) {
            LOGGER.debug("DownstreamSubscribed");
            eventProcessor.submit(new DownstreamSubscribedEvent(subscriber));
        }

        @Override
        public void request(long n) {
            LOGGER.debug("request {}", n);
            eventProcessor.submit(new DownstreamRequestedEvent(n));
        }

        @Override
        public void onSubscribe(org.reactivestreams.Subscription subscription) {
            LOGGER.debug("onSubscribe");
            eventProcessor.submit(new UpstreamSubscribed(subscription));
        }

        @Override
        public void onNext(Buffer buffer) {
            LOGGER.debug("onNext");
            eventProcessor.submit(new UpstreamOnNextEvent(buffer));
        }

        @Override
        public void onError(Throwable cause) {
            LOGGER.debug("onError");
            eventProcessor.submit(new UpstreamOnErrorEvent(cause));
        }

        @Override
        public void onComplete() {
            LOGGER.debug("onComplete");
            eventProcessor.submit(new UpstreamOnCompleteEvent());
        }
    }

    interface Event {

    }

    static class DownstreamSubscribedEvent implements Event {
        private rx.Subscriber<? super Buffer> subscriber;

        public DownstreamSubscribedEvent(rx.Subscriber<? super Buffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DownstreamSubscribedEvent{");
            sb.append('}');
            return sb.toString();
        }
    }

    static class DownstreamRequestedEvent implements Event {
        private long n;

        public DownstreamRequestedEvent(long n) {
            this.n = n;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DownstreamRequestedEvent{");
            sb.append("n=").append(n);
            sb.append('}');
            return sb.toString();
        }
    }

    static class UpstreamSubscribed implements Event {
        private Subscription subscription;

        public UpstreamSubscribed(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UpstreamSubscribed{");
            sb.append('}');
            return sb.toString();
        }
    }

    static class UpstreamOnNextEvent implements Event {
        private Buffer buffer;

        public UpstreamOnNextEvent(Buffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UpstreamOnNextEvent{");
            sb.append('}');
            return sb.toString();
        }
    }

    static class UpstreamOnErrorEvent implements Event {
        private Throwable cause;

        public UpstreamOnErrorEvent(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UpstreamOnErrorEvent{");
            sb.append("cause=").append(cause.getClass().getSimpleName());
            sb.append(", message=").append(cause.getMessage());
            sb.append('}');
            return sb.toString();
        }
    }

    static class UpstreamOnCompleteEvent implements Event {
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UpstreamOnCompleteEvent{");
            sb.append('}');
            return sb.toString();
        }
    }

    static class EventProcessor {
        private final Queue<Event> events = new ConcurrentLinkedDeque<>();
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private final Consumer<Event> consumer;
        private final boolean logErrors;

        public EventProcessor(Consumer<Event> consumer) {
            this(consumer, false);
        }

        public EventProcessor(Consumer<Event> consumer, boolean logErrors) {
            this.consumer = requireNonNull(consumer);
            this.logErrors = logErrors;
        }

        public void submit(Event event) {
            events.add(event);
            if (eventCount.getAndIncrement() == 0) {
                do {
                    Event e = events.poll();
                    try {
                        consumer.accept(e);
                    } catch (RuntimeException cause) {
                        // TODO: Event threw an exception.
                    }
                } while (eventCount.decrementAndGet() > 0);
            }
        }
    }

}
