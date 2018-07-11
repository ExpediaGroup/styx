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
package com.hotels.styx.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.StatusManager;
import com.hotels.styx.api.exceptions.NoAvailableHostsException;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.infrastructure.logging.ExceptionConverter.TARGET_CLASSES_PROPERTY_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


public class ExceptionConverterTest {
    final ILoggingEvent loggingEvent = newErrorLoggingEvent(new NoAvailableHostsException(GENERIC_APP));

    private ILoggingEvent newErrorLoggingEvent(Throwable throwable) {
        LoggingEvent loggingEvent = new LoggingEvent();
        loggingEvent.setLevel(Level.ERROR);
        loggingEvent.setThrowableProxy(new ThrowableProxy(throwable));
        return loggingEvent;
    }


    @Test
    public void extractsTheExceptionClassMethodAndCreatesAUniqueExceptionId() throws Exception {
        ExceptionConverter converter = newExceptionConverter();
        assertThat(converter.convert(loggingEvent).trim(), endsWith("[exceptionClass=com.hotels.styx.infrastructure.logging.ExceptionConverterTest, exceptionMethod=<init>, exceptionID=10d7182c]"));
    }

    @Test
    public void gracefullyHandlesExceptionInstancesWithoutStackTrace() throws Exception {
        ExceptionConverter converter = newExceptionConverter();
        ILoggingEvent errorEvent = newErrorLoggingEvent(new NoStackException());

        assertThat(converter.convert(errorEvent), endsWith(""));
    }

    static class NoStackException extends Throwable {
        public NoStackException() {
            super("This is a test exception without stack trace.", null, false, false);
        }
    }

    private ExceptionConverter newExceptionConverter() {
        return newExceptionConverter(new ContextBase());
    }

    private ExceptionConverter newExceptionConverter(ContextBase context) {
        ExceptionConverter converter = new ExceptionConverter();
        converter.setContext(context);
        converter.start();
        return converter;
    }


    @Test
    public void prioritizeTargetClassStackTraceElementsOverTheRootOnes() throws Exception {
        ExceptionConverter converter = newExceptionConverter();
        converter.getContext()
                .putProperty(TARGET_CLASSES_PROPERTY_NAME, "TargetClass");

        ILoggingEvent loggingEvent = newErrorLoggingEvent(new TargetClass().blow());
        assertThat(converter.convert(loggingEvent).trim(), endsWith("[exceptionClass=com.hotels.styx.infrastructure.logging.ExceptionConverterTest$TargetClass, exceptionMethod=blow, exceptionID=8b93d529]"));
    }

    @Test
    public void errorsIfTargetClassPropertyIsEmpty() throws Exception {
        ContextBase contextBase = new ContextBase();
        StatusManager statusManager = mock(StatusManager.class);
        contextBase.setStatusManager(statusManager);
        ExceptionConverter converter = newExceptionConverter(contextBase);
        converter.getContext()
                .putProperty(TARGET_CLASSES_PROPERTY_NAME, "");

        ILoggingEvent loggingEvent = newErrorLoggingEvent(new TargetClass().blow());
        converter.convert(loggingEvent);

        verify(statusManager).add(any(ErrorStatus.class));
    }

    static class TargetClass {

        public RuntimeException blow() {
            return new RuntimeException("blow");
        }

    }
}
