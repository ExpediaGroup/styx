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

import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequestAsBuf;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpResponseAsBuf;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class ChannelStatisticsHandlerTest {
    private ChannelStatisticsHandler handler;
    private MetricRegistry metricRegistry;

    @BeforeMethod
    public void createHandler() {
        this.metricRegistry = new CodaHaleMetricRegistry();
        this.handler = new ChannelStatisticsHandler(this.metricRegistry);
    }

    @Test
    public void countsReceivedBytes() throws Exception {
        ByteBuf buf = httpRequestAsBuf(POST, "/foo/bar", "Hello, world");
        this.handler.channelRead(mock(ChannelHandlerContext.class), buf);
        assertThat(countOf(ChannelStatisticsHandler.RECEIVED_BYTES), is((long) buf.readableBytes()));
    }

    @Test
    public void countsSentBytes() throws Exception {
        ByteBuf buf = httpResponseAsBuf(OK, "Response from server");
        this.handler.write(mock(ChannelHandlerContext.class), buf, mock(ChannelPromise.class));
        assertThat(countOf(ChannelStatisticsHandler.SENT_BYTES), is((long) buf.readableBytes()));
    }

    private long countOf(String sentBytes) {
        return this.metricRegistry.counter(metricName(sentBytes)).getCount();
    }

    private static String metricName(String sentBytes) {
        return ChannelStatisticsHandler.PREFIX + "." + sentBytes;
    }
}
