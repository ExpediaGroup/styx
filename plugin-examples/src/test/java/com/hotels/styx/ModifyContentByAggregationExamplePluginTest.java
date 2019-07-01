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

import com.hotels.styx.ModifyContentByAggregationExamplePlugin.Config;
import com.hotels.styx.api.*;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ModifyContentByAggregationExamplePluginTest {

    @Test
    public void modifiesContent() {
        // Set up
        Config config = new Config("MyExtraText");
        ModifyContentByAggregationExamplePlugin plugin = new ModifyContentByAggregationExamplePlugin(config);

        LiveHttpRequest request = get("/").build();

        HttpInterceptor.Chain chain = anyRequest -> Eventual.of(response()
                .body("OriginalBody", UTF_8)
                .build()
                .stream());

        // Execution

        HttpResponse response = Mono.from(plugin.intercept(request, chain)
                .flatMap(liveResponse -> liveResponse.aggregate(100)))
                .block();

        // Assertion
        assertThat(response.bodyAs(UTF_8), is("OriginalBodyMyExtraText"));
    }
}
