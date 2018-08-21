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
package com.hotels.styx.client.netty;

import ch.qos.logback.classic.Level;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.common.logging.HttpRequestMessageLogger;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.WARN;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpRequestMessageLoggerTest {

    private final Id appId;
    private final Origin origin;
    private LoggingTestSupport log;

    public HttpRequestMessageLoggerTest() {
        this.appId = id("MyApp");
        this.origin = newOriginBuilder("hostA", 80)
                .applicationId(this.appId)
                .id("h1")
                .build();
    }

    @BeforeMethod
    public void before() {
        log = new LoggingTestSupport("com.hotels.styx.http-messages.outbound");
    }

    @AfterMethod
    public void after() {
        log.stop();
    }

    @Test
    public void logsClientSideRequestShortFormat() {
        HttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false).logRequest(styxRequest, origin);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, format("requestId=%s, request=\\{method=GET, secure=false, uri=%s, origin=\"%s\"\\}",
                styxRequest.id(), styxRequest.url(), origin.hostAsString()))));
    }

    @Test
    public void logsClientSideRequestLongFormat() {
        HttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", true).logRequest(styxRequest, origin);

        assertThat(log.lastMessage(), is(loggingEvent(INFO,
                format("requestId=%s, request=\\{method=GET, secure=false, uri=%s, origin=\"%s\", headers=\\[Host=www.hotels.com\\]\\}",
                        styxRequest.id(), styxRequest.url(), origin.hostAsString()))));
    }

    @Test
    public void logsClientSideResponseDetailsShortFormat() {
        HttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        HttpResponse styxResponse = response(OK).build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false).logResponse(styxRequest, styxResponse);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, format("requestId=%s, response=\\{status=200 OK\\}", styxRequest.id()))));
    }

    @Test
    public void logsClientSideResponseDetailsLongFormat() {
        HttpRequest styxRequest = get("http://www.hotels.com/foo/bar/request").build();
        HttpResponse styxResponse = response(OK).build();
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", true).logResponse(styxRequest, styxResponse);

        assertThat(log.lastMessage(), is(loggingEvent(INFO, format("requestId=%s, response=\\{status=200 OK\\, headers=\\[\\]}", styxRequest.id()))));
    }

    @Test
    public void requestLoggingDoesNotThrowExceptionWhenReceivingNullArguments() {
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false).logRequest(null, origin);

        assertThat(log.lastMessage(), is(loggingEvent(WARN, "requestId=N/A, request=null, origin=MyApp:h1:hostA:80")));
    }

    @Test(dataProvider = "responseLogUnexpectedArguments")
    public void responseLoggingDoesNotThrowExceptionWhenReceivingNullArguments(HttpRequest request, HttpResponse response, Level expectedLogLevel, String expectedLogMessage) {
        new HttpRequestMessageLogger("com.hotels.styx.http-messages.outbound", false).logResponse(request, response);

        assertThat(log.lastMessage(), is(loggingEvent(expectedLogLevel, expectedLogMessage)));
    }

    @DataProvider(name = "responseLogUnexpectedArguments")
    private Object[][] responseLogUnexpectedArguments() {
        HttpRequest normalRequest = get("http://www.hotels.com/foo/bar/request").build();
        HttpResponse normalResponse = response(OK).build();

        return new Object[][]{
                {normalRequest, null, WARN, "requestId=.*, response=null"},
                {null, normalResponse, INFO, "requestId=null, response=\\{status=200 OK\\}"},
                {null, null, WARN, "requestId=null, response=null"},
        };
    }
}
