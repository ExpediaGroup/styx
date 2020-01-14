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
