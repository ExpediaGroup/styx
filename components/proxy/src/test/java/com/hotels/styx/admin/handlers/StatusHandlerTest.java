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

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import com.hotels.styx.api.messages.HttpResponseStatus;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.messages.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.api.messages.HttpResponseStatus.OK;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import com.hotels.styx.api.HttpRequest;

public class StatusHandlerTest {

    @Test
    public void returnsOKForHealthyHealthcheck() {
        StatusHandler statusHandler = new StatusHandler(underlyingHealthCheckIs(OK));
        FullHttpResponse response = waitForResponse(statusHandler.handle(get("/status").build(), HttpInterceptorContext.create()));
        assertThat(response.bodyAs(UTF_8), is("OK"));
    }

    @Test
    public void returnsNOT_OKForFaultyHealthcheck() {
        StatusHandler statusHandler = new StatusHandler(underlyingHealthCheckIs(INTERNAL_SERVER_ERROR));
        FullHttpResponse response = waitForResponse(statusHandler.handle(get("/status").build(), HttpInterceptorContext.create()));
        assertThat(response.bodyAs(UTF_8), is("NOT_OK"));
    }

    private static HttpHandler underlyingHealthCheckIs(HttpResponseStatus status) {
        return new BaseHttpHandler() {
            @Override
            protected HttpResponse doHandle(HttpRequest request) {
                return FullHttpResponse.response(status)
                        .body("some stuff", UTF_8)
                        .build()
                        .toStreamingResponse();
            }
        };
    }

}
