/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.server.netty.handlers;

import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Collects statistics about channels.
 */
@ChannelHandler.Sharable
public class ChannelStatisticsHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = getLogger(ChannelStatisticsHandler.class);

    private final CentralisedMetrics metrics;
    private final Counter receivedBytesCount;
    private final Counter sentBytesCount;
    private final AtomicLong totalConnections;

    public ChannelStatisticsHandler(CentralisedMetrics metrics) {
        this.metrics = requireNonNull(metrics);

        this.receivedBytesCount = metrics.proxy().server().bytesReceived();
        this.sentBytesCount = metrics.proxy().server().bytesSent();

        this.totalConnections = new AtomicLong();

        metrics.proxy().server().totalConnections().register(totalConnections);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        updateChannelPerThreadCounters(1);
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        updateChannelPerThreadCounters(-1);
        super.channelUnregistered(ctx);
    }

    private void updateChannelPerThreadCounters(int amount) {
        Thread thread = currentThread();
        Counter channelCount = metrics.proxy().server().registeredChannelCount(thread);
        channelCount.increment(amount);

        DistributionSummary channels = metrics.proxy().server().channelCount(thread);
        channels.record(channelCount.count());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        totalConnections.incrementAndGet();

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        totalConnections.decrementAndGet();

        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            receivedBytesCount.increment(((ByteBuf) msg).readableBytes());
        } else if (msg instanceof ByteBufHolder) {
            receivedBytesCount.increment(((ByteBufHolder) msg).content().readableBytes());
        } else {
            LOGGER.warn(format("channelRead(): Expected byte buffers, but got [%s]", msg));
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            sentBytesCount.increment(((ByteBuf) msg).readableBytes());
        } else if (msg instanceof ByteBufHolder) {
            sentBytesCount.increment(((ByteBufHolder) msg).content().readableBytes());
        }
        super.write(ctx, msg, promise);
    }
}
