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

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.TypeCache;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.bytebuddy.TypeCache.Sort.SOFT;

/**
 * Wraps a {@link Throwable} in a dynamic proxy that sanitizes its message to hide sensitive cookie
 * information. The supplied Throwable must be non-final, and must have a no-args constructor. The proxy-intercepted
 * methods are handled by an instance of {@link Interceptor}
 */
public class ThrowableSanitiser {

    private static final Logger LOG = LoggerFactory.getLogger(ThrowableSanitiser.class);

    private static final ThrowableSanitiser INSTANCE = new ThrowableSanitiser();

    /**
     * Return the singleton instance of this factory.
     * @return the singleton instance.
     */
    public static ThrowableSanitiser instance() {
        return INSTANCE;
    }

    private final TypeCache<String> typeCache = new TypeCache<>(SOFT);

    private ThrowableSanitiser() { /* Just making this private */ }

    /**
     * Wrap a {@link Throwable} in a dynamic proxy that sanitizes its message to hide sensitive cookie
     * information. The supplied Throwable must be non-final, and must have a no-args constructor. If the proxy
     * cannot be created for any reason (including that it is proxying a final class, or one without a no-args constructor),
     * then a warning is logged and the unproxied Throwable is returned back to the caller.
     * @param original the Throwable to be proxied
     * @param formatter hides the sensitive cookies.
     * @return the proxied Throwable, or the original throwable if it cannot be proxied.
     */
    public Throwable sanitise(Throwable original, SanitisedHttpHeaderFormatter formatter) {

        Class<?> clazz = original.getClass();
        try {
            Constructor<?> defaultConstructor = clazz.getConstructor();

            Class<?> proxyClass = typeCache.findOrInsert(getClass().getClassLoader(), clazz.getName(), () ->
                    new ByteBuddy()
                            .subclass(clazz)
                            .defineField("methodInterceptor", Interceptor.class, Visibility.PRIVATE)
                            .defineConstructor(Visibility.PUBLIC)
                            .withParameters(Interceptor.class)
                            .intercept(FieldAccessor.ofField("methodInterceptor").setsArgumentAt(0)
                                    .andThen(MethodCall.invoke(defaultConstructor)))
                            .method(ElementMatchers.any())
                            .intercept(MethodDelegation.toField("methodInterceptor"))
                            .make()
                            .load(getClass().getClassLoader())
                            .getLoaded());

            return (Throwable) proxyClass
                    .getConstructor(Interceptor.class)
                    .newInstance(new Interceptor(original, formatter));
        } catch (Exception e) {
            LOG.warn("Unable to proxy throwable class {} - {}", clazz, e.toString()); // No need to log stack trace here
        }
        return original;
    }

    /**
     * Intercepts methods of a {@link Throwable} so that the message can be modified to sanitize the values of any recognised cookies.
     * Any cause is similarly wrapped before being returned.
     */
    public static class Interceptor {

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
        public Interceptor(Throwable target, SanitisedHttpHeaderFormatter formatter) {
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
            return cause == null ? null : instance().sanitise(cause, formatter);
        }

        public Throwable fillInStackTrace() {
            return target;
        }

        public String toString() {
            return "Sanitized: " + target.toString();
        }
    }
}
