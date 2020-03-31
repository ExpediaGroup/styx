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

import org.junit.jupiter.api.Test;

import static com.hotels.styx.common.Collections.listOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.StringEndsWith.endsWith;

class ThrowableSanitiserTest {

    SanitisedHttpHeaderFormatter formatter = new SanitisedHttpHeaderFormatter(
            listOf(), listOf("secret-cookie", "private-cookie")
    );

    static Exception throwAndReturn(Exception ex) {
        try {
            throw ex;
        } catch (Exception e) {
            return e;
        }
    }

    @Test
    public void messagesWithNoCookiesAreNotChanged() {
        Exception e = throwAndReturn(new Exception("This does not contain any cookies."));
        Throwable proxy = ThrowableSanitiser.instance().sanitise(e, formatter);

        String prefix = "java.lang.Exception: ";
        assertThat(proxy.getMessage(), equalTo(prefix + e.getMessage()));
        assertThat(proxy.getLocalizedMessage(), equalTo(prefix + e.getLocalizedMessage()));
        assertThat(proxy.toString(), equalTo("Sanitized: " + e.toString()));
    }

    @Test
    public void messagesWithUnrecognizedCookiesAreNotChanged() {
        Exception e = throwAndReturn(new Exception("Some cookies: cookie1=c1;cookie2=c2"));
        Throwable proxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(proxy.getMessage(), endsWith(e.getMessage()));
        assertThat(proxy.getLocalizedMessage(), endsWith(e.getLocalizedMessage()));
        assertThat(proxy.toString(), endsWith(e.toString()));
    }

    @Test
    public void messagesWithRecognizedCookiesAreSanitized() {
        Exception e = throwAndReturn(new Exception("Some cookies: cookie1=c1;secret-cookie=secret;cookie2=c2;private-cookie=private"));
        Throwable proxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(proxy.getMessage(), containsString("cookie1=c1"));
        assertThat(proxy.getMessage(), containsString("cookie2=c2"));
        assertThat(proxy.getMessage(), containsString("secret-cookie=****"));
        assertThat(proxy.getMessage(), containsString("private-cookie=****"));
    }

    @Test
    public void exceptionCausesAreSanitized() {
        Exception inner = throwAndReturn(new Exception("Inner: cookie1=c1;secret-cookie=secret"));
        Exception outer = throwAndReturn(new Exception("Outer: cookie2=c2;private-cookie=private", inner));
        Throwable outerProxy = ThrowableSanitiser.instance().sanitise(outer, formatter);
        assertThat(outerProxy.getMessage(), containsString("cookie2=c2"));
        assertThat(outerProxy.getMessage(), containsString("private-cookie=****"));
        assertThat(outerProxy.getCause().getMessage(), containsString("cookie1=c1"));
        assertThat(outerProxy.getCause().getMessage(), containsString("secret-cookie=****"));
    }

    @Test
    public void nullMessagesAreAllowed() {
        Exception e = throwAndReturn(new Exception((String) null));
        Throwable proxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(proxy.getMessage(), equalTo("java.lang.Exception: null"));
    }

    @Test
    public void exceptionsWithNoDefaultConstructorAreNotProxied() {
        Exception e = throwAndReturn(new NoDefaultConstructorException("Ooops, no default constructor"));
        Throwable notProxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(notProxy.getMessage(), equalTo("Ooops, no default constructor"));
        assertThat(notProxy.getClass().getSuperclass(), not(instanceOf(NoDefaultConstructorException.class))); // i.e. not proxied.
    }

    @Test
    public void exceptionsOfFinalClassAreNotProxied() {
        Exception e = throwAndReturn(new FinalClassException());
        Throwable notProxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(notProxy.getMessage(), equalTo("This is a final class."));
        assertThat(notProxy.getClass().getSuperclass(), not(instanceOf(FinalClassException.class))); // i.e. not proxied.
    }

    @Test
    public void messagesWithInvalidCookiesAreSanitized() {
        Exception e = throwAndReturn(new Exception("Some cookies: cookie1=c1;secret-cookie=secret;bad-cookie=bad\u0000bad;private-cookie=private"));
        Throwable proxy = ThrowableSanitiser.instance().sanitise(e, formatter);
        assertThat(proxy.getMessage(), containsString("cookie1=c1"));
        assertThat(proxy.getMessage(), containsString("bad-cookie=bad\u0000bad"));
        assertThat(proxy.getMessage(), containsString("secret-cookie=****"));
        assertThat(proxy.getMessage(), containsString("private-cookie=****"));
    }

    static class NoDefaultConstructorException extends Exception {
        NoDefaultConstructorException(String msg) {
            super(msg);
        }
    }

    static final class FinalClassException extends Exception {
        FinalClassException() {
            super("This is a final class.");
        }
    }
}