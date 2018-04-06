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
package loadtest.plugins;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpHandler;

import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static rx.Observable.just;

final class AdminHandlers {
    private AdminHandlers() {
    }

    static ImmutableMap<String, HttpHandler> adminHandlers(String endpoint, String responseContent) {
        return ImmutableMap.of(endpoint, (request) -> just(response(OK)
                .body(responseContent)
                .build()));
    }
}
