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
package com.hotels.styx.server.netty.connectors;

import com.hotels.styx.api.HttpHeader;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Supplier;

import static com.hotels.styx.api.HttpHeader.header;
import static com.hotels.styx.server.netty.connectors.EmbeddedChannelSupport.httpResponseWithOkStatus;
import static com.hotels.styx.server.netty.connectors.EmbeddedChannelSupport.httpResponseWithOkStatusAndHeaders;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;

public class EmbeddedChannelSupportTest {
    @Test
    public void convertsNullTerminatedSupplierToList() {
        Supplier<Integer> supplier = new Supplier() {
            int i = 0;

            @Override
            public Object get() {
                return (i < 3) ? ++i : null;
            }
        };

        List<Integer> list = EmbeddedChannelSupport.toList(supplier);

        assertThat(list, contains(1, 2, 3));
    }

    @Test
    public void convertsEmbeddedChannelOutboundToList() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler());
        channel.writeOutbound(1, 2, 3);

        List<Object> list = EmbeddedChannelSupport.outbound(channel);

        assertThat(list, contains(1, 2, 3));
    }

    @Test
    public void matchesOkResponse() {
        assertThat(new DefaultHttpResponse(HTTP_1_1, OK), httpResponseWithOkStatus());
    }

    @Test
    public void doesNotMatchesNonOkResponse() {
        assertThat(new DefaultHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR), not(httpResponseWithOkStatus()));
    }

    @Test
    public void matchesOkResponseWithHeaders() {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set("foo1", "bar1");
        response.headers().set("foo2", "bar2");

        assertThat(response, httpResponseWithOkStatusAndHeaders(header("foo1", "bar1"), header("foo2", "bar2")));
    }

    @Test
    public void doesNotMatchesIncorrect() {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set("foo1", "incorrect");
        response.headers().set("foo2", "bar2");

        assertThat(response, not(httpResponseWithOkStatusAndHeaders(header("foo1", "bar1"), header("foo2", "bar2"))));
    }
}