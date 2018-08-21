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
package com.hotels.styx.server.netty.connectors;

import com.hotels.styx.api.HttpResponseStatus;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Optional;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY;
import static com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.lang.String.format;
import static java.util.regex.Pattern.quote;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ExceptionStatusMapperTest {
    private final ExceptionStatusMapper mapper = new ExceptionStatusMapper.Builder()
            .add(REQUEST_TIMEOUT, Exception1.class)
            .add(BAD_GATEWAY, Exception2.class, Exception3.class, Exception4.class)
            .build();

    @Test
    public void returnsEmptyIfNoStatusMatches() {
        assertThat(mapper.statusFor(new UnmappedException()), isAbsent());
    }

    @Test
    public void retrievesStatus() {
        assertThat(mapper.statusFor(new Exception1()), isValue(REQUEST_TIMEOUT));
    }

    @Test(dataProvider = "badGatewayExceptions")
    public void multipleExceptionsCanMapToTheSameStatus(Exception e) {
        assertThat(mapper.statusFor(e), isValue(BAD_GATEWAY));
    }

    @Test
    public void exceptionMayNotBeMappedToMultipleExceptions() {
        ExceptionStatusMapper mapper = new ExceptionStatusMapper.Builder()
                .add(BAD_GATEWAY, Exception1.class)
                .add(GATEWAY_TIMEOUT, DoubleMappedException.class)
                .build();

        LoggingTestSupport support = new LoggingTestSupport(ExceptionStatusMapper.class);

        Optional<HttpResponseStatus> status;

        try {
            status = mapper.statusFor(new DoubleMappedException());
        } finally {
            assertThat(support.lastMessage(), is(loggingEvent(ERROR,
                    "Multiple matching statuses for throwable="
                            + quote(DoubleMappedException.class.getName())
                            + " statuses=\\[502 Bad Gateway, 504 Gateway Timeout\\]"
            )));
        }

        assertThat(status, isAbsent());
    }

    @DataProvider(name = "badGatewayExceptions")
    private Object[][] badGatewayExceptions() {
        return new Object[][]{
                {new Exception2()},
                {new Exception3()},
                {new Exception4()},
        };
    }

    private static class Exception1 extends Exception {
    }

    private static class Exception2 extends Exception {
    }

    private static class Exception3 extends Exception {
    }

    private static class Exception4 extends Exception {
    }

    private static class DoubleMappedException extends Exception1 {
    }

    private static class UnmappedException extends Exception {
    }
}