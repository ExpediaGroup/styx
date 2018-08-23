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
package com.hotels.styx.common.http.handler;

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Returns a 404 Not Found response.
 */
public class NotFoundHandler extends BaseHttpHandler {
    public static final HttpHandler NOT_FOUND_HANDLER = new NotFoundHandler();

    private static final String NOT_FOUND_MESSAGE = "\n"
            + "<!DOCTYPE html>\n"
            + "<html lang=en>\n"
            + "  <meta charset=utf-8>\n"
            + "  <meta name=viewport content=\"initial-scale=1, minimum-scale=1, width=device-width\">\n"
            + "  <title>Error 404 (Not Found)!!1</title>\n"
            + "  <p><b>404.</b> <ins>That’s an error.</ins>\n"
            + "   <p>The requested URL was not found on this server.\n";

    @Override
    public HttpResponse doHandle(HttpRequest request) {
        return FullHttpResponse.response(NOT_FOUND)
                .body(NOT_FOUND_MESSAGE, UTF_8)
                .build()
                .toStreamingResponse();
    }
}
