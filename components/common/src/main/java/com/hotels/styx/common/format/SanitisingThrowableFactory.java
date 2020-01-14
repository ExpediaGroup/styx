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

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public class SanitisingThrowableFactory {

    private static final SanitisingThrowableFactory INSTANCE = new SanitisingThrowableFactory();

    public static SanitisingThrowableFactory instance() {
        return INSTANCE;
    }

    private ConcurrentHashMap<Class<?>, Factory> cache = new ConcurrentHashMap<>();

    private SanitisingThrowableFactory() { /* Just making this private */ }

    public Throwable create(Throwable target, SanitisedHttpHeaderFormatter formatter) {
        Factory factory = cache.computeIfAbsent(target.getClass(), clazz -> (Factory) Enhancer.create(clazz, new Passthrough()));
        SanitisingThrowableMethodInterceptor interceptor = new SanitisingThrowableMethodInterceptor(target, formatter);
        return (Throwable) factory.newInstance(interceptor);
    }

    static class Passthrough implements MethodInterceptor {

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            return proxy.invokeSuper(obj, args);
        }
    }
}
