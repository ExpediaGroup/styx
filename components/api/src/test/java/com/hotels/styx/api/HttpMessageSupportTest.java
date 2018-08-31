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
package com.hotels.styx.api;

import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpHeaderNames.CONNECTION;
import static com.hotels.styx.api.HttpHeaderNames.TRANSFER_ENCODING;
import static com.hotels.styx.api.HttpHeaderValues.CHUNKED;
import static com.hotels.styx.api.HttpHeaderValues.CLOSE;
import static com.hotels.styx.api.HttpHeaderValues.KEEP_ALIVE;
import static com.hotels.styx.api.HttpMessageSupport.chunked;
import static com.hotels.styx.api.HttpMessageSupport.keepAlive;
import static com.hotels.styx.api.HttpVersion.HTTP_1_0;
import static com.hotels.styx.api.HttpVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpMessageSupportTest {
    @Test
    public void chunkedReturnsTrueWhenChunkedTransferEncodingIsSet() {
        HttpHeaders headers = requestBuilder()
                .header(TRANSFER_ENCODING, CHUNKED)
                .build()
                .headers();

        assertThat(chunked(headers), is(true));

        HttpHeaders headers2 = requestBuilder()
                .header(TRANSFER_ENCODING, "foo")
                .addHeader(TRANSFER_ENCODING, CHUNKED)
                .addHeader(TRANSFER_ENCODING, "bar")
                .build()
                .headers();

        assertThat(chunked(headers2), is(true));
    }

    private HttpRequest.Builder requestBuilder() {
        return HttpRequest.get("/");
    }

    @Test
    public void chunkedReturnsFalseWhenChunkedTransferEncodingIsNotSet() {
        HttpHeaders headers = requestBuilder()
                .header(TRANSFER_ENCODING, "foo")
                .build()
                .headers();

        assertThat(chunked(headers), is(false));
    }

    @Test
    public void chunkedReturnsTrueWhenChunkedTransferEncodingIsAbsent() {
        HttpHeaders headers = requestBuilder().build().headers();
        assertThat(chunked(headers), is(false));
    }

    @Test
    public void keepAliveReturnsTrueWhenHttpConnectionMustBeKeptAlive() {
        HttpHeaders connectionHeaderAbsent = requestBuilder().build().headers();

        HttpHeaders connectionHeaderClose = requestBuilder()
                .header(CONNECTION, CLOSE)
                .build()
                .headers();

        HttpHeaders connectionHeaderKeepAlive = requestBuilder()
                .header(CONNECTION, KEEP_ALIVE)
                .build()
                .headers();

        assertThat(keepAlive(connectionHeaderAbsent, HTTP_1_0), is(false));
        assertThat(keepAlive(connectionHeaderAbsent, HTTP_1_1), is(true));

        assertThat(keepAlive(connectionHeaderClose, HTTP_1_0), is(false));
        assertThat(keepAlive(connectionHeaderClose, HTTP_1_1), is(false));

        assertThat(keepAlive(connectionHeaderKeepAlive, HTTP_1_0), is(true));
        assertThat(keepAlive(connectionHeaderKeepAlive, HTTP_1_1), is(true));
    }
}