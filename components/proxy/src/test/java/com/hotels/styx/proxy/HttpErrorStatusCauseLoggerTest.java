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
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

public class HttpErrorStatusCauseLoggerTest {
    HttpErrorStatusCauseLogger httpErrorStatusCauseLogger;
    LoggingTestSupport loggingTestSupport;

    @BeforeMethod
    public void setUp() {
        loggingTestSupport = new LoggingTestSupport(HttpErrorStatusCauseLogger.class);
        httpErrorStatusCauseLogger = new HttpErrorStatusCauseLogger();
    }

    @AfterMethod
    public void removeAppender() {
        loggingTestSupport.stop();
    }

    @Test
    public void logsThrowables() {
        Exception exception = new Exception("This is just a test");

        httpErrorStatusCauseLogger.proxyErrorOccurred(INTERNAL_SERVER_ERROR, exception);

        assertThat(loggingTestSupport.log(), hasItem(
                loggingEvent(
                        ERROR,
                        "Failure status=\"500 Internal Server Error\"",
                        "java.lang.Exception",
                        "This is just a test")));
    }

    @Test
    public void logsThrowablesWithStatus5xxExcluding500WithoutStackTrace() {
        Exception exception = new Exception("This is just a test");

        httpErrorStatusCauseLogger.proxyErrorOccurred(BAD_GATEWAY, exception);

        assertThat(loggingTestSupport.log(), hasItem(
                loggingEvent(
                        ERROR,
                        "Failure status=\"502 Bad Gateway\", exception=\"java.lang.Exception.*This is just a test.*\"")));
    }

    @Test
    public void logsInternalServerErrorWithRequest() {
        HttpRequest request = HttpRequest.get("/foo").build();
        Exception exception = new Exception("This is just a test");

        httpErrorStatusCauseLogger.proxyErrorOccurred(request, INTERNAL_SERVER_ERROR, exception);

        assertThat(loggingTestSupport.log(), hasItem(
                loggingEvent(
                        ERROR,
                        "Failure status=\"500 Internal Server Error\" during request=HttpRequest\\{version=HTTP/1.1, method=GET, uri=/foo, headers=\\[\\], id=.*, clientAddress=.*:.*\\}",
                        "java.lang.Exception",
                        "This is just a test")));
    }

    @Test
    public void logsOtherExceptionsWithoutRequest() throws Exception {
        HttpRequest request = HttpRequest.get("/foo").build();
        Exception exception = new Exception("This is just a test");

        httpErrorStatusCauseLogger.proxyErrorOccurred(request, BAD_GATEWAY, exception);

        assertThat(loggingTestSupport.log(), hasItem(
                loggingEvent(
                        ERROR,
                        "Failure status=\"502 Bad Gateway\", exception=\"java.lang.Exception.*This is just a test.*\"")));
    }

}