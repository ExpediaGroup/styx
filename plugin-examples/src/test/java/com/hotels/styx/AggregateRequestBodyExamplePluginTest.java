/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.hotels.styx.AggregateRequestBodyExamplePlugin.Config;
import com.hotels.styx.api.ByteStream;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class AggregateRequestBodyExamplePluginTest {

    @Test
    public void contentIsModified() {
        // Set up
        Config config = new Config("MyExtraText");
        String originalBody = "OriginalBody";
        AggregateRequestBodyExamplePlugin plugin = new AggregateRequestBodyExamplePlugin(config);
        LiveHttpRequest request = LiveHttpRequest.post("/", ByteStream.from(originalBody, UTF_8)).build();

        // Our implementation of "chain.proceed()" verifies the content of the request
        HttpInterceptor.Chain chain = intRequest -> {
            String requestBody = Mono.from(intRequest.aggregate(100)).block().bodyAs(UTF_8);
            assertThat(requestBody, is(originalBody + config.extraText()));
            return Eventual.of(LiveHttpResponse.response().build());
        };
        // Invoke plugin and wait until it finishes execution
        Mono.from(plugin.intercept(request, chain)).block();
    }
}
