package com.hotels.styx.common.format;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a {@link Throwable} so that the message can be modified to sanitize the values of any recognised cookies.
 * Any cause is similarly wrapped before being returned.
 */
public class SanitisingThrowableMethodInterceptor implements MethodInterceptor {

    private static final ConcurrentHashMap<String, Pattern> COOKIE_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static Pattern cookieNamePattern(String cookieName) {
        return COOKIE_NAME_PATTERN_CACHE.computeIfAbsent(cookieName, name -> Pattern.compile(name + "\\s*="));
    }

    private final Throwable throwable;
    private final SanitisedHttpHeaderFormatter formatter;

    /**
     * Enhance a throwable, using the given {@link SanitisedHttpHeaderFormatter} to recognise and sanitise cookie values.
     * @param formatter provides the sanitising logic
     */
    public SanitisingThrowableMethodInterceptor(Throwable throwable, SanitisedHttpHeaderFormatter formatter) {
        this.throwable = throwable;
        this.formatter = formatter;
    }

    @Override
    public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args,
                            MethodProxy proxy) throws Throwable {
        switch (method.getName()) {
            case "getMessage":
                return getMessage();
            case "getLocalizedMessage":
                return getLocalizedMessage();
            case "toString":
                return toString();
            case "getCause":
                return getCause();
            default:
                return proxy.invoke(throwable, args);
        }
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

    public String getMessage() throws Throwable {
        return throwable.getClass().getName() + ": " + sanitiseCookies(throwable.getMessage());
    }

    public String getLocalizedMessage() throws Throwable {
        return throwable.getClass().getName() + ": " + sanitiseCookies(throwable.getLocalizedMessage());
    }

    public synchronized Throwable getCause() throws Throwable {
        Throwable cause = throwable.getCause();
        return cause == null ? null : (Throwable) Enhancer.create(cause.getClass(), new SanitisingThrowableMethodInterceptor(cause, formatter));
    }

    public String toString() {
        return "Sanitized: " + throwable.toString();
    }
}

