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
package com.hotels.styx.support.matchers;

import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.slf4j.LoggerFactory.getLogger;

public class LoggingTestSupportTest {
    private static final Logger LOGGER = getLogger(LoggingTestSupportTest.class);

    private LoggingTestSupport support;

    @BeforeMethod
    public void startRecordingLogs() {
        support = new LoggingTestSupport(LoggingTestSupportTest.class);
    }

    @AfterMethod
    public void stopRecordingLogs() {
        support.stop();
    }

    @Test
    public void recordsLastLogEntry() {
        LOGGER.info("Hello, World!");

        assertThat(support.lastMessage(), is(loggingEvent(INFO, "Hello, World!")));
    }

    @Test
    public void recordsAllLogEntries() {
        LOGGER.info("Hello, World!");
        LOGGER.info("Goodbye, World!");

        assertThat(support.log(), contains(loggingEvent(INFO, "Hello, World!"), loggingEvent(INFO, "Goodbye, World!")));
    }

    @Test
    public void recordsExceptionDetails() {
        LOGGER.error("Bad!", new RuntimeException("Error details"));

        assertThat(support.lastMessage(), is(loggingEvent(ERROR, "Bad!", RuntimeException.class, "Error details")));
    }

    @Test
    public void stopsRecording() {
        support.stop();

        LOGGER.info("Hello, World!");

        assertThat(support.log(), is(empty()));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotAcceptNullClass() {
        new LoggingTestSupport((Class) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void cannotAcceptNullName() {
        new LoggingTestSupport((String) null);
    }

    @Test
    public void supportsLoggersByName() {
        Logger logger = getLogger("my-test-logger");

        LoggingTestSupport support = new LoggingTestSupport("my-test-logger");

        try {
            logger.info("Hello, World!");

            assertThat(support.lastMessage(), is(loggingEvent(INFO, "Hello, World!")));
        } finally {
            support.stop();
        }
    }
}