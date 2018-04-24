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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.slf4j.LoggerFactory.getLogger;

public class LoggingTestSupport {
    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    public LoggingTestSupport(Class<?> classUnderTest) {
        this(logger(classUnderTest));
    }

    public LoggingTestSupport(String name) {
        this(logger(name));
    }

    private LoggingTestSupport(Logger logger) {
        this.logger = requireNonNull(logger);
        this.appender = addListAppender();
    }

    public void stop() {
        removeListAppender(this.appender);
    }

    public List<ILoggingEvent> log() {
        return this.appender.list;
    }

    private ListAppender<ILoggingEvent> addListAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((Context) LoggerFactory.getILoggerFactory());
        appender.start();

        logger.addAppender(appender);

        return appender;
    }

    private void removeListAppender(ListAppender<ILoggingEvent> appender) {
        logger.detachAppender(appender);
    }

    private static Logger logger(Class<?> classUnderTest) {
        return (Logger) getLogger(requireNonNull(classUnderTest));
    }

    private static Logger logger(String name) {
        return (Logger) getLogger(requireNonNull(name));
    }

    public ILoggingEvent lastMessage() {
        if (log().isEmpty()) {
            return null;
        }

        int lastIndex = log().size() - 1;
        return this.log().get(lastIndex);
    }

    @Override
    public String toString() {
        return log().stream().map(Object::toString).collect(joining("\n"));
    }
}
