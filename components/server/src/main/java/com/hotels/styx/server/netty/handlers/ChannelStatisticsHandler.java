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
package com.hotels.styx.server.netty.handlers;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.hotels.styx.api.MetricRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;
import static java.lang.String.format;

/**
 * Collects statistics about channels.
 */
@ChannelHandler.Sharable
public class ChannelStatisticsHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelStatisticsHandler.class);

    public static final String PREFIX = "connections";

    public static final String RECEIVED_BYTES = "bytes-received";
    public static final String SENT_BYTES = "bytes-sent";
    public static final String TOTAL_CONNECTIONS = "total-connections";

    private final MetricRegistry metricRegistry;
    private final Counter receivedBytesCount;
    private final Counter sentBytesCount;
    private final Counter totalConnections;

    public ChannelStatisticsHandler(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry.scope(PREFIX);

        this.receivedBytesCount = this.metricRegistry.counter(RECEIVED_BYTES);
        this.sentBytesCount = this.metricRegistry.counter(SENT_BYTES);
        this.totalConnections = this.metricRegistry.counter(TOTAL_CONNECTIONS);
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
        Counter channelCount = this.metricRegistry.counter(name(counterPrefix(thread), "registered-channel-count"));
        channelCount.inc(amount);

        Histogram histogram = metricRegistry.histogram(name(counterPrefix(thread), "channels"));
        histogram.update(channelCount.getCount());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        totalConnections.inc();

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        totalConnections.dec();

        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            receivedBytesCount.inc(((ByteBuf) msg).readableBytes());
        } else if (msg instanceof ByteBufHolder) {
            receivedBytesCount.inc(((ByteBufHolder) msg).content().readableBytes());
        } else {
            LOGGER.warn(format("channelRead(): Expected byte buffers, but got [%s]", msg));
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            sentBytesCount.inc(((ByteBuf) msg).readableBytes());
        } else if (msg instanceof ByteBufHolder) {
            sentBytesCount.inc(((ByteBufHolder) msg).content().readableBytes());
        }
        super.write(ctx, msg, promise);
    }

    private static String counterPrefix(Thread thread) {
        return name("eventloop", thread.getName());
    }
}
