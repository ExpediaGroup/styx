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
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.support.api.HttpMessageBodies;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class HttpMessageLoggingInterceptorTest {
    private LoggingTestSupport responseLogSupport;
    private HttpMessageLoggingInterceptor interceptor;

    @BeforeMethod
    public void before() {
        responseLogSupport = new LoggingTestSupport("com.hotels.styx.http-messages.inbound");
        interceptor = new HttpMessageLoggingInterceptor(true);
    }

    @AfterMethod
    public void after() {
        responseLogSupport.stop();
    }

    @Test
    public void logsRequestsAndResponses() {
        HttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                .header("RespHeader", "RespHeaderValue")
                .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        String requestPattern = "request=\\{method=GET, uri=/, origin=\"N/A\", headers=\\[ReqHeader=ReqHeaderValue, Cookie=ReqCookie=ReqCookieValue\\]\\}";
        String responsePattern = "response=\\{status=200 OK, headers=\\[RespHeader=RespHeaderValue\\, Set-Cookie=RespCookie=RespCookieValue]\\}";

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + requestPattern),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + responsePattern)));
    }

    @Test
    public void logsRequestsAndResponsesShort() {
        interceptor = new HttpMessageLoggingInterceptor(false);
        HttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                        .header("RespHeader", "RespHeaderValue")
                        .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        String requestPattern = "request=\\{method=GET, uri=/, origin=\"N/A\"}";
        String responsePattern = "response=\\{status=200 OK}";

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + requestPattern),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + responsePattern)));
    }

    @Test
    public void logsSecureRequests() {
        HttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(response(OK))));

        String requestPattern = "request=\\{method=GET, uri=/, origin=\"N/A\", headers=\\[ReqHeader=ReqHeaderValue, Cookie=ReqCookie=ReqCookieValue\\]\\}";
        String responsePattern = "response=\\{status=200 OK, headers=\\[\\]\\}";

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + requestPattern),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + responsePattern)));
    }


    private static HttpInterceptor.Chain chain(HttpResponse.Builder resp) {
        return new HttpInterceptor.Chain() {
            @Override
            public StyxObservable<HttpResponse> proceed(HttpRequest request) {
                return StyxObservable.of(resp.build());
            }

            @Override
            public HttpInterceptor.Context context() {
                return new HttpInterceptorContext(true);
            }
        };
    }

    private static void consume(StyxObservable<HttpResponse> resp) {
        await(resp.map(HttpMessageBodies::bodyAsString).asCompletableFuture());
    }
}