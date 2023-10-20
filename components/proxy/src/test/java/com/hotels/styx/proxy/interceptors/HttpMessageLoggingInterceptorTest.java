/*
  Copyright (C) 2013-2023 Expedia Inc.

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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
public class HttpMessageLoggingInterceptorTest {

    private static final String FORMATTED_REQUEST = "request";
    private static final String FORMATTED_RESPONSE = "response";

    private LoggingTestSupport responseLogSupport;
    private HttpMessageLoggingInterceptor interceptor;

    private HttpMessageFormatter httpMessageFormatter;

    private Runnable resetLevel;

    @BeforeAll
    public void setup() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.hotels.styx.http-messages.inbound");
        Level previousLevel = logger.getLevel();
        logger.setLevel(INFO);
        resetLevel = () -> logger.setLevel(previousLevel);

        httpMessageFormatter = mock(HttpMessageFormatter.class);
        when(httpMessageFormatter.formatRequest(any(LiveHttpRequest.class))).thenReturn(FORMATTED_REQUEST);
        when(httpMessageFormatter.formatResponse(any(LiveHttpResponse.class))).thenReturn(FORMATTED_RESPONSE);
    }

    @AfterAll
    public void tearDown() {
        resetLevel.run();
    }

    @BeforeEach
    public void before() {
        responseLogSupport = new LoggingTestSupport("com.hotels.styx.http-messages.inbound");
        interceptor = new HttpMessageLoggingInterceptor(true, httpMessageFormatter);
    }

    @AfterEach
    public void after() {
        responseLogSupport.stop();
    }

    @Test
    public void logsRequestsAndResponses() {
        LiveHttpRequest request = get("/")
                .version(HttpVersion.HTTP_1_1)
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                .header("RespHeader", "RespHeaderValue")
                .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, origin=null, request=" + FORMATTED_REQUEST),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, response=" + FORMATTED_RESPONSE)));
    }

    @Test
    public void logsRequestsAndResponsesShort() {
        interceptor = new HttpMessageLoggingInterceptor(false, httpMessageFormatter);
        LiveHttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                        .header("RespHeader", "RespHeaderValue")
                        .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        String requestPattern = "request=\\{version=HTTP/1.1, method=GET, uri=/, id=" + request.id() + "\\}";
        String responsePattern = "response=\\{version=HTTP/1.1, status=200 OK\\}";

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, origin=null, " + requestPattern),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, " + responsePattern)));
    }

    @Test
    public void logsSecureRequests() {
        LiveHttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(response(OK))));

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, origin=null, request=" + FORMATTED_REQUEST),
                loggingEvent(INFO, "requestId=" + request.id() + ", secure=true, response=" + FORMATTED_RESPONSE)));
    }


    private static HttpInterceptor.Chain chain(LiveHttpResponse.Builder resp) {
        return new HttpInterceptor.Chain() {
            @Override
            public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
                return Eventual.of(resp.build());
            }

            @Override
            public HttpInterceptor.Context context() {
                return requestContext(true);
            }
        };
    }

    private static void consume(Eventual<LiveHttpResponse> resp) {
        Mono.from(resp.flatMap(it -> it.aggregate(1000000))).block();
    }
}
