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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static com.hotels.styx.support.api.matchers.HttpHeadersMatcher.isNotCacheable;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PingHandlerTest {
    final PingHandler handler = new PingHandler();

    @Test
    public void respondsPongToPingRequest() {
        HttpResponse response = waitForResponse(handler.handle(get("/ping").build(), HttpInterceptorContext.create()));
        assertThat(response.status(), is(OK));
        assertThat(response.headers(), isNotCacheable());
        assertThat(response.contentType().get(), is("text/plain; charset=utf-8"));
        assertThat(response.bodyAs(UTF_8), is("pong"));
    }

}
