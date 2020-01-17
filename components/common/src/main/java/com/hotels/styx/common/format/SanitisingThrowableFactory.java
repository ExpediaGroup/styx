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
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

import static net.bytebuddy.TypeCache.Sort.SOFT;

/**
 * Factory for wrapping a {@link Throwable} in a dynamic proxy that sanitizes its message to hide sensitive cookie
 * information. The supplied Throwable must be non-final, and must have a no-args constructor. The proxy-intercepted
 * methods are handled by an instance of {@link SanitisingThrowableInterceptor}
 */
public class SanitisingThrowableFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SanitisingThrowableFactory.class);

    private static final SanitisingThrowableFactory INSTANCE = new SanitisingThrowableFactory();

    /**
     * Return the singleton instance of this factory.
     * @return the singleton instance.
     */
    public static SanitisingThrowableFactory instance() {
        return INSTANCE;
    }

    private final TypeCache<String> typeCache = new TypeCache<>(SOFT);

    private SanitisingThrowableFactory() { /* Just making this private */ }

    /**
     * Wrap a {@link Throwable} in a dynamic proxy that sanitizes its message to hide sensitive cookie
     * information. The supplied Throwable must be non-final, and must have a no-args constructor. If the proxy
     * cannot be created for any reason (including that it is proxying a final class, or one without a no-args constructor),
     * then a warning is logged and the unproxied Throwable is returned back to the caller.
     * @param target the Throwable to be proxied
     * @param formatter hides the sensitive cookies.
     * @return the proxied Throwable, or the supplied Target if it cannot be proxied.
     */
    public Throwable create(Throwable target, SanitisedHttpHeaderFormatter formatter) {

        Class<?> clazz = target.getClass();
        try {
            Constructor<?> defaultConstructor = clazz.getConstructor();

            Class<?> proxyClass = typeCache.findOrInsert(getClass().getClassLoader(), clazz.getName(), () ->
                    new ByteBuddy()
                            .subclass(clazz)
                            .defineField("methodInterceptor", SanitisingThrowableInterceptor.class, Visibility.PRIVATE)
                            .defineConstructor(Visibility.PUBLIC)
                            .withParameters(SanitisingThrowableInterceptor.class)
                            .intercept(FieldAccessor.ofField("methodInterceptor").setsArgumentAt(0)
                                    .andThen(MethodCall.invoke(defaultConstructor)))
                            .method(ElementMatchers.any())
                            .intercept(MethodDelegation.toField("methodInterceptor"))
                            .make()
                            .load(getClass().getClassLoader())
                            .getLoaded());

            return (Throwable) proxyClass
                    .getConstructor(SanitisingThrowableInterceptor.class)
                    .newInstance(new SanitisingThrowableInterceptor(target, formatter));
        } catch (Exception e) {
            LOG.warn("Unable to proxy throwable class {} - {}", clazz, e.toString()); // No need to log stack trace here
        }
        return target;
    }
}
