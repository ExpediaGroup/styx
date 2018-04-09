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


import com.hotels.styx.server.RequestTimeoutException;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.timeout.IdleStateEvent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.timeout.IdleStateEvent.READER_IDLE_STATE_EVENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class RequestTimeoutHandlerTest {

    private EmbeddedChannel channel;
    private final DefaultHttpRequest first = new DefaultHttpRequest(HTTP_1_1, GET, "/don't/care");
    private final DefaultLastHttpContent last = new DefaultLastHttpContent();

    @BeforeMethod
    public void setUp() {
        channel = new EmbeddedChannel(
                new MockIdleStateHandler(),
                new RequestTimeoutHandler());
    }

    @Test(expectedExceptions = RequestTimeoutException.class)
    public void throwsRequestTimeoutExceptionAfterReadTimesOutDuringIncompleteHttpRequest() {
        channel.writeInbound(first);
        channel.writeInbound(READER_IDLE_STATE_EVENT);
    }

    @Test
    public void doesNothingWhenReadTimesOutAfterCompleteHttpRequestIsReceived() {
        channel.writeInbound(first);
        channel.writeInbound(last);
        channel.writeInbound(READER_IDLE_STATE_EVENT);

        DefaultFullHttpResponse response = (DefaultFullHttpResponse) channel.readOutbound();
        assertThat(response, is(nullValue()));
    }

    @Test
    public void doesNothingWhenReadTimesOutOnIdleChannel() {
        channel.writeInbound(READER_IDLE_STATE_EVENT);

        DefaultFullHttpResponse response = (DefaultFullHttpResponse) channel.readOutbound();
        assertThat(response, is(nullValue()));
    }

    private static class MockIdleStateHandler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof IdleStateEvent) {
                //
                // channelRead event is just a work-around to trigger READER_IDLE_STATE_EVENT.
                // This is necessary because normal Netty IdleStateHandler doesn't work with
                // embedded channels.
                //
                fireReaderIdleEvent(ctx);
            } else {
                super.channelRead(ctx, msg);
            }
        }

        private static void fireReaderIdleEvent(ChannelHandlerContext ctx) {
            ctx.fireUserEventTriggered(READER_IDLE_STATE_EVENT);
        }
    }
}
