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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.equalTo;

public final class LoggingEventMatcher extends TypeSafeMatcher<ILoggingEvent> {
    private final Level level;
    private final Matcher<String> message;
    private final LoggedExceptionMatcher exception;

    private LoggingEventMatcher(Level level, String message) {
        this.level = requireNonNull(level);
        this.message = new RegExMatcher(message);
        this.exception = null;
    }

    private LoggingEventMatcher(Level level, String message, String exceptionClass, String exceptionMessage) {
        this.level = requireNonNull(level);
        this.message = new RegExMatcher(message);
        this.exception = new LoggedExceptionMatcher(exceptionClass, new RegExMatcher(exceptionMessage));
    }

    public static Matcher<ILoggingEvent> loggingEvent(Level level, String message, String exceptionClass, String exceptionMessage) {
        return new LoggingEventMatcher(level, message, exceptionClass, exceptionMessage);
    }

    /**
     * Matches a log event.
     * <p>
     * This variant is useful for error logs, because it allows exception class and
     * exception message to be verified by the test.
     *
     * @param level            Log level
     * @param message          Log message
     * @param exceptionClass   Exception class
     * @param exceptionMessage Exception message
     * @return
     */
    public static Matcher<ILoggingEvent> loggingEvent(Level level, String message, Class<? extends Throwable> exceptionClass, String exceptionMessage) {
        return loggingEvent(level, message, exceptionClass.getName(), exceptionMessage);
    }

    /**
     * Matches a log event.
     * <p>
     * WARNING: this is not useful for matching ERROR level logs, as it does not allow
     * one to specify exception class or message that are associated with ERROR logs.
     *
     * @param level   Log level
     * @param message Log message
     * @return
     */
    public static Matcher<ILoggingEvent> loggingEvent(Level level, String message) {
        return new LoggingEventMatcher(level, message);
    }

    @Override
    protected void describeMismatchSafely(ILoggingEvent item, Description description) {
        description.appendText(format("loggingEvent(level=%s, message='%s'", item.getLevel(), item.getFormattedMessage()));

        IThrowableProxy throwableProxy = item.getThrowableProxy();

        if (throwableProxy != null) {
            description.appendText(" ");
            describeException(description, throwableProxy.getClassName(), throwableProxy.getMessage());
        }

        description.appendText(")");
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(format("loggingEvent(level=%s, message=%s", level, message));

        if (exception != null) {
            description.appendText(" ");
            describeException(description, exception.exceptionClass, exception.exceptionMessage);
        }

        description.appendText(")");
    }

    @Override
    protected boolean matchesSafely(ILoggingEvent event) {
        return this.level.equals(event.getLevel())
                && this.message.matches(event.getFormattedMessage().replace(System.lineSeparator(), ""))
                && exceptionMatches(event.getThrowableProxy());
    }

    private boolean exceptionMatches(IThrowableProxy throwableProxy) {
        return exception == null
                ? throwableProxy == null
                : exception.matches(throwableProxy);
    }

    private static final class LoggedExceptionMatcher extends TypeSafeMatcher<IThrowableProxy> {
        private final String exceptionClass;
        private final Matcher<String> exceptionMessage;

        private LoggedExceptionMatcher(String exceptionClass, Matcher<String> exceptionMessage) {
            this.exceptionClass = exceptionClass;
            this.exceptionMessage = exceptionMessage;
        }

        @Override
        protected void describeMismatchSafely(IThrowableProxy item, Description mismatchDescription) {
            describeException(mismatchDescription, item.getClassName(), item.getMessage());
        }

        @Override
        public void describeTo(Description description) {
            describeException(description, exceptionClass, exceptionMessage);
        }

        @Override
        protected boolean matchesSafely(IThrowableProxy item) {
            return exceptionClass.equals(item.getClassName())
                    && exceptionMessage.matches(item.getMessage().replace(System.lineSeparator(), ""))
                    && item.getStackTraceElementProxyArray().length > 0;
        }
    }

    private static void describeException(Description description, String className, Object message) {
        description.appendText(format("exception(class=%s, message=%s)", className, message));
    }

    /**
     * @deprecated Use {@link ContainsExactlyOneMatcher#containsExactlyOne(Object)}
     */
    @Deprecated
    public static <T> Matcher<Iterable<T>> containsExactlyOne(T item) {
        return ContainsExactlyOneMatcher.containsExactlyOne(equalTo(item));
    }

    /**
     * @deprecated Use {@link ContainsExactlyOneMatcher#containsExactlyOne(Matcher)}
     */
    @Deprecated
    public static <T> Matcher<Iterable<T>> containsExactlyOne(Matcher<T> itemMatcher) {
        return ContainsExactlyOneMatcher.containsExactlyOne(itemMatcher);
    }
}