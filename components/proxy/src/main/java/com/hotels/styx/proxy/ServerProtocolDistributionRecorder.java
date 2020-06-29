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
package com.hotels.styx.proxy;

import com.hotels.styx.common.SimpleCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import static java.lang.String.valueOf;

/**
 * Records request and response count with protocol.
 */
public class ServerProtocolDistributionRecorder extends ChannelDuplexHandler {
    private final Counter requests;
    private final SimpleCache<HttpResponseStatus, Counter> responses;

    public ServerProtocolDistributionRecorder(MeterRegistry meterRegistry, boolean secure) {
        requests = meterRegistry.counter("styx.server.requests", "protocol", protocolName(secure));
        responses = new SimpleCache<>(status -> meterRegistry.counter("styx.server.responses",
                "protocol", protocolName(secure),
                "statusCode", valueOf(status.code())));
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
            responses.get(((HttpResponse) msg).status()).increment();
        }

        super.write(ctx, msg, promise);
    }

    private static String protocolName(boolean secure) {
        return secure ? "https" : "http";
    }
}
