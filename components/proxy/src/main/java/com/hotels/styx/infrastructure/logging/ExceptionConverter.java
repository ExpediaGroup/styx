/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Context;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static ch.qos.logback.core.CoreConstants.LINE_SEPARATOR;
import static com.google.common.base.Charsets.UTF_8;
import static com.hotels.styx.common.Strings.isNotEmpty;
import static java.lang.Integer.toHexString;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;

/**
 * Logback converter to log Splunk friendly exception data.
 * Tries to find the first target class in the available stacktraces from the innermost exception recursively.
 * If no target classes can be found, reverts to the first available class on the top of the stacktrace.
 * WARNING: This converter is only capable of parsing MDC conversion words and does not support any other expressions in its 1st parameter!
 *
 * @author Gabor_Arki
 * @author Zoltan_Varga
 * @author fgizaw
 */
public class ExceptionConverter extends ClassicConverter {
    public static final String TARGET_CLASSES_PROPERTY_NAME = "targetclasses";
    private static final String SPLUNK_FRIENDLY_LOG_MESSAGE_TEMPLATE = "[exceptionClass={0}, exceptionMethod={1}, exceptionID={2}]"
            + LINE_SEPARATOR;
    private static final String[] TARGET_CLASSES_DEFAULT = {"com.hotels.styx"};
    private static final int MAXIMUM_ALLOWED_DEPTH = 10;
    private static final HashFunction MD5_HASHING = Hashing.md5();
    private static final String NO_MSG = "";

    private String[] styxClasses;

    @Override
    public void start() {
        styxClasses = readStyxClassesFromProperty();
        super.start();
    }

    @Override
    public String convert(final ILoggingEvent loggingEvent) {
        return Optional.ofNullable(loggingEvent)
                .map(this::getExceptionData)
                .orElse(NO_MSG);
    }

    private String getExceptionData(final ILoggingEvent loggingEvent) {
        return Optional.ofNullable(loggingEvent.getThrowableProxy())
                .map(proxy -> findStyxStackTraceElement(proxy).orElse(getTopElement(proxy)))
                .map(ExceptionConverter::createExceptionMessage)
                .orElse(NO_MSG);
    }

    private static String createExceptionMessage(StackTraceElementProxy loggableStackTraceElement) {
        String exceptionClass = loggableStackTraceElement.getStackTraceElement().getClassName();
        String exceptionMethod = loggableStackTraceElement.getStackTraceElement().getMethodName();
        String exceptionID = hashCodeForException(exceptionClass, exceptionMethod);
        return MessageFormat.format(SPLUNK_FRIENDLY_LOG_MESSAGE_TEMPLATE, exceptionClass, exceptionMethod, exceptionID);
    }

    private Optional<StackTraceElementProxy> findStyxStackTraceElement(final IThrowableProxy throwableProxy) {
        return collectThrowableProxies(throwableProxy).stream()
                .filter(Objects::nonNull)
                .flatMap(proxy -> Arrays.stream(proxy.getStackTraceElementProxyArray()))
                .filter(steProxy -> containsStyxClasses(steProxy.getSTEAsString()))
                .findFirst();
    }

    private String[] readStyxClassesFromProperty() {
        String[] targetClasses = TARGET_CLASSES_DEFAULT;
        Context ctx = getContext();
        String property = ctx.getProperty(TARGET_CLASSES_PROPERTY_NAME);
        if (isNotEmpty(property)) {
            targetClasses = stream(property.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);
        } else {
            addInfo("The '" + TARGET_CLASSES_PROPERTY_NAME + "' property should be present on logback configuration. Using default classname prefixes.");
        }
        return targetClasses;
    }

    private Collection<IThrowableProxy> collectThrowableProxies(final IThrowableProxy throwableProxy) {
        IThrowableProxy[] throwableProxyArray = new IThrowableProxy[MAXIMUM_ALLOWED_DEPTH];
        IThrowableProxy actualThrowableProxy = throwableProxy;
        for (int i = 0; i < throwableProxyArray.length; i++) {
            if (actualThrowableProxy != null) {
                throwableProxyArray[i] = actualThrowableProxy;
                actualThrowableProxy = actualThrowableProxy.getCause();
            } else {
                break;
            }
        }
        return asList(throwableProxyArray);
    }

    private StackTraceElementProxy getTopElement(final IThrowableProxy throwableProxy) {
        StackTraceElementProxy stackTraceLine = null;
        StackTraceElementProxy[] stackTraceElementProxyArray = throwableProxy.getStackTraceElementProxyArray();
        if (stackTraceElementProxyArray != null && stackTraceElementProxyArray.length > 0) {
            stackTraceLine = stackTraceElementProxyArray[0];
        }
        return stackTraceLine;
    }

    private boolean containsStyxClasses(final String stackTraceLine) {
        boolean retValue = false;
        for (String targetClass : styxClasses) {
            if (stackTraceLine.contains(targetClass)) {
                retValue = true;
                break;
            }
        }
        return retValue;
    }

    private static String hashCodeForException(final String exceptionClass, final String exceptionMethod) {
        final String hashString = exceptionClass + exceptionMethod;
        int md5Hash = MD5_HASHING.newHasher()
                .putString(hashString, UTF_8)
                .hash()
                .asInt();
        return toHexString(md5Hash);
    }

}
