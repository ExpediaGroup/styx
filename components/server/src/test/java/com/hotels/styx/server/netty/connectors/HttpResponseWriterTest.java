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
package com.hotels.styx.server.netty.connectors;


import ch.qos.logback.classic.Level;
import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.exceptions.TransportLostException;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.logging.LoggingHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.subjects.PublishSubject;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.api.Buffers.toByteBuf;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;
import static java.net.InetAddress.getLoopbackAddress;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static rx.RxReactiveStreams.toPublisher;

public class HttpResponseWriterTest {
    private LoggingTestSupport LOGGER;

    private PublishSubject<Buffer> contentObservable;
    private Queue<ChannelWriteArguments> channelArgs;
    private AtomicBoolean channelRead;

    @BeforeMethod
    public void setUp() {
        LOGGER = new LoggingTestSupport(HttpResponseWriter.class);
        contentObservable = PublishSubject.create();
        channelArgs = new ArrayDeque<>();
        channelRead = new AtomicBoolean(false);
    }

    @AfterMethod
    public void tearDown() {
        LOGGER.stop();
    }

    @Test
    public void completesFutureOnlyAfterContentObservableIsCompleted() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);
                        CompletableFuture<Void> future = writer.write(response);
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("aaa", UTF_8));
                        assertThat(future.isDone(), is(false));

                        contentObservable.onCompleted();
                        assertThat(future.isDone(), is(true));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
        assertThat(channelRead.get(), is(true));
    }

    @Test
    public void completesFutureOnlyAfterAllWritesAreSuccessfullyCompleted() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                new LoggingHandler(),
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);
                        CompletableFuture<Void> future = writer.write(response);
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("aaa", UTF_8));
                        assertThat(future.isDone(), is(false));

                        contentObservable.onCompleted();
                        assertThat(future.isDone(), is(false));

                        writeAck(channelArgs);  // For response headers
                        writeAck(channelArgs);  // For content chunk
                        writeAck(channelArgs);  // For EMPTY_LAST_CHUNK
                        assertThat(future.isDone(), is(true));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
        assertThat(channelRead.get(), is(true));
    }

    @Test
    public void ignoresLastEmptyHttpContentWriteOutcome() throws Exception {
        /*
         * It is necessary to ignore outcome of LastEmptyHttpContent.
         * This is because the full response would have been already sent,
         * and the remote end may have closed the connection before LastEmptyHttpContent
         * would have been written. This would result in an unnecessary
         * rejection of response writer future, even when the response in
         * fact was correctly sent. The following diagram illustrates the
         * scenario:
         *
         * 1. Styx HTTP Response writer writes the headers.
         *
         * 2. Styx HTTP Response writer writes the remaining content.
         *
         * 3. Remote receives the full response and closes the connection.
         *
         * 4. Styx HTTP Response Writer attempts to write the last empty HTTP
         *    content chunk. This will now fail because the TCP connection has
         *    closed.
         *
         * 5. HttpResponseWriter future completes unsuccessfully.
         *
         */
        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                new LoggingHandler(),
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);

                        CompletableFuture<Void> future = writer.write(response);
                        writeAck(channelArgs);  // For response headers
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("aaa", UTF_8));
                        writeAck(channelArgs);  // For content chunk
                        assertThat(future.isDone(), is(false));

                        contentObservable.onCompleted();
                        writeError(channelArgs);  // For EMPTY_LAST_CHUNK

                        assertThat(future.isDone(), is(true));
                        assertThat(future.isCompletedExceptionally(), is(false));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
        assertThat(channelRead.get(), is(true));
    }

    @Test(expectedExceptions = ExecutionException.class)
    public void failsTheResultWhenResponseWriteFails() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);
                        CompletableFuture<Void> future = writer.write(response);
                        assertThat(future.isDone(), is(false));
                        writeError(channelArgs);

                        assertThat(future.isDone(), is(true));
                        future.get(200, MILLISECONDS);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
    }


    @Test(expectedExceptions = ExecutionException.class)
    public void failsTheResultWhenContentWriteFails() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);
                        CompletableFuture<Void> future = writer.write(response);

                        writeAck(channelArgs);
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("aaa", UTF_8));
                        assertThat(future.isDone(), is(false));

                        contentObservable.onCompleted();
                        assertThat(future.isDone(), is(false));

                        writeError(channelArgs);

                        assertThat(future.isDone(), is(true));
                        future.get(200, MILLISECONDS);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
    }

    @Test
    public void sendsEmptyLastHttpContentWhenContentObservableCompletes() throws Exception {
        CaptureHttpResponseWriteEventsHandler writeEventsCollector = new CaptureHttpResponseWriteEventsHandler();

        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                writeEventsCollector,
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);

                        CompletableFuture<Void> future = writer.write(response);
                        writeAck(channelArgs);
                        assertThat(future.isDone(), is(false));

                        contentObservable.onCompleted();
                        assertThat(future.isDone(), is(false));

                        writeAck(channelArgs);
                        assertThat(future.isDone(), is(true));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());
        assertThat(channelRead.get(), is(true));

        List<Object> writeEvents = writeEventsCollector.writeEvents();

        assertThat(writeEvents.get(0), instanceOf(DefaultHttpResponse.class));
        assertThat(writeEvents.get(1), is(EMPTY_LAST_CONTENT));
    }

    @Test
    public void unsubscribesFromContentWhenCancelled() throws Exception {
        CaptureHttpResponseWriteEventsHandler writeEventsCollector = new CaptureHttpResponseWriteEventsHandler();

        AtomicBoolean unsubscribed = new AtomicBoolean(false);

        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                writeEventsCollector,
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);

                        CompletableFuture<Void> future = writer.write(response);
                        writeAck(channelArgs);
                        assertThat(future.isDone(), is(false));

                        future.cancel(false);
                        assertThat(unsubscribed.get(), is(true));
                        assertThat(future.isDone(), is(true));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable.doOnUnsubscribe(() -> unsubscribed.set(true))))).build());
        assertThat(channelRead.get(), is(true));
    }

    @Test
    public void releasesContentChunksWhenFailsToConvertToNettyHeaders() throws Exception {
        CaptureHttpResponseWriteEventsHandler writeEventsCollector = new CaptureHttpResponseWriteEventsHandler();

        Buffer chunk1 = new Buffer("aaa", UTF_8);
        Buffer chunk2 = new Buffer("aaa", UTF_8);
        AtomicBoolean unsubscribed = new AtomicBoolean(false);

        EmbeddedChannel ch = new EmbeddedChannel(
                new CaptureChannelArgumentsHandler(channelArgs),
                writeEventsCollector,
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx, httpResponse -> {
                            throw new RuntimeException();
                        });

                        CompletableFuture<Void> future = writer.write(response);

                        contentObservable.onNext(chunk1);
                        contentObservable.onNext(chunk2);
                        contentObservable.onCompleted();

                        assertThat(future.isDone(), is(true));
                        assertThat(toByteBuf(chunk1).refCnt(), is(0));
                        assertThat(toByteBuf(chunk2).refCnt(), is(0));

                        channelRead.set(true);
                    }
                }
        );

        LiveHttpResponse.Builder response = response(OK).cookies(responseCookie(",,,,", ",,,,").build());
        ch.writeInbound(response.body(new ByteStream(toPublisher(contentObservable.doOnUnsubscribe(() -> unsubscribed.set(true))))).build());
        assertThat(channelRead.get(), is(true));
    }

    @Test
    public void logsSentAndAcknowledgedBytes() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new SimpleChannelInboundHandler<LiveHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, LiveHttpResponse response) throws Exception {
                        HttpResponseWriter writer = new HttpResponseWriter(ctx);
                        CompletableFuture<Void> future = writer.write(response);
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("aaa", UTF_8));
                        assertThat(future.isDone(), is(false));

                        contentObservable.onNext(new Buffer("bbbb", UTF_8));
                        assertThat(future.isDone(), is(false));

                        contentObservable.onError(new TransportLostException(
                                new InetSocketAddress(getLoopbackAddress(), 5050),
                                newOriginBuilder("localhost", 5050).build()));
                        assertThat(future.isDone(), is(true));

                        channelRead.set(true);
                    }
                }
        );

        ch.writeInbound(response(OK).body(new ByteStream(toPublisher(contentObservable))).build());

        assertThat(LOGGER.lastMessage(), is(
                loggingEvent(
                        Level.WARN,
                        "Content observable error. Written content bytes 7/7 \\(ackd/sent\\). Write events 3/3 \\(ackd/writes\\).*",
                        TransportLostException.class,
                        "Connection to origin lost. origin=\"generic-app:anonymous-origin:localhost:5050\", remoteAddress=\"localhost/127.0.0.1:5050.*")));
    }

    @Test(enabled = false)
    public void releasesUnsentContentBuffersAfterHeaderWriteFailure() throws Exception {
        /*
         * Note: The previous OldHttpResponseWriter did not release content buffers either.
         * Going forward, design a better error handling strategy for Styx. However at the
         * moment this haven't had any adverse effect in production.
         */
    }

    @Test(enabled = false)
    public void releasesUnsentContentBuffersAfterContentObservableFailure() throws Exception {
        /*
         * Note: The previous OldHttpResponseWriter did not release content buffers either.
         * Going forward, design a better error handling strategy for Styx. However at the
         * moment this haven't had any adverse effect in production.
         */
    }

    @Test(enabled = false)
    public void releasesUnsentContentBuffersAfterContentWriteFailure() throws Exception {
        /*
         * Note: The previous OldHttpResponseWriter did not release content buffers either.
         * Going forward, design a better error handling strategy for Styx. However at the
         * moment this haven't had any adverse effect in production.
         */
    }

    private static void writeError(Queue<ChannelWriteArguments> channelOpQueue) {
        ChannelWriteArguments args = channelOpQueue.remove();
        args.promise().setFailure(new RuntimeException("Simulated failure"));
    }

    private static void writeAck(Queue<ChannelWriteArguments> channelOpQueue) {
        ChannelWriteArguments args = channelOpQueue.remove();
        args.promise().setSuccess();
    }

    private class ChannelWriteArguments {
        private ChannelHandlerContext ctx;
        private Object msg;
        private ChannelPromise promise;

        ChannelWriteArguments(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            this.ctx = ctx;
            this.msg = msg;
            this.promise = promise;
        }


        public ChannelHandlerContext ctx() {
            return ctx;
        }

        public Object msg() {
            return msg;
        }

        ChannelPromise promise() {
            return promise;
        }
    }

    private class CaptureChannelArgumentsHandler extends ChannelOutboundHandlerAdapter {
        private final Queue<ChannelWriteArguments> channelArguments;

        CaptureChannelArgumentsHandler(Queue<ChannelWriteArguments> argumentQueue) {
            this.channelArguments = argumentQueue;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            channelArguments.add(new ChannelWriteArguments(ctx, msg, promise));
        }
    }

    private class CaptureHttpResponseWriteEventsHandler extends ChannelOutboundHandlerAdapter {
        private final List<Object> writeEvents = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            writeEvents.add(msg);
            super.write(ctx, msg, promise);
        }

        public List<Object> writeEvents() {
            return ImmutableList.copyOf(writeEvents);
        }

    }
}