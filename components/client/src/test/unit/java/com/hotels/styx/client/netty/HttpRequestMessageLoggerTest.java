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
package com.hotels.styx.client.netty;

import ch.qos.logback.classic.Level;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.common.logging.HttpRequestMessageLogger;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.WARN;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(PER_CLASS)
public class HttpRequestMessageLoggerTest {

    private static final String FORMATTED_REQUEST = "request";
    private static final String FORMATTED_RESPONSE = "response";
    private Origin origin;
    private LoggingTestSupport log;

    private HttpMessageFormatter httpMessageFormatter;

    @BeforeAll
    public void setup() {
        this.origin = newOriginBuilder("hostA", 80)
                .applicationId(id("MyApp"))
                .id("h1")
                .build();
        httpMessageFormatter = mock(HttpMessageFormatter.class);
        when(httpMessageFormatter.formatRequest(any(LiveHttpRequest.class))).thenReturn(FORMATTED_REQUEST);
        when(httpMessageFormatter.formatResponse(any(LiveHttpResponse.class))).thenReturn(FORMATTED_RESPONSE);
    }

    @BeforeEach
    public void before() {
        log = new LoggingTestSupport("com.hotels.styx.http-messages.outbound");
    }

    @AfterEach
    public void after() {
        log.stop();
    }

    @Test
    public void logsClientSideRequestShortFormat() {
        LiveHttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false, httpMessageFormatter).logRequest(styxRequest, origin, true);

        assertThat(log.lastMessage(), is(loggingEvent(INFO,
                format("requestId=%s, secure=true, origin=%s, request=\\{version=HTTP/1.1, method=GET, uri=%s, id=%s}",
                    styxRequest.id(), origin, styxRequest.url(), styxRequest.id()))));
    }

    @Test
    public void logsClientSideRequestLongFormat() {
        LiveHttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", true, httpMessageFormatter).logRequest(styxRequest, origin, true);

        assertThat(log.lastMessage(), is(loggingEvent(INFO,
                format("requestId=%s, secure=true, origin=%s, request=" + FORMATTED_REQUEST,
                        styxRequest.id(), origin))));
    }

    @Test
    public void logsClientSideResponseDetailsShortFormat() {
        LiveHttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        LiveHttpResponse styxResponse = response(OK).build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false, httpMessageFormatter).logResponse(styxRequest, styxResponse);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, format("requestId=%s, response=\\{version=HTTP/1.1, status=200 OK\\}", styxRequest.id()))));
    }

    @Test
    public void logsClientSideResponseDetailsLongFormat() {
        LiveHttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        LiveHttpResponse styxResponse = response(OK).build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", true, httpMessageFormatter).logResponse(styxRequest, styxResponse);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, format("requestId=%s, response=" + FORMATTED_RESPONSE,styxRequest.id()))));
    }

    @Test
    public void requestLoggingDoesNotThrowExceptionWhenReceivingNullArguments() {
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false, httpMessageFormatter).logRequest(null, origin, true);

        assertThat(log.lastMessage(), is(loggingEvent(WARN, "requestId=N/A, origin=MyApp:h1:hostA:80, request=null")));
    }

    @ParameterizedTest
    @MethodSource("responseLogUnexpectedArguments")
    public void responseLoggingDoesNotThrowExceptionWhenReceivingNullArguments(LiveHttpRequest request, LiveHttpResponse response, Level expectedLogLevel, String expectedLogMessage) {
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false, httpMessageFormatter).logResponse(request, response);

        assertThat(log.lastMessage(), is(loggingEvent(expectedLogLevel, expectedLogMessage)));
    }

    private static Stream<Arguments> responseLogUnexpectedArguments() {
        LiveHttpRequest normalRequest = get("http://www.hotels.com/foo/bar/request").build();
        LiveHttpResponse normalResponse = response(OK).build();

        return Stream.of(
                Arguments.of(normalRequest, null, WARN, "requestId=.*, response=null"),
                Arguments.of(null, normalResponse, INFO, "requestId=null, response=\\{version=HTTP/1.1, status=200 OK\\}"),
                Arguments.of(null, null, WARN, "requestId=null, response=null")
        );
    }
}
