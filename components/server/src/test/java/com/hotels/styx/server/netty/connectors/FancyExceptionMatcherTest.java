package com.hotels.styx.server.netty.connectors;

import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FancyExceptionMatcherTest {
    @Test
    public void matchesExceptionWithoutCause() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class);

        assertThat(matcher.isMatch(new Exception()), is(false));
        assertThat(matcher.isMatch(new IllegalStateException()), is(true));
        assertThat(matcher.isMatch(new IllegalStateException(new IllegalArgumentException())), is(true));
    }

    @Test
    public void matchesExceptionWithCause() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class, IllegalArgumentException.class);

        assertThat(matcher.isMatch(new Exception()), is(false));
        assertThat(matcher.isMatch(new IllegalStateException()), is(false));
        assertThat(matcher.isMatch(new IllegalStateException(new IllegalArgumentException())), is(true));
    }

    @Test
    public void matchesExceptionWithTripleNesting() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class, IllegalArgumentException.class, UnsupportedOperationException.class);

        assertThat(matcher.isMatch(new Exception()), is(false));
        assertThat(matcher.isMatch(new IllegalStateException()), is(false));
        assertThat(matcher.isMatch(new IllegalStateException(new IllegalArgumentException())), is(false));
        assertThat(matcher.isMatch(new IllegalStateException(new IllegalArgumentException(new UnsupportedOperationException()))), is(true));
    }
}