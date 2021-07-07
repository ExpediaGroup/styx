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
package com.hotels.styx.proxy;

import com.hotels.styx.common.SimpleCache;
import com.hotels.styx.metrics.CentralisedMetrics;
import io.micrometer.core.instrument.Counter;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Records request and response count with protocol.
 */
public class ServerProtocolDistributionRecorder extends ChannelDuplexHandler {
    private final Counter requests;
    private final SimpleCache<Integer, Counter> responses;

    public ServerProtocolDistributionRecorder(CentralisedMetrics metrics, boolean secure) {
        CentralisedMetrics.Proxy.Server serverMetrics = metrics.proxy().server();

        requests = secure ? serverMetrics.httpsRequests() : serverMetrics.httpRequests();
        responses = secure ? serverMetrics.httpsResponses() : serverMetrics.httpResponses();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            requests.increment();
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse) {
            responses.get(((HttpResponse) msg).status().code()).increment();
        }

        super.write(ctx, msg, promise);
    }
}
