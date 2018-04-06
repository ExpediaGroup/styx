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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.api.Clock;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.support.netty.HttpMessageSupport.httpRequest;
import static com.hotels.styx.support.netty.HttpMessageSupport.httpResponseAsBuf;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TimeToFirstByteHandlerTest {
    static final String HTTP_BODY = "A response from an origin.";

    TimeToFirstByteListener timeToFirstByteListener;
    EmbeddedChannel channel;
    TestClock clock;

    @BeforeMethod
    public void setUp() {
        timeToFirstByteListener = mock(TimeToFirstByteListener.class);
        clock = new TestClock();
        channel = newEmbeddedChannel(clock, timeToFirstByteListener);
    }

    @Test
    public void updatesConnectionPoolWithTimeToFirstByteWhenHttpResponseArrives() throws InterruptedException {
        channel.writeOutbound(httpRequest(GET, "http://www.hotels.com/foo/bar/request"));
        clock.time = 100;
        channel.writeInbound(httpResponseAsBuf(OK, HTTP_BODY).retain());

        verify(timeToFirstByteListener).notifyTimeToFirstByte(100, MILLISECONDS);
    }

    @Test
    public void updatesTimeToFirstByteOnlyOnTheFirstHttpContentByte() throws InterruptedException {
        int headersLength = 39;

        channel.writeOutbound(httpRequest(GET, "http://www.hotels.com/foo/bar/request"));

        ByteBuf response = httpResponseAsBuf(OK, HTTP_BODY).retain();
        int responseLength = response.writerIndex() - response.readerIndex();
        int contentLength = responseLength - headersLength;

        ByteBuf headersOnly = response.slice(0, headersLength);
        ByteBuf contentChunk1 = response.slice(headersLength, contentLength - 10);
        ByteBuf contentChunk2 = response.slice(headersLength, 10);

        clock.time = 100;
        channel.writeInbound(headersOnly);
        clock.time = 200;
        channel.writeInbound(contentChunk1);
        clock.time = 300;
        channel.writeInbound(contentChunk2);

        verify(timeToFirstByteListener, times(1)).notifyTimeToFirstByte(200, MILLISECONDS);
    }

    private EmbeddedChannel newEmbeddedChannel(TestClock clock, TimeToFirstByteListener timeToFirstByteListener) {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new TimeToFirstByteHandler(timeToFirstByteListener, clock));
    }

    private static final class TestClock implements Clock {
        private long time;

        @Override
        public long tickMillis() {
            return time;
        }
    }

}
