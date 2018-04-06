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
import com.hotels.styx.api.http.handlers.BaseHttpHandler;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

import java.io.ByteArrayOutputStream;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpResponse.Builder.response;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
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
        return response(OK)
                .disableCaching()
                .contentType(PLAIN_TEXT_UTF_8)
                .body(threadDumpContent())
                .build();
    }

    private byte[] threadDumpContent() {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        threadDump.dump(contents);
        return contents.toByteArray();
    }
}
