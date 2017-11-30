/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.servers;

import com.github.tomakehurst.wiremock.http.Response;
import com.hotels.styx.api.HttpHeaders;
import com.hotels.styx.api.messages.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

final class WiremockResponseConverter {

    private WiremockResponseConverter() {
    }

    static FullHttpResponse<String> toStyxResponse(Response response) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
        HttpHeaders headers = toStyxHeaders(response.getHeaders());
        String content = response.getBodyAsString();

        return FullHttpResponse.<String>response(status).headers(headers).body(content).build();
    }

    private static HttpHeaders toStyxHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders headers) {
        HttpHeaders.Builder builder = new HttpHeaders.Builder();

        if (headers != null) {
            headers.all().forEach(header -> builder.add(header.key(), header.values()));
        }

        return builder.build();
    }

}
