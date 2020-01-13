package com.hotels.styx.common.format;

import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.StringEndsWith.endsWith;

class SanitisingThrowableProxyTest {

    SanitisedHttpHeaderFormatter formatter = new SanitisedHttpHeaderFormatter(
            emptyList(), asList("secret-cookie", "private-cookie")
    );

    Exception exception(String msg) {
        try {
            throw new Exception(msg);
        } catch (Exception e) {
            return e;
        }
    }

    Exception exception(String msg, Exception cause) {
        try {
            throw new Exception(msg, cause);
        } catch (Exception e) {
            return e;
        }
    }

    @Test
    public void messagesWithNoCookiesAreNotChanged() {
        Exception e = exception("This does not contain any cookies.");
        SanitisingThrowableProxy proxy = new SanitisingThrowableProxy(e, formatter);

        String prefix = "java.lang.Exception: ";
        assertThat(proxy.getMessage(), equalTo(prefix + e.getMessage()));
        assertThat(proxy.getLocalizedMessage(), equalTo(prefix + e.getLocalizedMessage()));
        assertThat(proxy.toString(), equalTo("Sanitized: " + e.toString()));
    }

    @Test
    public void messagesWithUnrecognizedCookiesAreNotChanged() {
        Exception e = exception("Some cookies: cookie1=c1;cookie2=c2");
        SanitisingThrowableProxy proxy = new SanitisingThrowableProxy(e, formatter);
        assertThat(proxy.getMessage(), endsWith(e.getMessage()));
        assertThat(proxy.getLocalizedMessage(), endsWith(e.getLocalizedMessage()));
        assertThat(proxy.toString(), endsWith(e.toString()));
    }

    @Test
    public void messagesWithRecognizedCookiesAreSanitized() {
        Exception e = exception("Some cookies: cookie1=c1;secret-cookie=secret;cookie2=c2;private-cookie=private");
        SanitisingThrowableProxy proxy = new SanitisingThrowableProxy(e, formatter);
        assertThat(proxy.getMessage(), containsString("cookie1=c1"));
        assertThat(proxy.getMessage(), containsString("cookie2=c2"));
        assertThat(proxy.getMessage(), containsString("secret-cookie=****"));
        assertThat(proxy.getMessage(), containsString("private-cookie=****"));
    }

    @Test
    public void exceptionCausesAreSanitized() {
        Exception inner = exception("Inner: cookie1=c1;secret-cookie=secret");
        Exception outer = exception("Outer: cookie2=c2;private-cookie=private", inner);
        SanitisingThrowableProxy outerProxy = new SanitisingThrowableProxy(outer, formatter);
        assertThat(outerProxy.getMessage(), containsString("cookie2=c2"));
        assertThat(outerProxy.getMessage(), containsString("private-cookie=****"));
        assertThat(outerProxy.getCause().getMessage(), containsString("cookie1=c1"));
        assertThat(outerProxy.getCause().getMessage(), containsString("secret-cookie=****"));
    }

    @Test
    public void nullMessagesAreAllowed() {
        Exception e = exception(null);
        SanitisingThrowableProxy proxy = new SanitisingThrowableProxy(e, formatter);
        assertThat(proxy.getMessage(), equalTo("java.lang.Exception: null"));
    }

    @Test
    public void messagesWithInvalidCookiesAreSanitized() {
        Exception e = exception("Some cookies: cookie1=c1;secret-cookie=secret;bad-cookie=bad\u0000bad;private-cookie=private");
        SanitisingThrowableProxy proxy = new SanitisingThrowableProxy(e, formatter);
        assertThat(proxy.getMessage(), containsString("cookie1=c1"));
        assertThat(proxy.getMessage(), containsString("bad-cookie=bad\u0000bad"));
        assertThat(proxy.getMessage(), containsString("secret-cookie=****"));
        assertThat(proxy.getMessage(), containsString("private-cookie=****"));
    }
}