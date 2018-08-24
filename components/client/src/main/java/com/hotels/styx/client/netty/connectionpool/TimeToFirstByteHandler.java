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

import com.hotels.styx.api.Clock;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;

import static com.hotels.styx.api.Clocks.systemClock;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A netty channel handler that provides the {@link TimeToFirstByteListener} with a measurement of time-to-first-byte in milliseconds.
 */
class TimeToFirstByteHandler extends ChannelDuplexHandler {
    private final TimeToFirstByteListener timeToFirstByteListener;

    private boolean firstChunkReceived;
    private long startTimeMs;
    private final Clock clock;

    TimeToFirstByteHandler(TimeToFirstByteListener timeToFirstByteListener) {
        this(timeToFirstByteListener, systemClock());
    }

    TimeToFirstByteHandler(TimeToFirstByteListener timeToFirstByteListener, Clock clock) {
        this.timeToFirstByteListener = requireNonNull(timeToFirstByteListener);
        this.clock = requireNonNull(clock);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpContent && !this.firstChunkReceived) {
            timeToFirstByteListener.notifyTimeToFirstByte(clock.tickMillis() - startTimeMs, MILLISECONDS);
            firstChunkReceived = true;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpRequest) {
            startTimeMs = clock.tickMillis();
            firstChunkReceived = false;
        }
        super.write(ctx, msg, promise);
    }
}
