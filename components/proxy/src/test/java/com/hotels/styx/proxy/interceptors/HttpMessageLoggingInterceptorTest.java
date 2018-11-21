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

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
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
        LiveHttpRequest request = get("/")
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
        LiveHttpRequest request = get("/")
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
        LiveHttpRequest request = get("/")
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


    private static HttpInterceptor.Chain chain(LiveHttpResponse.Builder resp) {
        return new HttpInterceptor.Chain() {
            @Override
            public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
                return Eventual.of(resp.build());
            }

            @Override
            public HttpInterceptor.Context context() {
                return new HttpInterceptorContext(true);
            }
        };
    }

    private static void consume(Eventual<LiveHttpResponse> resp) {
        Mono.from(resp.flatMap(it -> it.aggregate(1000000))).block();
    }
}