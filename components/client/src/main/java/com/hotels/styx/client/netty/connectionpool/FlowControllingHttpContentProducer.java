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
package com.hotels.styx.client.netty.connectionpool;

import com.google.common.annotations.VisibleForTesting;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.client.netty.ConsumerDisconnectedException;
import com.hotels.styx.common.StateMachine;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

import static com.google.common.base.Objects.toStringHelper;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.BUFFERING;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.BUFFERING_COMPLETED;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.COMPLETED;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.EMITTING_BUFFERED_CONTENT;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.STREAMING;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.TERMINATED;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static rx.internal.operators.BackpressureUtils.getAndAddRequest;

class FlowControllingHttpContentProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowControllingHttpContentProducer.class);

    private final StateMachine<ProducerState> stateMachine;
    private final String loggingPrefix;

    private final Runnable askForMore;
    private final Runnable onCompleteAction;
    private final Consumer<Throwable> onTerminateAction;
    private final Runnable delayedTearDownAction;

    private final Queue<ByteBuf> readQueue = new ConcurrentLinkedDeque<>();
    private final AtomicLong requested = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong receivedChunks = new AtomicLong(0);
    private final AtomicLong receivedBytes = new AtomicLong(0);
    private final AtomicLong emittedChunks = new AtomicLong(0);
    private final AtomicLong emittedBytes = new AtomicLong(0);

    private final Origin origin;

    private volatile Subscriber<? super ByteBuf> contentSubscriber;

    enum ProducerState {
        BUFFERING,
        STREAMING,
        BUFFERING_COMPLETED,
        EMITTING_BUFFERED_CONTENT,
        COMPLETED,
        TERMINATED
    }

    FlowControllingHttpContentProducer(
            Runnable askForMore,
            Runnable onCompleteAction,
            Consumer<Throwable> onTerminateAction,
            Runnable delayedTearDownAction,
            String loggingPrefix,
            Origin origin) {
        this.askForMore = requireNonNull(askForMore);
        this.onCompleteAction = requireNonNull(onCompleteAction);
        this.onTerminateAction = requireNonNull(onTerminateAction);
        this.delayedTearDownAction = requireNonNull(delayedTearDownAction);
        this.origin = requireNonNull(origin);

        this.stateMachine = new StateMachine.Builder<ProducerState>()
                .initialState(BUFFERING)
                .transition(BUFFERING, RxBackpressureRequestEvent.class, this::rxBackpressureRequestBeforeSubscription)
                .transition(BUFFERING, ContentChunkEvent.class, this::contentChunkEventWhileBuffering)
                .transition(BUFFERING, ChannelInactiveEvent.class, this::releaseAndTerminate)
                .transition(BUFFERING, ChannelExceptionEvent.class, this::releaseAndTerminate)
                .transition(BUFFERING, ContentSubscribedEvent.class, this::contentSubscribedEventWhileBuffering)
                .transition(BUFFERING, ContentEndEvent.class, this::contentEndEventWhileBuffering)

                .transition(BUFFERING_COMPLETED, RxBackpressureRequestEvent.class, this::rxBackpressureRequestBeforeSubscription)
                .transition(BUFFERING_COMPLETED, ContentChunkEvent.class, this::spuriousContentChunkEvent)
                .transition(BUFFERING_COMPLETED, ChannelInactiveEvent.class, s-> scheduleTearDown(BUFFERING_COMPLETED))
                .transition(BUFFERING_COMPLETED, ChannelExceptionEvent.class, s -> BUFFERING_COMPLETED)
                .transition(BUFFERING_COMPLETED, DelayedTearDownEvent.class, this::releaseAndTerminate)
                .transition(BUFFERING_COMPLETED, ContentSubscribedEvent.class, this::contentSubscribedEventWhileBufferingCompleted)
                .transition(BUFFERING_COMPLETED, ContentEndEvent.class, s -> BUFFERING_COMPLETED)

                .transition(STREAMING, RxBackpressureRequestEvent.class, this::rxBackpressureRequestEventWhileStreaming)
                .transition(STREAMING, ContentChunkEvent.class, this::contentChunkEventWhileStreaming)
                .transition(STREAMING, ChannelInactiveEvent.class, e -> emitErrorAndTerminate(e.cause()))
                .transition(STREAMING, ChannelExceptionEvent.class, e -> emitErrorAndTerminate(e.cause()))
                .transition(STREAMING, ContentSubscribedEvent.class, this::contentSubscribedEventWhileStreaming)
                .transition(STREAMING, ContentEndEvent.class, this::contentEndEventWhileStreaming)
                .transition(STREAMING, UnsubscribeEvent.class, this::emitErrorAndTerminateOnPrematureUnsubscription)

                .transition(EMITTING_BUFFERED_CONTENT, RxBackpressureRequestEvent.class, this::rxBackpressureRequestEventWhileEmittingBufferedContent)
                .transition(EMITTING_BUFFERED_CONTENT, ContentChunkEvent.class, this::spuriousContentChunkEvent)
                .transition(EMITTING_BUFFERED_CONTENT, ChannelInactiveEvent.class, s -> scheduleTearDown(EMITTING_BUFFERED_CONTENT))
                .transition(EMITTING_BUFFERED_CONTENT, ChannelExceptionEvent.class, s -> EMITTING_BUFFERED_CONTENT)
                .transition(EMITTING_BUFFERED_CONTENT, DelayedTearDownEvent.class, s -> emitErrorAndTerminate(s.cause()))
                .transition(EMITTING_BUFFERED_CONTENT, ContentSubscribedEvent.class, this::contentSubscribedEventWhileEmittingBufferedContent)
                .transition(EMITTING_BUFFERED_CONTENT, ContentEndEvent.class, this::contentEndEventWhileEmittingBufferedContent)
                .transition(EMITTING_BUFFERED_CONTENT, UnsubscribeEvent.class, this::emitErrorAndTerminateOnPrematureUnsubscription)

                .transition(COMPLETED, ContentChunkEvent.class, this::spuriousContentChunkEvent)
                .transition(COMPLETED, UnsubscribeEvent.class, ev -> COMPLETED)
                .transition(COMPLETED, RxBackpressureRequestEvent.class, ev -> COMPLETED)
                .transition(COMPLETED, ContentSubscribedEvent.class, this::contentSubscribedInCompletedState)
                .transition(COMPLETED, DelayedTearDownEvent.class, ev -> COMPLETED)

                .transition(TERMINATED, ContentChunkEvent.class, this::spuriousContentChunkEvent)
                .transition(TERMINATED, ContentSubscribedEvent.class, this::contentSubscribedInTerminatedState)
                .transition(TERMINATED, RxBackpressureRequestEvent.class, ev -> TERMINATED)

                .onInappropriateEvent((state, event) -> {
                    LOGGER.warn(warningMessage("Inappropriate event=" + event.getClass().getSimpleName()));
                    return state;
                })
                .build();

        this.loggingPrefix = loggingPrefix;
    }

    /*
     * BUFFERING state event handlers
     */
    private ProducerState rxBackpressureRequestBeforeSubscription(RxBackpressureRequestEvent event) {
        // This can occur before the actual content subscribe event. This occurs if the subscriber
        // has called request() before actually having subscribed to the content observable. In this
        // case just initialise the request count with requested N value.
        requested.compareAndSet(Long.MAX_VALUE, 0);
        getAndAddRequest(requested, event.n());
        return this.state();
    }

    private ProducerState contentChunkEventWhileBuffering(ContentChunkEvent event) {
        receivedBytes.addAndGet(event.chunk.readableBytes());
        receivedChunks.incrementAndGet();
        readQueue.add(event.chunk);
        return BUFFERING;
    }

    private <T> ProducerState releaseAndTerminate(CausalEvent event) {
        releaseBuffers();
        onTerminateAction.accept(event.cause());
        return TERMINATED;
    }

    private ProducerState contentSubscribedEventWhileBuffering(ContentSubscribedEvent event) {
        this.contentSubscriber = event.subscriber;
        emitChunks(this.contentSubscriber);
        return STREAMING;
    }

    private ProducerState contentEndEventWhileBuffering(ContentEndEvent event) {
        return BUFFERING_COMPLETED;
    }

    /*
     * BUFFERING_COMPLETED event handlers
     */

    private ProducerState spuriousContentChunkEvent(ContentChunkEvent event) {
        // Should not occur because content has already been fully consumed.
        LOGGER.warn(warningMessage("Spurious content chunk."));
        ReferenceCountUtil.release(event.chunk);
        return this.state();
    }


    private ProducerState contentSubscribedEventWhileBufferingCompleted(ContentSubscribedEvent event) {
        this.contentSubscriber = event.subscriber;
        if (readQueue.size() == 0) {
            this.contentSubscriber.onCompleted();
            this.onCompleteAction.run();
            return COMPLETED;
        }

        emitChunks(this.contentSubscriber);

        if (readQueue.size() > 0) {
            return EMITTING_BUFFERED_CONTENT;
        } else {
            this.contentSubscriber.onCompleted();
            this.onCompleteAction.run();
            return COMPLETED;
        }
    }


    /*
     * STREAMING event handlers
     */
    private ProducerState rxBackpressureRequestEventWhileStreaming(RxBackpressureRequestEvent event) {
        requested.compareAndSet(Long.MAX_VALUE, 0);
        getAndAddRequest(requested, event.n());

        if (requested.get() <= 1) {
            askForMore.run();
        }
        emitChunks(contentSubscriber);

        return STREAMING;
    }

    private ProducerState contentChunkEventWhileStreaming(ContentChunkEvent event) {
        receivedBytes.addAndGet(event.chunk.readableBytes());
        receivedChunks.incrementAndGet();
        readQueue.add(event.chunk);
        emitChunks(contentSubscriber);
        return STREAMING;
    }

    private ProducerState emitErrorAndTerminate(Throwable cause) {
        releaseBuffers();
        contentSubscriber.onError(cause);
        onTerminateAction.accept(cause);
        return TERMINATED;

    }

    private ProducerState contentSubscribedEventWhileStreaming(ContentSubscribedEvent event) {
        // Subscription is already in place in Streaming state. Therefore this is a second subscription,
        // which is not allowed.
        releaseBuffers();
        IllegalStateException cause = new IllegalStateException(
                format("Secondary subscription occurred. producerState=%s. connection=%s",
                        state(), loggingPrefix));
        contentSubscriber.onError(cause);
        event.subscriber.onError(cause);
        onTerminateAction.accept(cause);
        return TERMINATED;
    }

    private ProducerState contentSubscribedInCompletedState(ContentSubscribedEvent event) {
        event.subscriber.onError(new IllegalStateException(
                format("Secondary subscription occurred. producerState=%s. connection=%s", state(), loggingPrefix)));
        return COMPLETED;
    }

    private ProducerState contentSubscribedInTerminatedState(ContentSubscribedEvent event) {
        event.subscriber.onError(new IllegalStateException(
                format("Secondary subscription occurred. producerState=%s. connection=%s", state(), loggingPrefix)));
        return TERMINATED;
    }

    private ProducerState contentEndEventWhileStreaming(ContentEndEvent event) {
        if (readQueue.size() > 0) {
            return EMITTING_BUFFERED_CONTENT;
        } else {
            this.contentSubscriber.onCompleted();
            this.onCompleteAction.run();
            return COMPLETED;
        }
    }

    private ProducerState emitErrorAndTerminateOnPrematureUnsubscription(UnsubscribeEvent event) {
        return emitErrorAndTerminate(
                new ConsumerDisconnectedException(
                        format("The consumer unsubscribed. connection=%s", loggingPrefix),
                        this.state().toString()));
    }

    /*
     * EMITTING_BUFFERED_CONTENT event handlers
     */
    private ProducerState rxBackpressureRequestEventWhileEmittingBufferedContent(RxBackpressureRequestEvent event) {
        requested.compareAndSet(Long.MAX_VALUE, 0);
        getAndAddRequest(requested, event.n());
        if (requested.get() <= 1) {
            askForMore.run();
        }
        emitChunks(contentSubscriber);
        if (readQueue.size() == 0) {
            LOGGER.debug("{} -> {}", new Object[]{this.state(), COMPLETED});
            this.contentSubscriber.onCompleted();
            this.onCompleteAction.run();
            return COMPLETED;
        } else {
            return EMITTING_BUFFERED_CONTENT;
        }
    }

    private ProducerState contentSubscribedEventWhileEmittingBufferedContent(ContentSubscribedEvent event) {
        // A second subscription occurred for the content observable that already
        // has a subscriber. Something is probably gone badly wrong. Therefore tear
        // everything down.

        releaseBuffers();

        event.subscriber.onError(
                new IllegalStateException(
                        format("Content observable is already subscribed. producerState=%s, connection=%s",
                                state(), loggingPrefix)));
        contentSubscriber.onError(
                new IllegalStateException(
                        format("Secondary subscription occurred. producerState=%s, connection=%s",
                                state(), loggingPrefix)));
        onTerminateAction.accept(
                new IllegalStateException(
                        format("Secondary content subscription detected. producerState=%s. connection=%s",
                                state(), loggingPrefix)));

        return TERMINATED;
    }

    private ProducerState contentEndEventWhileEmittingBufferedContent(ContentEndEvent event) {
        // Does not happen, because last HTTP content is already received.
        return EMITTING_BUFFERED_CONTENT;
    }

    private ProducerState scheduleTearDown(ProducerState state) {
        delayedTearDownAction.run();
        return state;
    }

    /*
     * Event injector methods:
     */
    void newChunk(ByteBuf content) {
        stateMachine.handle(new ContentChunkEvent(content));
    }

    void lastHttpContent() {
        stateMachine.handle(new ContentEndEvent());
    }

    void channelException(Throwable cause) {
        stateMachine.handle(new ChannelExceptionEvent(cause));
    }

    void channelInactive(Throwable cause) {
        stateMachine.handle(new ChannelInactiveEvent(cause));
    }

    void tearDownResources() {
        stateMachine.handle(new DelayedTearDownEvent(new ResponseTimeoutException(origin,
                "channelClosed",
                receivedBytes(),
                receivedChunks(),
                emittedBytes(),
                emittedChunks())));
    }

    void request(long n) {
        stateMachine.handle(new RxBackpressureRequestEvent(n));
    }

    void onSubscribed(Subscriber<? super ByteBuf> subscriber) {
        if (inSubscribedState()) {
            LOGGER.warn(warningMessage("Secondary content subscription"));
        }
        stateMachine.handle(new ContentSubscribedEvent(subscriber));
    }

    private boolean inSubscribedState() {
        return state() == COMPLETED || state() == STREAMING || state() == EMITTING_BUFFERED_CONTENT || state() == TERMINATED;
    }

    void unsubscribe() {
        stateMachine.handle(new UnsubscribeEvent());
    }

    long emittedBytes() {
        return emittedBytes.get();
    }

    long emittedChunks() {
        return emittedChunks.get();
    }

    long receivedBytes() {
        return receivedBytes.get();
    }

    long receivedChunks() {
        return receivedChunks.get();
    }

    /*
     * Helper methods:
     */
    private void releaseBuffers() {
        ByteBuf value;
        while ((value = this.readQueue.poll()) != null) {
            ReferenceCountUtil.release(value);
        }
    }

    // This must not be run with locks held:
    private void emitChunks(Subscriber<? super ByteBuf> downstream) {
        LongUnaryOperator decrementIfBackpressureEnabled = current -> current == Long.MAX_VALUE ? current : current > 0 ? current - 1 : 0;
        LongUnaryOperator incrementIfBackpressureEnabled = current -> current == Long.MAX_VALUE ? current : current + 1;

        while (requested.getAndUpdate(decrementIfBackpressureEnabled) > 0) {
            ByteBuf value = this.readQueue.poll();
            if (value == null) {
                requested.getAndUpdate(incrementIfBackpressureEnabled);
                break;
            }
            emittedBytes.addAndGet(value.readableBytes());
            emittedChunks.incrementAndGet();
            downstream.onNext(value);

        }
    }

    @VisibleForTesting
    ProducerState state() {
        return stateMachine.currentState();
    }

    private String warningMessage(String msg) {
        return format("message=\"%s\", prefix=%s, state=%s, receivedChunks=%d, receivedBytes=%d, emittedChunks=%d, emittedBytes=%d",
                msg, loggingPrefix, state(), receivedChunks.get(), receivedBytes.get(), emittedChunks.get(), emittedBytes.get());
    }

    private static final class ContentChunkEvent {
        private final ByteBuf chunk;

        ContentChunkEvent(ByteBuf chunk) {
            this.chunk = requireNonNull(chunk);
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("chunk", chunk)
                    .toString();
        }
    }

    private static final class ContentEndEvent {
        @Override
        public String toString() {
            return toStringHelper(this)
                    .toString();
        }
    }

    private static final class ContentSubscribedEvent {
        private final Subscriber<? super ByteBuf> subscriber;

        ContentSubscribedEvent(Subscriber<? super ByteBuf> subscriber) {
            this.subscriber = requireNonNull(subscriber);
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("subscriber", subscriber)
                    .toString();
        }
    }

    private static final class RxBackpressureRequestEvent {
        private final long n;

        RxBackpressureRequestEvent(long n) {
            this.n = n;
        }

        long n() {
            return n;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("n", n)
                    .toString();
        }
    }

    private interface CausalEvent {
        Throwable cause();
    }

    private static final class ChannelInactiveEvent implements CausalEvent {
        private final Throwable cause;

        ChannelInactiveEvent(Throwable cause) {
            this.cause = requireNonNull(cause);
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("cause", cause)
                    .toString();
        }
    }

    private static final class ChannelExceptionEvent implements CausalEvent {
        private final Throwable cause;

        ChannelExceptionEvent(Throwable cause) {
            this.cause = requireNonNull(cause);
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("cause", cause)
                    .toString();
        }
    }

    private static final class DelayedTearDownEvent implements CausalEvent {
        private final Throwable cause;

        private DelayedTearDownEvent(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("cause", cause)
                    .toString();
        }
    }

    private static class UnsubscribeEvent {
    }
}
