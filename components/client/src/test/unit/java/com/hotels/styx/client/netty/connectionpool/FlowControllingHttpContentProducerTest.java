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

import com.hotels.styx.api.exceptions.ResponseTimeoutException;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.client.netty.ConsumerDisconnectedException;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.BUFFERING;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.BUFFERING_COMPLETED;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.COMPLETED;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.EMITTING_BUFFERED_CONTENT;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.STREAMING;
import static com.hotels.styx.client.netty.connectionpool.FlowControllingHttpContentProducer.ProducerState.TERMINATED;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


public class FlowControllingHttpContentProducerTest {
    private static final Long NO_BACKPRESSURE = Long.MAX_VALUE;
    private TestSubscriber<? super ByteBuf> downstream;
    private TestSubscriber<? super ByteBuf> additionalSubscriber;
    private FlowControllingHttpContentProducer producer;
    private Runnable askForMore;
    private Runnable onCompleteAction;
    private Consumer<Throwable> onTerminateAction;
    private Runnable tearDownAction;
    private ByteBuf contentChunk1;
    private ByteBuf contentChunk2;
    private TransportLostException transportLostCause = new TransportLostException(new InetSocketAddress(8080), newOriginBuilder("localhost", 8080).build());
    private LoggingTestSupport logger;

    public void setUpAndRequest(long initialCount) {
        downstream = new TestSubscriber<>(initialCount);
        additionalSubscriber = new TestSubscriber<>(initialCount);
        askForMore = mock(Runnable.class);
        onCompleteAction = mock(Runnable.class);
        onTerminateAction = mock(Consumer.class);
        tearDownAction = mock(Runnable.class);

        producer = new FlowControllingHttpContentProducer(
                askForMore,
                onCompleteAction,
                onTerminateAction,
                tearDownAction,
                "foobar",
                newOriginBuilder("foohost", 12345).build());

        producer.request(initialCount);
    }

    @BeforeMethod
    public void setUp() throws Exception {
        contentChunk1 = copiedBuffer("aaa", UTF_8);
        contentChunk2 = copiedBuffer("bbb", UTF_8);
        logger = new LoggingTestSupport(FlowControllingHttpContentProducer.class);
    }

    @AfterMethod
    public void tearDown() throws Exception {
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
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("aaa", UTF_8), copiedBuffer("bbb", UTF_8)));
    }


    @Test
    public void transitionFromBufferingToBufferingCompletedState() throws Exception {
        // Last HTTP Content event will trigger a transition to BUFFERING_COMPLETED state
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.newChunk(copiedBuffer("aaa", UTF_8));
        producer.newChunk(copiedBuffer("bbb", UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));
    }


    @Test
    public void handlesContentUnsubscriptionWhenStreaming() throws Exception {
        // On Subscribe, transition from buffering to streaming state
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        ByteBuf firstChunk = copiedBuffer("aaa", UTF_8);
        producer.newChunk(firstChunk);
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.onSubscribed(downstream);

        assertThat(producer.state(), is(STREAMING));
        assertThat(downstream.getOnNextEvents(), is(empty()));
        producer.unsubscribe();

        ByteBuf secondChunk = copiedBuffer("bbbb", UTF_8);
        producer.newChunk(secondChunk);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(downstream.getOnNextEvents(), is(empty()));
        downstream.assertError(ConsumerDisconnectedException.class);
        assertThat(firstChunk.refCnt(), is(0));
        assertThat(secondChunk.refCnt(), is(0));
    }

    @Test
    public void handlesContentUnsubscriptionWhenEmitting() throws Exception {
        // On Subscribe, transition from buffering to streaming state
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        ByteBuf firstChunk = copiedBuffer("aaa", UTF_8);

        producer.newChunk(firstChunk);
        assertThat(downstream.getOnNextEvents(), is(emptyList()));
        producer.lastHttpContent();

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.unsubscribe();

        ByteBuf secondChunk = copiedBuffer("bbbb", UTF_8);
        producer.newChunk(secondChunk);

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
        assertThat(downstream.getOnNextEvents(), is(empty()));
        assertException(downstream.getOnErrorEvents().get(0), Throwable.class, "The consumer unsubscribed. connection=foobar producerState=EMITTING_BUFFERED_CONTENT");
        assertThat(firstChunk.refCnt(), is(0));
        assertThat(secondChunk.refCnt(), is(0));
    }


    @Test
    public void channelExceptionInBufferingState() throws Exception {
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
    public void styxClosesChannelWhileInBufferingState() throws Exception {
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
    public void channelUnexpectedlyTerminatesInBufferingState() throws Exception {
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
        assertException(getCause(downstream), IllegalStateException.class,
                "Secondary subscription occurred. producerState=STREAMING. connection=foobar");
        assertException(getCause(additionalSubscriber), IllegalStateException.class,
                "Secondary subscription occurred. producerState=STREAMING. connection=foobar");
    }

    @Test
    public void passesOnReceivedContentChunksInStreamingState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("ccc", UTF_8));
        producer.newChunk(copiedBuffer("ddd", UTF_8));

        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("ccc", UTF_8), copiedBuffer("ddd", UTF_8)));
    }

    @Test
    public void transitionsFromStreamingToCompletedStateWhenThereIsNoOutstandingEvents() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void emitsIllegalStateExceptionWhenAdditionalContentSubscriptionOccursInCompletedState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.onSubscribed(downstream);
        producer.newChunk(Unpooled.copiedBuffer("foobar", StandardCharsets.UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));

        producer.onSubscribed(additionalSubscriber);

        assertThat(logger.log(), hasItem(loggingEvent(WARN, "message=.Secondary content subscription.*")));

        assertException(getCause(additionalSubscriber), IllegalStateException.class,
                "Secondary subscription occurred. producerState=COMPLETED. connection=foobar");
    }

    @Test
    public void emitsIllegalStateExceptionWhenAdditionalContentSubscriptionOccursInTerminatedState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.onSubscribed(downstream);
        producer.newChunk(Unpooled.copiedBuffer("foobar", StandardCharsets.UTF_8));
        producer.channelException(new RuntimeException("An exception occurred, doesn't matter what."));

        assertThat(producer.state(), is(TERMINATED));

        producer.onSubscribed(additionalSubscriber);

        assertThat(logger.log(), hasItem(loggingEvent(WARN, "message=.Secondary content subscription.*")));

        assertException(getCause(additionalSubscriber), IllegalStateException.class,
                "Secondary subscription occurred. producerState=TERMINATED. connection=foobar");
    }

    @Test
    public void transitionsFromStreamingToEmittingBufferedContentWhenThereAreOutstandingEvents() throws Exception {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("ccc", UTF_8));
        producer.newChunk(copiedBuffer("ddd", UTF_8));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.request(2);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("ccc", UTF_8), copiedBuffer("ddd", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void honoursDownstreamBackPressureRequestsInStreamingState() throws Exception {
        setUpAndRequest(1);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("chunk 1", UTF_8)));

        producer.request(2);
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("chunk 1", UTF_8), copiedBuffer("chunk 2", UTF_8), copiedBuffer("chunk 3", UTF_8)));

        producer.request(1);
        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)));

        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void channelExceptionInStreamingState() throws Exception {
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
        assertException(getCause(downstream), RuntimeException.class, "Something went wrong - simulated exception");
    }

    @Test
    public void styxClosesChannelInStreamingState() throws Exception {
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
        assertException(getCause(downstream), TransportLostException.class,
                "Connection to origin lost. origin=\"generic-app:anonymous-origin:localhost:8080\", remoteAddress=\"0.0.0.0/0.0.0.0:8080\".");
    }

    @Test
    public void unexpectedChannelClosureInStreamingState() throws Exception {
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
        assertException(getCause(downstream), TransportLostException.class,
                "Connection to origin lost. origin=\"generic-app:anonymous-origin:localhost:8080\", remoteAddress=\"0.0.0.0/0.0.0.0:8080\".");
    }

    @Test
    public void releasesOfferedContentBufferInBufferingCompletedState() throws Exception {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.newChunk(contentChunk1);

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
    }

    @Test
    public void transitionFromBufferingCompletedToEmittingBufferedContent() throws Exception {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.request(1);

        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("blah", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void replaysBufferedContentWhenDownstreamSubscribesInBufferingCompletedState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.onSubscribed(downstream);

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("blah", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void channelExceptionInBufferingCompletedState() throws Exception {
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
    public void UnexpectedChannelClosureInBufferingCompletedState() throws Exception {
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
        assertThat(downstream.getOnNextEvents(), contains(contentChunk1));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void delayedTearDownInBufferingCompletedState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(contentChunk1);
        producer.newChunk(contentChunk2);
        producer.lastHttpContent();
        assertThat(producer.state(), is(BUFFERING_COMPLETED));

        producer.channelInactive(transportLostCause);
        producer.tearDownResources();

        verify(tearDownAction).run();
        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        ArgumentCaptor<ResponseTimeoutException> argumentCaptor = ArgumentCaptor.forClass(ResponseTimeoutException.class);
        verify(onTerminateAction).accept(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getMessage(), containsString("bytesReceived=6"));

        assertThat(contentChunk1.refCnt(), is(0));
        assertThat(contentChunk2.refCnt(), is(0));
    }

    @Test
    public void releasesSpuriousContentChunksInEmittingBufferedContentState() throws Exception {
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
    public void releasesSpuriousContentChunksInCompletedState() throws Exception {
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
    public void releasesSpuriousContentChunksInTerminatedState() throws Exception {
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
        TestSubscriber<? super ByteBuf> downstream2 = new TestSubscriber<>(1);
        producer.onSubscribed(downstream2);

        assertException(getCause(downstream),
                IllegalStateException.class,
                "Secondary subscription occurred. producerState=EMITTING_BUFFERED_CONTENT, connection=foobar");

        assertException(getCause(downstream2),
                IllegalStateException.class,
                "Content observable is already subscribed. producerState=EMITTING_BUFFERED_CONTENT, connection=foobar");

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));
    }

    @Test
    public void ignoresAnyNewChunksInEmittingBufferedContentState() throws Exception {
        setUpAndRequest(0);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("aaa", UTF_8));
        producer.newChunk(copiedBuffer("bbbb", UTF_8));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.lastHttpContent();
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        producer.newChunk(copiedBuffer("ccccc", UTF_8));
        producer.newChunk(copiedBuffer("dddddd", UTF_8));

        producer.request(10);
        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));

        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("aaa", UTF_8), copiedBuffer("bbbb", UTF_8)));
        assertThat(downstream.getOnCompletedEvents().size(), is(1));
    }


    @Test
    public void channelExceptionInEmittingBufferedContentState() throws Exception {
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
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("blah", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void styxClosesChannelInEmittingBufferedContentState() throws Exception {
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
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("blah", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void UnexpectedChannelClosureInEmittingBufferedContentState() throws Exception {
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
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("blah", UTF_8)));
        assertThat(hasCompleted(downstream), is(true));
    }

    @Test
    public void delayedTearDownInEmittingBufferedContentState() throws Exception {
        setUpAndRequest(0);

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));


        producer.tearDownResources();

        assertThat(producer.state(), is(TERMINATED));
        verify(onCompleteAction, never()).run();
        verify(onTerminateAction).accept(isA(Throwable.class));

        assertThat(downstream.getOnNextEvents(), is(emptyList()));
        assertThat(getCause(downstream), is(instanceOf(ResponseTimeoutException.class)));
    }



    /*
     *  1. subscribe
     *  2. empty last newChunk (lastHttpContent)
     *  3. newChunk
     *  4. notifySubscriber
     */
    @Test
    public void ignoresContentChunksAndContentEndEventsInCompletedState() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);
        producer.onSubscribed(downstream);
        producer.lastHttpContent();
        assertThat(producer.state(), is(COMPLETED));

        producer.newChunk(copiedBuffer("blah", UTF_8));
        producer.lastHttpContent();

        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
        assertThat(downstream.getOnCompletedEvents().size(), is(1));
        assertThat(downstream.getOnNextEvents(), is(emptyList()));
    }


    @Test
    public void honoursDownstreamBackpressureRequestsInEmittingBufferedContentState() throws Exception {
        setUpAndRequest(0);
        assertThat(producer.state(), is(BUFFERING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));

        producer.lastHttpContent();
        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        assertThat(downstream.getOnNextEvents(), is(emptyList()));

        producer.request(1);
        assertThat(downstream.getOnNextEvents(), contains(copiedBuffer("chunk 1", UTF_8)));

        producer.request(2);
        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8)
        ));

        producer.request(2);
        assertThat(downstream.getOnNextEvents(), contains(
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
    public void providesNRequestedChunks() throws Exception {
        setUpAndRequest(1);

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        producer.newChunk(copiedBuffer("chunk 5", UTF_8));

        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8)));

        producer.request(3);
        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)
        ));

        producer.request(1);
        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8),
                copiedBuffer("chunk 5", UTF_8)
        ));

        producer.lastHttpContent();
        assertThat(downstream.getOnCompletedEvents().size(), is(1));
        assertThat(producer.state(), is(COMPLETED));
        verify(onCompleteAction).run();
        verify(onTerminateAction, never()).accept(isA(Throwable.class));
    }

    @Test
    public void backpressureCanBeTurnedOffMidStream() throws Exception {
        setUpAndRequest(1);

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));
        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));
        producer.newChunk(copiedBuffer("chunk 5", UTF_8));
        producer.lastHttpContent();

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(EMITTING_BUFFERED_CONTENT));

        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8)
        ));

        producer.request(Long.MAX_VALUE);
        assertThat(downstream.getOnNextEvents(), contains(
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
    public void backpressureCanBeTurnedBackOnMidStream() throws Exception {
        setUpAndRequest(NO_BACKPRESSURE);

        producer.newChunk(copiedBuffer("chunk 1", UTF_8));
        producer.newChunk(copiedBuffer("chunk 2", UTF_8));

        producer.onSubscribed(downstream);
        assertThat(producer.state(), is(STREAMING));

        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8)
        ));

        producer.request(0);

        producer.newChunk(copiedBuffer("chunk 3", UTF_8));
        producer.newChunk(copiedBuffer("chunk 4", UTF_8));

        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8)
        ));

        producer.request(2);
        assertThat(downstream.getOnNextEvents(), contains(
                copiedBuffer("chunk 1", UTF_8),
                copiedBuffer("chunk 2", UTF_8),
                copiedBuffer("chunk 3", UTF_8),
                copiedBuffer("chunk 4", UTF_8)
        ));
    }

    public <T> void assertException(Throwable cause, Class<T> klass, String message) {
        assertThat(cause, is(instanceOf(klass)));
        assertThat(cause.getMessage(), is(message));
    }

    private Throwable getCause(TestSubscriber<? super ByteBuf> downstream) {
        return downstream.getOnErrorEvents().get(0);
    }

    private boolean hasCompleted(TestSubscriber<? super ByteBuf> downstream) {
        return downstream.getOnCompletedEvents().size() == 1;
    }

}