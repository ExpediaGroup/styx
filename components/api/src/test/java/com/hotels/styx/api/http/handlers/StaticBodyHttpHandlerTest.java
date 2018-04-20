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
import com.hotels.styx.api.messages.FullHttpResponse;
import com.hotels.styx.api.v2.StyxCoreObservable;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.MockContext.MOCK_CONTEXT;
import static com.hotels.styx.api.TestSupport.getFirst;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StaticBodyHttpHandlerTest {
    @Test
    public void respondsWithStaticBody() throws ExecutionException, InterruptedException {
        StaticBodyHttpHandler handler = new StaticBodyHttpHandler(PLAIN_TEXT_UTF_8, "foo", UTF_8);

        HttpResponse response = getFirst(handler.handle(get("/").build(), MOCK_CONTEXT));
        FullHttpResponse fullResponse = ((StyxCoreObservable<FullHttpResponse>) response.toFullResponse(1024))
                .asCompletableFuture()
                .get();

        assertThat(fullResponse.status(), is(OK));
        assertThat(fullResponse.contentType(), isValue(PLAIN_TEXT_UTF_8.toString()));
        assertThat(fullResponse.contentLength(), isValue(length("foo")));
        assertThat(fullResponse.bodyAs(UTF_8), is("foo"));
    }

    private Integer length(String string) {
        return string.getBytes().length;
    }

}
