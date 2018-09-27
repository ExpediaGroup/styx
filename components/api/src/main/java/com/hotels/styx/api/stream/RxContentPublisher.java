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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Relies on source observable to satisfy the reactive publisher specification.
 * This needs to be rectified.
 */
public class RxContentPublisher implements Publisher<Buffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RxContentPublisher.class);
    private final Observable<Buffer> upstream;
    private final AtomicReference<ContentSubscription> subscription = new AtomicReference<>();
    private final boolean flowControl;

    enum State {
        INITIAL, BUFFERING, EMITTING, FINISHING, COMPLETED
    }

    public RxContentPublisher(Observable<Buffer> upstream) {
        this.upstream = requireNonNull(upstream);
        this.flowControl = true;
    }

    public RxContentPublisher(Observable<Buffer> upstream, boolean flowControl) {
        this.upstream = requireNonNull(upstream);
        this.flowControl = flowControl;
    }

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        ContentSubscription s = new ContentSubscription(subscriber, this.upstream, this.flowControl);
        s.init();
    }

    private static class ContentSubscription implements Subscription, Consumer<Event> {
        private final RxBridgeSubscriber bridge;

        private final EventProcessor eventProcessor = new EventProcessor(this);
        private final Subscriber<? super Buffer> downstream;
        private final Observable<Buffer> upstream;
        private volatile long requests;
        private volatile State state = State.INITIAL;
        private final ConcurrentLinkedDeque<Buffer> queue = new ConcurrentLinkedDeque<>();
        private Throwable errorCause;
        private rx.Subscription upstreamSubscription;


        ContentSubscription(Subscriber<? super Buffer> downstream, Observable<Buffer> upstream, boolean flowControl) {
            this.downstream = downstream;
            this.upstream = upstream;
            this.bridge = new RxBridgeSubscriber(eventProcessor, flowControl);
        }

        public void init() {
            eventProcessor.submit(new SubscriptionEvent(downstream));
        }


        /*
         * - allow multiple threads to request?
         * - only one thread in emit loop
         *
         *
         *
         */
        @Override
        public void request(long n) {
            eventProcessor.submit(new RequestEvent(n));
        }

        @Override
        public void cancel() {
            eventProcessor.submit(new CancelEvent());
        }

        void transitionTo(State nextState) {
            state = nextState;
        }


        /**
         * Todo: Javadoc: Processes events.
         *
         * @param event
         */
        @Override
        public void accept(Event event) {
            LOGGER.debug("accept: {} - {}", state, event);
            if (state == State.INITIAL) {
                if (event instanceof SubscriptionEvent) {
                    upstreamSubscription = upstream.subscribe(bridge);
                    downstream.onSubscribe(this);
                    transitionTo(State.BUFFERING);
                } else {
                    LOGGER.warn("Unexpected event in INITIAL state: {}", event);
                }
            } else if (state == State.BUFFERING) {
                if (event instanceof RequestEvent) {
                    if (request((RequestEvent) event)) {
                        return;
                    }
                    if (requests > 0) {
                        transitionTo(State.EMITTING);
                    }
                } else if (event instanceof CancelEvent) {
                    bridge.cancel();
                    drain();
                    transitionTo(State.COMPLETED);
                } else if (event instanceof UpstreamOnNextEvent) {
                    queue.add(((UpstreamOnNextEvent) event).buffer);
                } else if (event instanceof UpstreamOnCompleteEvent) {
                    if (queue.isEmpty()) {
                        downstream.onComplete();
                        transitionTo(State.COMPLETED);
                    } else {
                        // Cannot emit queued events, because downstream is pushing back
                        transitionTo(State.FINISHING);
                    }
                } else if (event instanceof UpstreamOnErrorEvent) {
                    errorCause = ((UpstreamOnErrorEvent) event).cause;
                    if (queue.isEmpty()) {
                        downstream.onError(((UpstreamOnErrorEvent) event).cause);
                        transitionTo(State.COMPLETED);
                    } else {
                        // Cannot emit queued events, because downstream is pushing back
                        transitionTo(State.FINISHING);
                    }
                }
            } else if (state == State.EMITTING) {
                if (event instanceof RequestEvent) {
                    if (request((RequestEvent) event)) {
                        return;
                    }

                    if (requests == 0) {
                        transitionTo(State.BUFFERING);
                    }
                } else if (event instanceof CancelEvent) {
                    bridge.cancel();
                    drain();
                    transitionTo(State.COMPLETED);
                } else if (event instanceof UpstreamOnNextEvent) {
                    queue.add(((UpstreamOnNextEvent) event).buffer);
                    emit();
                    if (requests == 0) {
                        transitionTo(State.BUFFERING);
                    }
                } else if (event instanceof UpstreamOnCompleteEvent) {
                    downstream.onComplete();
                    transitionTo(State.COMPLETED);
                } else if (event instanceof UpstreamOnErrorEvent) {
                    errorCause = ((UpstreamOnErrorEvent) event).cause;
                    downstream.onError(errorCause);
                    transitionTo(State.COMPLETED);
                }
            } else if (state == State.FINISHING) {
                if (event instanceof RequestEvent) {
                    if (request((RequestEvent) event)) {
                        return;
                    }

                    if (queue.isEmpty() && errorCause == null) {
                        downstream.onComplete();
                        transitionTo(State.COMPLETED);
                    } else if (queue.isEmpty()) {
                        downstream.onError(errorCause);
                        transitionTo(State.COMPLETED);
                    }
                } else if (event instanceof CancelEvent) {
                    LOGGER.warn("TODO!");
                } else {
                    // Unexpected event!
                    LOGGER.warn("Unexpected event in FINISHING state: {}", event);
                }
            }
        }

        private boolean request(RequestEvent event) {
            long n = event.n;

            if (n <= 0) {
                downstream.onError(new IllegalArgumentException(format("Request count cannot be negative or zero (n=%d)", n)));
                bridge.cancel();
                transitionTo(State.COMPLETED);
                drain();
                return true;
            }

            requests += n;
            bridge.requestMore(n);

            emit();
            return false;
        }

        private void drain() {
            // TODO: drain any outstanding buffers.
            // TODO: Decrement reference counts.
            // TODO: Add an unit test
            while (!queue.isEmpty()) {
                queue.removeFirst();
            }
        }


        private void emit() {
            while (requests > 0 && !queue.isEmpty()) {
                Buffer b = queue.poll();
                downstream.onNext(b);
                requests--;
            }
        }

    }


    static class RxBridgeSubscriber extends rx.Subscriber<Buffer> {
        private final EventProcessor eventProcessor;
        private final boolean flowControl;

        RxBridgeSubscriber(EventProcessor eventProcessor, boolean flowControl) {
            this.eventProcessor = eventProcessor;
            this.flowControl = flowControl;
        }

        @Override
        public void onStart() {
            if (flowControl) {
                request(0);
            } else {
                request(Long.MAX_VALUE);
            }
            super.onStart();
        }

        @Override
        public void onCompleted() {
            eventProcessor.submit(new UpstreamOnCompleteEvent());
        }

        @Override
        public void onError(Throwable cause) {
            eventProcessor.submit(new UpstreamOnErrorEvent(cause));
        }

        @Override
        public void onNext(Buffer buffer) {
            eventProcessor.submit(new UpstreamOnNextEvent(buffer));
        }

        void requestMore(long n) {
            if (flowControl) {
                request(n);
            }
        }


        public void cancel() {
            unsubscribe();
        }

    }


    interface Event {
    }

    static class SubscriptionEvent implements Event {
        private final Subscriber<? super Buffer> downstream;

        SubscriptionEvent(Subscriber<? super Buffer> downstream) {
            this.downstream = downstream;
        }
    }

    static class RequestEvent implements Event {
        private final long n;

        RequestEvent(long n) {
            this.n = n;
        }
    }

    private static class CancelEvent implements Event {
    }

    static class UpstreamOnNextEvent implements Event {
        private final Buffer buffer;

        UpstreamOnNextEvent(Buffer buffer) {
            this.buffer = buffer;
        }
    }

    private static class UpstreamOnCompleteEvent implements Event {
    }

    static class UpstreamOnErrorEvent implements Event {
        private final Throwable cause;

        UpstreamOnErrorEvent(Throwable cause) {
            this.cause = cause;
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
