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

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Context;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import javax.annotation.Nullable;
import java.text.MessageFormat;

import static ch.qos.logback.core.CoreConstants.LINE_SEPARATOR;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Optional.fromNullable;
import static java.lang.Integer.toHexString;
import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;

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

    @Override
    public String convert(final ILoggingEvent loggingEvent) {
        return fromNullable(loggingEvent.getThrowableProxy())
                .transform(createExceptionData(loggingEvent))
                .or(NO_MSG);
    }

    private Function<IThrowableProxy, String> createExceptionData(final ILoggingEvent loggingEvent) {
        return new Function<IThrowableProxy, String>() {
            @Nullable
            @Override
            public String apply(IThrowableProxy iThrowableProxy) {
                return getExceptionData(loggingEvent);
            }
        };
    }

    private String getExceptionData(final ILoggingEvent loggingEvent) {
        IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
        StackTraceElementProxy loggableStackTraceElement =
                findFirstStackTraceElementForClasses(throwableProxy, readStyxClassesFromProperty())
                        .or(fromNullable(getTopStackTraceElement(throwableProxy)))
                        .orNull();

        return fromNullable(loggableStackTraceElement)
                .transform(CREATE_EXCEPTION_MESSAGE)
                .or(NO_MSG);
    }

    private static final Function<StackTraceElementProxy, String> CREATE_EXCEPTION_MESSAGE = new Function<StackTraceElementProxy, String>() {
        @Nullable
        @Override
        public String apply(@Nullable StackTraceElementProxy loggableStackTraceElement) {
            String exceptionClass = loggableStackTraceElement.getStackTraceElement().getClassName();
            String exceptionMethod = loggableStackTraceElement.getStackTraceElement().getMethodName();
            String exceptionID = hashCodeForException(exceptionClass, exceptionMethod);
            return MessageFormat.format(SPLUNK_FRIENDLY_LOG_MESSAGE_TEMPLATE, exceptionClass, exceptionMethod, exceptionID);
        }
    };


    private Optional<StackTraceElementProxy> findFirstStackTraceElementForClasses(final IThrowableProxy throwableProxy, final String[] targetClasses) {
        return FluentIterable.from(collectThrowableProxies(throwableProxy))
                .transformAndConcat(collectStackTraceElements())
                .firstMatch(stackTraceElementForClasses(targetClasses));
    }

    private static Predicate<StackTraceElementProxy> stackTraceElementForClasses(final String[] targetClasses) {
        return new Predicate<StackTraceElementProxy>() {
            @Override
            public boolean apply(@Nullable StackTraceElementProxy stackTraceElementProxy) {
                return contains(stackTraceElementProxy.getSTEAsString(), targetClasses);
            }
        };
    }

    private static Function<IThrowableProxy, Iterable<StackTraceElementProxy>> collectStackTraceElements() {
        return new Function<IThrowableProxy, Iterable<StackTraceElementProxy>>() {
            @Nullable
            @Override
            public Iterable<StackTraceElementProxy> apply(@Nullable IThrowableProxy iThrowableProxy) {
                return iThrowableProxy != null ? asList(iThrowableProxy.getStackTraceElementProxyArray()) : EMPTY_LIST;
            }
        };
    }

    private String[] readStyxClassesFromProperty() {
        String[] targetClasses = TARGET_CLASSES_DEFAULT;
        Context ctx = getContext();
        String property = ctx.getProperty(TARGET_CLASSES_PROPERTY_NAME);
        if (!Strings.isNullOrEmpty(property)) {
            targetClasses = Iterables.toArray(Splitter.on(",").trimResults().split(property), String.class);
        } else {
            addError(MessageFormat.format("The '{0}' property should be present on logback configuration. Using default classname prefixes.",
                    TARGET_CLASSES_PROPERTY_NAME));
        }
        return targetClasses;
    }

    private Iterable<IThrowableProxy> collectThrowableProxies(final IThrowableProxy throwableProxy) {
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

    private StackTraceElementProxy getTopStackTraceElement(final IThrowableProxy throwableProxy) {
        StackTraceElementProxy stackTraceLine = null;
        StackTraceElementProxy[] stackTraceElementProxyArray = throwableProxy.getStackTraceElementProxyArray();
        if (stackTraceElementProxyArray != null && stackTraceElementProxyArray.length > 0) {
            stackTraceLine = stackTraceElementProxyArray[0];
        }
        return stackTraceLine;
    }

    private static boolean contains(final String stackTraceLine, final String[] targetClasses) {
        boolean retValue = false;
        for (String targetClass : targetClasses) {
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
