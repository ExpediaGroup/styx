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
package com.hotels.styx.plugins;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import org.slf4j.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

public class ContentCutoffPlugin implements Plugin {
    private static final Logger LOGGER = getLogger(ContentCutoffPlugin.class);

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest httpRequest, Chain chain) {
        return chain
                .proceed(httpRequest)
                .flatMap(response -> response.aggregate(1000000)
                        .flatMap(it -> {
                            LOGGER.info("do something with full content: " + it.bodyAs(UTF_8));

                            // Error: Returns an already consumed response.
                            return Eventual.of(response);
                        })
                );
    }

}
