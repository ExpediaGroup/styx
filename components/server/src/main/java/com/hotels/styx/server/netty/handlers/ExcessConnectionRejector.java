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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Rejects connections once the number reaches the maximum.
 */
@ChannelHandler.Sharable
public class ExcessConnectionRejector extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = getLogger(ExcessConnectionRejector.class);
    private final ChannelGroup channelGroup;
    private final int maxConnectionsCount;

    public ExcessConnectionRejector(ChannelGroup channelGroup, int maxConnectionsCount) {
        checkArgument(maxConnectionsCount > 0);
        this.channelGroup = requireNonNull(channelGroup);
        this.maxConnectionsCount = maxConnectionsCount;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (channelGroup.size() >= maxConnectionsCount) {
            LOGGER.warn("Max allowed connection  to server exceeded: current={} configured={}", channelGroup.size(), maxConnectionsCount);
            ctx.close();
            return;
        }
        channelGroup.add(ctx.channel());
        super.channelRegistered(ctx);
    }
}
