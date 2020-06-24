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
package com.hotels.styx.server.netty.handlers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static com.hotels.styx.api.Metrics.name;
import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Collects statistics about channels.
 */
@ChannelHandler.Sharable
public class ChannelStatisticsHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = getLogger(ChannelStatisticsHandler.class);

    public static final String EVENTLOOP_TAG = "eventloop";

    public static final String BYTES_RECEIVED = "connection.bytes-received";
    public static final String BYTES_SENT = "connection.bytes-sent";
    public static final String TOTAL_CONNECTIONS = "connection.total-connections";
    public static final String REGISTERED_CHANNEL_COUNT = "connection.registered-channel-count";
    public static final String CHANNELS_SUMMARY = "connection.channels";


    private final MeterRegistry meterRegistry;
    private final String prefix;

    private final Counter receivedBytesCount;
    private final Counter sentBytesCount;
    private final AtomicLong totalConnections;

    public ChannelStatisticsHandler(MeterRegistry meterRegistry, String meterPrefix) {
        this.meterRegistry = meterRegistry;
        this.prefix = meterPrefix;

        this.receivedBytesCount = this.meterRegistry.counter(name(meterPrefix, BYTES_RECEIVED));
        this.sentBytesCount = this.meterRegistry.counter(name(meterPrefix, BYTES_SENT));
        this.totalConnections = this.meterRegistry.gauge(name(meterPrefix, TOTAL_CONNECTIONS), new AtomicLong());
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
        Thread thread = Thread.currentThread();
        Counter channelCount = this.meterRegistry
                .counter(name(prefix, REGISTERED_CHANNEL_COUNT), counterTags(thread));
        channelCount.increment(amount);

        DistributionSummary channels = meterRegistry.summary(name(prefix, CHANNELS_SUMMARY), counterTags(thread));
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

    private static Tags counterTags(Thread thread) {
        return Tags.of(EVENTLOOP_TAG, thread.getName());
    }
}
