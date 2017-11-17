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
import com.hotels.styx.api.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.copiedBuffer;

public class WiremockResponseConverter {

    public static HttpResponse toStyxResponse(Response response) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(response.getStatus());
        com.hotels.styx.api.HttpHeaders headers = toStyxHeaders(response.getHeaders());
        ByteBuf content = toNettyContent(response);

        return HttpResponse.Builder.response(status).headers(headers).body(content).build();
    }

    private static com.hotels.styx.api.HttpHeaders toStyxHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders headers) {
        HttpResponse.Builder builder = HttpResponse.Builder.response();

        if (headers != null) {
            headers.all().forEach(header -> builder.addHeader(header.key(), header.values()));
        }

        return builder.build().headers();
    }

    private static ByteBuf toNettyContent(Response response) {
        ByteBuf content;
        if (response.getBody() != null) {
            content = copiedBuffer(response.getBody());
        } else {
            content = EMPTY_BUFFER;
        }
        return content;
    }

}
