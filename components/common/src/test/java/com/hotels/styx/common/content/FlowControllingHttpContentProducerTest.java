/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.common.content;

import com.hotels.styx.api.exceptions.ContentTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscriber;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.BUFFERING;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.BUFFERING_COMPLETED;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.COMPLETED;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.EMITTING_BUFFERED_CONTENT;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.STREAMING;
import static com.hotels.styx.common.content.FlowControllingHttpContentProducer.ProducerState.TERMINATED;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class FlowControllingHttpContentProducerTest {
    private static final Long NO_BACKPRESSURE = Long.MAX_VALUE;
    private static final int INACTIVITY_TIMEOUT_MS = 500;
    private Subscriber<? super ByteBuf> downstream;
    private Subscriber<? super ByteBuf> additionalSubscriber;
    private FlowControllingHttpContentProducer producer;
    private Runnable askForMore;
    private Runnable onCompleteAction;
    private Consumer<Throwable> onTerminateAction;
    private ByteBuf contentChunk1;
    private ByteBuf contentChunk2;
    private TransportLostException transportLostCause = new TransportLostException(new InetSocketAddress(8080), newOriginBuilder("localhost", 8080).build());
    private LoggingTestSupport logger;
    private EventLoop eventLoop;

    public void setUpAndRequest(long initialCount) {
        downstream = mock(Subscriber.class);
        additionalSubscriber = mock(Subscriber.class);
        askForMore = mock(Runnable.class);
        onCompleteAction = mock(Runnable.class);
        onTerminateAction = mock(Consumer.class);
        eventLoop = mock(EventLoop.class);

        producer = new FlowControllingHttpContentProducer(
                askForMore,
                onCompleteAction,
                onTerminateAction,
                "foobar",
                newOriginBuilder("foohost", 12345).build());

        producer.request(initialCount);
    }

    @BeforeEach
    public void setUp() {
        contentChunk1 = copiedBuffer("aaa", UTF_8);
        contentChunk2 = copiedBuffer("bbb", UTF_8);
        logger = new LoggingTestSupport(FlowControllingHttpContentProducer.class);
    }

    @AfterEach
    public void tearDown() {
        logger.stop();
    }

    /* ---- Buffering state tests ---- */


    @Test
    public void transitionFromBufferingToStreamingState() {
        // On Subscribe, transition from buffering to streaming state
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.newChunk(copiedBuffer("aaa", UTF_8));
        producer.newChunk(copiedBuffer("bbb", UTF_8));
        verify(downstream, never()).onNext(any());

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));
        assertThat(captureOnNext(), contains(copiedBuffer("aaa", UTF_8), copiedBuffer("bbb", UTF_8)));
    }


    @Test
    public void transitionFromBufferingToBufferingCompletedState() {
        // Last HTTP Content event will trigger a transition to BUFFERING_COMPLETED state
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.newChunk(copiedBuffer("aaa", UTF_8));
        producer.newChunk(copiedBuffer("bbb", UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        verify(downstream, never()).onNext(any());
    }


    @Test
    public void handlesContentUnsubscriptionWhenStreaming() {
        // On Subscribe, transition from buffering to streaming state
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        ByteBuf firstChunk = copiedBuffer("aaa", UTF_8);
        producer.newChunk(firstChunk);
        verify(downstream, never()).onNext(any());

        producer.onSubscribed(downstream);

        assertThat(producer.state(), is(STREAMING));
        verify(downstream, never()).onNext(any());
        producer.unsubscribe();

        ByteBuf secondChunk = copiedBuffer("bbbb", UTF_8);
        producer.newChunk(secondChunk);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        verify(downstream, never()).onNext(any());
        verify(downstream, times(1)).onError(any(ConsumerDisconnectedException.class));
        assertThat(firstChunk.refCnt(), is(0));
        assertThat(secondChunk.refCnt(), is(0));
    }

    @Test
    public void handlesContentUnsubscriptionWhenEmitting() {
        // On Subscribe, transition from buffering to streaming state
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        ByteBuf firstChunk = copiedBuffer("aaa", UTF_8);

        producer.newChunk(firstChunk);
        verify(onCompleteAction, never()).run();
        producer.lastHttpContent();

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.unsubscribe();

        ByteBuf secondChunk = copiedBuffer("bbbb", UTF_8);
        producer.newChunk(secondChunk);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        verify(onCompleteAction, never()).run();
        assertException(downstream, Throwable.class, "The consumer unsubscribed. connection=foobar producerState=EMITTING_BUFFERED_CONTENT");
        assertThat(firstChunk.refCnt(), is(0));
        assertThat(secondChunk.refCnt(), is(0));
    }


    @Test
    public void channelExceptionInBufferingState() {
        // Releases buffered chunks when channelException triggers a transition to TERMINATED state.

        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        assertThat(contentChunk1.refCnt(), is(1));
        assertThat(contentChunk2.refCnt(), is(1));

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);

        producer.channelException(new RuntimeException("Something went wrong - simulated exception"));

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
    }

    @Test
    public void styxClosesChannelWhileInBufferingState() {
        // Releases buffered chunks when channel closure triggers a transition to TERMINATED state.

        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        assertThat(contentChunk1.refCnt(), is(1));
        assertThat(contentChunk2.refCnt(), is(1));

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);

        producer.channelInactive(transportLostCause);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
    }

    @Test
    public void channelUnexpectedlyTerminatesInBufferingState() {
        // Releases buffered chunks when channel closure triggers a transition to TERMINATED state.

        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        assertThat(contentChunk1.refCnt(), is(1));
        assertThat(contentChunk2.refCnt(), is(1));

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);

        producer.channelInactive(transportLostCause);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
    }


    /* ---- Streaming state tests ---- */

    @Test
    public void doesNotAllowDoubleSubscriptionInStreamingState() {
        // RxBackpressure event in Streaming state
        // Emits an IllegalStateException and releases buffered content:
        setUpAndRequest(0);

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.onSubscribed(additionalSubscriber);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
        assertException(downstream, IllegalStateException.class,
                "Secondary subscription occurred. producerState=STREAMING. connection=foobar");
        assertException(downstream, IllegalStateException.class,
                "Secondary subscription occurred. producerState=STREAMING. connection=foobar");
    }

    @Test
    public void passesOnReceivedContentChunksInStreamingState() {
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("ccc", UTF_8));
        producer.newChunk(copiedBuffer("ddd", UTF_8));

        assertThat(captureOnNext(), contains(copiedBuffer("ccc", UTF_8), copiedBuffer("ddd", UTF_8)));
    }

    @Test
    public void transitionsFromStreamingToCompletedStateWhenThereIsNoOutstandingEvents() {
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        verify(downstream).onComplete();
    }

    @Test
    public void emitsIllegalStateExceptionWhenAdditionalContentSubscriptionOccursInCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.onSubscribed(downstream);
        producer.newChunk(Unpooled.copiedBuffer("foobar", StandardCharsets.UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));

        producer.onSubscribed(additionalSubscriber);

        assertThat(logger.log(), hasItem(loggingEvent(WARN, "message=.Secondary content subscription.*")));

        assertException(additionalSubscriber, IllegalStateException.class,
                "Secondary subscription occurred. producerState=COMPLETED. connection=foobar");
    }

    @Test
    public void emitsIllegalStateExceptionWhenAdditionalContentSubscriptionOccursInTerminatedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.onSubscribed(downstream);
        producer.newChunk(Unpooled.copiedBuffer("foobar", StandardCharsets.UTF_8));
        producer.channelException(new RuntimeException("An exception occurred, doesn't matter what."));

        assertThat(producer.state(), is(TERMINATED));

        producer.onSubscribed(additionalSubscriber);

        assertThat(logger.log(), hasItem(loggingEvent(WARN, "message=.Secondary content subscription.*")));

        assertException(additionalSubscriber, IllegalStateException.class,
                "Secondary subscription occurred. producerState=TERMINATED. connection=foobar");
    }

    @Test
    public void transitionsFromStreamingToEmittingBufferedContentWhenThereAreOutstandingEvents() {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("ccc", UTF_8));
        producer.newChunk(copiedBuffer("ddd", UTF_8));
        verify(downstream, never()).onNext(any());

        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.request(2);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(copiedBuffer("ccc", UTF_8), copiedBuffer("ddd", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void honoursDownstreamBackPressureRequestsInStreamingState() {
        setUpAndRequest(1);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        assertThat(captureOnNext(), contains(copiedBuffer("chunk 1", UTF_8)));

        producer.request(2);
        assertThat(captureOnNext(), contains(copiedBuffer("chunk 1", UTF_8), copiedBuffer("chunk 2", UTF_8), copiedBuffer("chunk 3", UTF_8)));

        producer.request(1);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)));

        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        verify(downstream).onComplete();
    }

    @Test
    public void channelExceptionInStreamingState() {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);
        assertThat(producer.state(), is(STREAMING));

        producer.channelException(new RuntimeException("Something went wrong - simulated exception"));

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
        assertException(downstream, RuntimeException.class, "Something went wrong - simulated exception");
    }

    @Test
    public void styxClosesChannelInStreamingState() {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);
        assertThat(producer.state(), is(STREAMING));

        producer.channelInactive(transportLostCause);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        verify(onTerminateAction).accept(isA(Throwable.class));

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
        assertException(downstream, TransportLostException.class,
                "Connection to origin lost. origin=\"generic-app:anonymous-origin:localhost:8080\", remoteAddress=\"0.0.0.0/0.0.0.0:8080\".");
    }

    @Test
    public void unexpectedChannelClosureInStreamingState() {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);
        assertThat(producer.state(), is(STREAMING));

        producer.channelInactive(transportLostCause);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
        assertException(downstream, TransportLostException.class,
                "Connection to origin lost. origin=\"generic-app:anonymous-origin:localhost:8080\", remoteAddress=\"0.0.0.0/0.0.0.0:8080\".");
    }

    @Test
    public void releasesOfferedContentBufferInBufferingCompletedState() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        verify(downstream, never()).onNext(any());

        producer.newChunk(contentChunk1);

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
    }

    @Test
    public void transitionFromBufferingCompletedToEmittingBufferedContent() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        verify(downstream, never()).onNext(any());

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.request(1);

        assertThat(captureOnNext(), contains(copiedBuffer("blah", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void replaysBufferedContentWhenDownstreamSubscribesInBufferingCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        verify(downstream, never()).onNext(any());

        producer.onSubscribed(downstream);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(copiedBuffer("blah", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void channelExceptionInBufferingCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(contentChunk1);
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        producer.channelException(new RuntimeException("Someting went wrong - simulated exception"));

        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        producer.onSubscribed(downstream);
        producer.request(10);
        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void unexpectedChannelClosureInBufferingCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(contentChunk1);
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        producer.channelInactive(transportLostCause);

        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        // Keep the data available, since the HTTP Response has been fully received.
        producer.onSubscribed(downstream);
        producer.request(10);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(contentChunk1));
        verify(downstream).onComplete();
    }

    @Test
    public void tearDownInBufferingCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        producer.channelInactive(transportLostCause);
        producer.tearDownResources("test teardown");

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        ArgumentCaptor<ContentTimeoutException> argumentCaptor = ArgumentCaptor.forClass(ContentTimeoutException.class);
        verify(onTerminateAction).accept(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getMessage(), containsString("bytesReceived=6"));

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
    }

    @Test
    public void releasesSpuriousContentChunksInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("Buffer 1.", UTF_8));
        producer.newChunk(copiedBuffer("Buffer 2.", UTF_8));
        producer.newChunk(copiedBuffer("this gets held in EMITTING_BUFFERED_CONTENT", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.newChunk(contentChunk1);

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));
    }

    @Test
    public void releasesSpuriousContentChunksInCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));
        producer.newChunk(copiedBuffer("Buffer 1.", UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        producer.newChunk(contentChunk1);

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(producer.state(), is(COMPLETED));

        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void releasesSpuriousContentChunksInTerminatedState() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(contentChunk1);

        producer.channelException(new RuntimeException("This is a simulated exception"));
        assertThat(producer.state(), is(TERMINATED));
        assertThat(contentChunk1.refCnt(), is(0));

        producer.newChunk(contentChunk2);
        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(contentChunk2.refCnt(), is(0));
    }

    @Test
    public void doesNotAllowDoubleSubscriptionInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("Buffer 1.", UTF_8));
        producer.newChunk(copiedBuffer("Buffer 2.", UTF_8));
        producer.newChunk(copiedBuffer("this gets held in EMITTING_BUFFERED_CONTENT", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        // Note: This generates additional backpressure request() for content producer:
        producer.onSubscribed(additionalSubscriber);

        assertException(downstream, IllegalStateException.class,
                "Secondary subscription occurred. producerState=EMITTING_BUFFERED_CONTENT, connection=foobar");

        assertException(additionalSubscriber, IllegalStateException.class,
                "Content observable is already subscribed. producerState=EMITTING_BUFFERED_CONTENT, connection=foobar");

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
    }

    @Test
    public void ignoresAnyNewChunksInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("aaa", UTF_8));
        producer.newChunk(copiedBuffer("bbbb", UTF_8));
        verify(downstream, never()).onNext(any());

        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.newChunk(copiedBuffer("ccccc", UTF_8));
        producer.newChunk(copiedBuffer("dddddd", UTF_8));

        producer.request(10);
        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));

        assertThat(captureOnNext(), contains(copiedBuffer("aaa", UTF_8), copiedBuffer("bbbb", UTF_8)));
        verify(downstream).onComplete();
    }


    @Test
    public void channelExceptionInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.channelException(new RuntimeException("Someting went wrong - simulated exception"));

        // Keep the data available, since the HTTP Response has been fully received.
        producer.request(10);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(copiedBuffer("blah", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void styxClosesChannelInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.channelInactive(transportLostCause);

        // Keep the data available, since the HTTP Response has been fully received.
        producer.request(10);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(copiedBuffer("blah", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void UnexpectedChannelClosureInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.channelInactive(transportLostCause);

        // Keep the data available, since the HTTP Response has been fully received.
        producer.request(10);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(captureOnNext(), contains(copiedBuffer("blah", UTF_8)));
        verify(downstream).onComplete();
    }

    @Test
    public void tearDownInEmittingBufferedContentState() {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));


        producer.tearDownResources("test teardown");

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));

        verify(downstream, never()).onNext(any());
        ArgumentCaptor<Throwable> errorArg = ArgumentCaptor.forClass(Throwable.class);
        verify(downstream, atLeast(1)).onError(errorArg.capture());
        assertThat(errorArg.getValue(), is(instanceOf(ContentTimeoutException.class)));
    }



    /*
     *  1. subscribe
     *  2. empty last newChunk (lastHttpContent)
     *  3. newChunk
     *  4. notifySubscriber
     */
    @Test
    public void ignoresContentChunksAndContentEndEventsInCompletedState() {
        setUpAndRequest(NO_BACKPRESSURE);
        producer.onSubscribed(downstream);
        producer.lastHttpContent();
        assertThat(producer.state(), is(COMPLETED));

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        verify(downstream).onComplete();
        verify(downstream, never()).onNext(any());
    }


    @Test
    public void honoursDownstreamBackpressureRequestsInEmittingBufferedContentState() {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));

        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        verify(downstream, never()).onNext(any());

        producer.request(1);
        assertThat(captureOnNext(), contains(copiedBuffer("chunk 1", UTF_8)));

        producer.request(2);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8)
        ));

        producer.request(2);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)
        ));

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void providesNRequestedChunks() {
        setUpAndRequest(1);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        producer.newChunk(copiedBuffer("chunk 5", UTF_8));

        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8)));

        producer.request(3);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)
        ));

        producer.request(1);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8),
                copiedBuffer("chunk 5", UTF_8)
        ));

        producer.lastHttpContent();
        verify(downstream).onComplete();
        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void backpressureCanBeTurnedOffMidStream() {
        setUpAndRequest(1);

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        producer.newChunk(copiedBuffer("chunk 5", UTF_8));
        producer.lastHttpContent();

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8)
        ));

        producer.request(Long.MAX_VALUE);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8),
                copiedBuffer("chunk 5", UTF_8)
        ));

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void backpressureCanBeTurnedBackOnMidStream() {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8)
        ));

        producer.request(0);

        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));

        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8)
        ));

        producer.request(2);
        assertThat(captureOnNext(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)
        ));
    }


    /*
     * Flow Control
     */

    @Test
    public void requestN0EventInBufferingState() {
        setUpAndRequest(0);

        verify(askForMore).run();
    }

    @Test
    public void requestN5EventInBufferingState() {
        setUpAndRequest(5);

        verify(askForMore).run();
    }

    @Test
    public void requestEventInBufferingState() {
        // Enable channel if queue depth is zero
        setUpAndRequest(0);

        verify(askForMore).run();

        producer.newChunk(contentChunk1);
        verify(askForMore, times(1)).run();

        producer.request(1);
        verify(askForMore, times(1)).run();
    }

    @Test
    public void contentEventInBufferingState() {
        setUpAndRequest(5);

        verify(askForMore).run();

        producer.newChunk(contentChunk1);
        verify(askForMore, times(1)).run();

        producer.newChunk(contentChunk2);
        verify(askForMore, times(1)).run();
    }

    @Test
    public void requestInBufferingCompletedState() {
        setUpAndRequest(5);

        producer.lastHttpContent();
        assertEquals(producer.state(), BUFFERING_COMPLETED);

        producer.request(5);
        verify(askForMore).run();
    }

    @Test
    public void requestInStreamingState() {
        setUpAndRequest(0);
        verify(askForMore).run();

        producer.onSubscribed(downstream);

        assertEquals(producer.state(), STREAMING);
        verify(askForMore, times(2)).run();

        producer.request(5);
        verify(askForMore, times(3)).run();
    }

    @Test
    public void requestInStreamingState2() {
        // Enables channel when queue depth is zero

        setUpAndRequest(0);
        verify(askForMore).run();

        producer.onSubscribed(downstream);
        assertEquals(producer.state(), STREAMING);

        producer.newChunk(contentChunk1);
        verify(askForMore, times(2)).run();

        producer.newChunk(contentChunk2);
        verify(askForMore, times(2)).run();

        producer.request(1);
        verify(askForMore, times(2)).run();

        // Read Queue depth drops down to zero:
        producer.request(1);
        verify(askForMore, times(3)).run();
    }

    @Test
    public void contentInStreamingState() {
        // Keeps channel disabled when requested count is zero

        setUpAndRequest(0);
        verify(askForMore).run();

        // Queue depth is zero, so this will trigger another request.
        producer.onSubscribed(downstream);

        assertEquals(producer.state(), STREAMING);
        verify(askForMore, times(2)).run();

        producer.newChunk(contentChunk1);
        verify(askForMore, times(2)).run();

        producer.newChunk(contentChunk2);
        verify(askForMore, times(2)).run();
    }

    @Test
    public void contentInStreamingState2() {
        // Enables channel when queue depth remains at zero

        setUpAndRequest(1);
        verify(askForMore).run();

        producer.onSubscribed(downstream);

        assertEquals(producer.state(), STREAMING);
        verify(askForMore, times(2)).run();

        producer.newChunk(contentChunk1);
        verify(askForMore, times(3)).run();

        producer.newChunk(contentChunk2);
        verify(askForMore, times(3)).run();
    }

    private long getPercentageOfValue(int percentage, long value) {
        return (value * percentage) / 100;
    }

    private <T> void assertException(Subscriber subscriber, Class<T> klass, String message) {
        ArgumentCaptor<Throwable> errorArg = ArgumentCaptor.forClass(Throwable.class);
        verify(subscriber, atLeast(1)).onError(errorArg.capture());
        assertThat(errorArg.getValue(), is(instanceOf(klass)));
        assertThat(errorArg.getValue().getMessage(), is(message));
    }

    private List<ByteBuf> captureOnNext() {
        ArgumentCaptor<ByteBuf> responseArg = ArgumentCaptor.forClass(ByteBuf.class);
        verify(downstream, atLeast(1)).onNext(responseArg.capture());
        return responseArg.getAllValues();
    }
}