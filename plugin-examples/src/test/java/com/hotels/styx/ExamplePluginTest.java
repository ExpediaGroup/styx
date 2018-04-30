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
package com.hotels.styx;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static rx.Observable.just;

/**
 * This is a unit test for your plugin. Please change it to test the behaviour you expect your plugin to exhibit.
 */
public class ExamplePluginTest {
    private final ExamplePluginConfig config = new ExamplePluginConfig("foo", "bar");
    private final ExamplePlugin plugin = new ExamplePlugin(config);

    /**
     * This tests the behaviours added in the example plugin.
     */
    @Test
    public void addsExtraHeaders() throws Exception {
        // a simple way to mock the downstream system
        HttpInterceptor.Chain chain = request -> {
            assertThat(request.header("myRequestHeader").orElse(null), is("foo"));

            return just(response(OK).build());
        };

        // an example request you expect your plugin to receive
        HttpRequest request = get("/foo")
                .build();

        HttpResponse response = plugin.intercept(request, chain).toBlocking().single();

        assertThat(response.header("myResponseheader").orElse(null), is("bar"));
    }
}
