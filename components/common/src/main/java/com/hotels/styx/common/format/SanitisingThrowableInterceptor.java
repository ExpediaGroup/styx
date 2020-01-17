/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.common.format;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts methods of a {@link Throwable} so that the message can be modified to sanitize the values of any recognised cookies.
 * Any cause is similarly wrapped before being returned.
 */
public class SanitisingThrowableInterceptor {

    private static final ConcurrentHashMap<String, Pattern> COOKIE_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static Pattern cookieNamePattern(String cookieName) {
        return COOKIE_NAME_PATTERN_CACHE.computeIfAbsent(cookieName, name -> Pattern.compile(name + "\\s*="));
    }

    private final Throwable target;
    private final SanitisedHttpHeaderFormatter formatter;

    /**
     * Enhance a throwable, using the given {@link SanitisedHttpHeaderFormatter} to recognise and sanitise cookie values.
     * @param target the target throwable to enhance
     * @param formatter provides the sanitising logic
     */
    public SanitisingThrowableInterceptor(Throwable target, SanitisedHttpHeaderFormatter formatter) {
        this.target = target;
        this.formatter = formatter;
    }

    /**
     * Default method interceptor, to delegate all other method calls to the target.
     * @param method the method being proxied
     * @param args the arguments of the method being proxied
     * @return the response from the target object
     * @throws Exception if the target method throws an exception
     */
    @RuntimeType
    public Object intercept(@Origin Method method, @AllArguments Object[] args) throws Exception {
        return method.invoke(target, args);
    }

    private String sanitiseCookies(String message) {
        if (message == null) {
            return null;
        }
        return formatter.cookiesToHide().stream()
                // Find earliest 'cookiename=' in message
                .map(cookie -> cookieNamePattern(cookie).matcher(message))
                .filter(Matcher::find)
                .map(Matcher::start)
                .min(Integer::compareTo)
                .map(cookiesStart -> {
                    // Assume the cookies run to the end of the message
                    String cookies = message.substring(cookiesStart);
                    String sanitizedCookies = formatter.formatCookieHeaderValue(cookies);
                    return message.substring(0, cookiesStart) + sanitizedCookies;
                })
                .orElse(message);
    }

    public String getMessage() {
        return target.getClass().getName() + ": " + sanitiseCookies(target.getMessage());
    }

    public String getLocalizedMessage() {
        return target.getClass().getName() + ": " + sanitiseCookies(target.getLocalizedMessage());
    }

    public Throwable getCause() {
        Throwable cause = target.getCause();
        return cause == null ? null : SanitisingThrowableFactory.instance().create(cause, formatter);
    }

    public Throwable fillInStackTrace() {
        return target;
    }

    public String toString() {
        return "Sanitized: " + target.toString();
    }
}

