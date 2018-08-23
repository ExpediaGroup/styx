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

import com.codahale.metrics.jvm.ThreadDump;
import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;

import java.io.ByteArrayOutputStream;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.management.ManagementFactory.getThreadMXBean;

/**
 * Provides an HTTP response with a body consisting of a thread dump.
 */
public class ThreadsHandler extends BaseHttpHandler {
    private final ThreadDump threadDump;

    /**
     * Constructs an instance.
     */
    public ThreadsHandler() {
        this.threadDump = new ThreadDump(getThreadMXBean());
    }

    @Override
    public HttpResponse doHandle(HttpRequest request) {
        return FullHttpResponse.response(OK)
                .disableCaching()
                .header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(threadDumpContent(), true)
                .build()
                .toStreamingResponse();
    }

    private byte[] threadDumpContent() {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        threadDump.dump(contents);
        return contents.toByteArray();
    }
}
