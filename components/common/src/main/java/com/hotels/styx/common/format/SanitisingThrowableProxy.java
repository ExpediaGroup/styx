package com.hotels.styx.common.format;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a {@link Throwable} so that the message can be modified to sanitize the values of any recognised cookies.
 * Any cause is similarly wrapped before being returned.
 */
public class SanitisingThrowableProxy extends Throwable {

    private static final ConcurrentHashMap<String, Pattern> COOKIE_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static Pattern cookieNamePattern(String cookieName) {
        return COOKIE_NAME_PATTERN_CACHE.computeIfAbsent(cookieName, name -> Pattern.compile(name + "\\s*="));
    }

    private final Throwable throwable;
    private final SanitisedHttpHeaderFormatter formatter;

    /**
     * Wrap a throwable, using the given {@link SanitisedHttpHeaderFormatter} to recognise and sanitise cookie values.
     * @param throwable the throwable to wrap
     * @param formatter provides the sanitising logic
     */
    public SanitisingThrowableProxy(Throwable throwable, SanitisedHttpHeaderFormatter formatter) {
        this.throwable = throwable;
        this.formatter = formatter;
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

    /**
     * Returns the class of the wrapped Throwable.
     * @return the class of the wrapped Throwable.
     */
    public Class<? extends Throwable> delegateClass() {
        return throwable.getClass();
    }

    @Override
    public String getMessage() {
        return throwable.getClass().getName() + ": " + sanitiseCookies(throwable.getMessage());
    }

    @Override
    public String getLocalizedMessage() {
        return throwable.getClass().getName() + ": " + sanitiseCookies(throwable.getLocalizedMessage());
    }

    @Override
    public synchronized Throwable getCause() {
        Throwable cause = throwable.getCause();
        return cause == null ? null : new SanitisingThrowableProxy(cause, formatter);
    }

    @Override
    public synchronized Throwable initCause(Throwable cause) {
        return throwable.initCause(cause);
    }

    @Override
    public String toString() {
        return "Sanitized: " + throwable.toString();
    }

    @Override
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        throwable.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        throwable.printStackTrace(s);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        if (throwable != null) {
            throwable.fillInStackTrace();
        }
        return this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return throwable.getStackTrace();
    }
}

