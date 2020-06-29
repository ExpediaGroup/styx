/*
  Copyright (C) 2013-2020 Expedia Inc.

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

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;

/**
 * Provides an HTTP response with a body consisting of a thread dump.
 */
public class ThreadsHandler extends BaseHttpHandler {

    @Override
    public HttpResponse doHandle(HttpRequest request, HttpInterceptor.Context context) {
        return HttpResponse.response(OK)
                .disableCaching()
                .header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(threadDumpContent(), true)
                .build();
    }

    private static byte[] threadDumpContent() {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(contents));

        writer.println();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            writer.println(threadInfo.toString());
        }

        writer.println();
        writer.flush();
        return contents.toByteArray();
    }
}
