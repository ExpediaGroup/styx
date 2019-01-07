/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Stops channelInactive events from propagating if the channel never became active to begin with.
 *
 * In some circumstances (e.g. when a channel is rejected due to having too many connections) we may receive a
 * channelInactive event on a channel that was not active.
 *
 * This event is unneeded and will confuse later handlers that collect metrics about channel activity.
 *
 * Note that this handler is not sharable, because it tracks whether a single channel is active.
 */
public class ChannelActivityEventConstrainer extends ChannelInboundHandlerAdapter {
    private boolean channelActivated;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channelActivated = true;
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // In some circumstances (e.g. when a channel is rejected due to having too many connections) we may receive a
        // channelInactive event on a channel that was not active. We don't need this event (and it will confuse
        // metrics, etc. so we prevent it from propagating)
        if (channelActivated) {
            super.channelInactive(ctx);
        }

        channelActivated = false;
    }
}
