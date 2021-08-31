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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequestAsBuf;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpResponseAsBuf;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class ChannelStatisticsHandlerTest {
    private ChannelStatisticsHandler handler;
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void createHandler() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.handler = new ChannelStatisticsHandler(new CentralisedMetrics(this.meterRegistry));
    }

    @Test
    public void countsReceivedBytes() throws Exception {
        ByteBuf buf = httpRequestAsBuf(POST, "/foo/bar", "Hello, world");
        this.handler.channelRead(mock(ChannelHandlerContext.class), buf);
        assertThat(countOf("proxy.server.bytesReceived"), is((double) buf.readableBytes()));
    }

    @Test
    public void countsSentBytes() throws Exception {
        ByteBuf buf = httpResponseAsBuf(OK, "Response from server");
        this.handler.write(mock(ChannelHandlerContext.class), buf, mock(ChannelPromise.class));
        assertThat(countOf("proxy.server.bytesSent"), is((double) buf.readableBytes()));
    }

    private double countOf(String counter) {
        return this.meterRegistry.counter(counter).count();
    }

}