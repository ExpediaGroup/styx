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
package com.hotels.styx.api.http.handlers;


import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.TestSupport.getFirst;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StaticBodyHttpHandlerTest {
    @Test
    public void respondsWithStaticBody() {
        StaticBodyHttpHandler handler = new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "foo", UTF_8);

        HttpResponse response = getFirst(handler.handle(get("/").build()));
        HttpResponse.DecodedResponse<String> decodedResponse = response.decode(
                buf -> buf.toString(StandardCharsets.UTF_8), 1024).toBlocking().first();

        assertThat(response.status(), is(OK));
        assertThat(response.contentType(), isValue(PLAIN_TEXT_UTF_8.toString()));
        assertThat(response.contentLength(), isValue(length("foo")));
        assertThat(decodedResponse.body(), is("foo"));
    }

    private Integer length(String string) {
        return string.getBytes().length;
    }

}
