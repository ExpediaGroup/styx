package com.hotels.styx.server.netty.connectors;

import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FancyExceptionMatcherTest {
    @Test
    public void matchesExceptionWithoutCause() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class);

        assertThat(matcher.matchLevel(new Exception()), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException()), is(1));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException())), is(1));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException(new UnsupportedOperationException()))), is(1));
    }

    @Test
    public void matchesExceptionWithCause() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class, IllegalArgumentException.class);

        assertThat(matcher.matchLevel(new Exception()), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException()), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException())), is(2));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException(new UnsupportedOperationException()))), is(2));
    }

    @Test
    public void matchesExceptionWithTripleNesting() {
        FancyExceptionMatcher matcher = new FancyExceptionMatcher(IllegalStateException.class, IllegalArgumentException.class, UnsupportedOperationException.class);

        assertThat(matcher.matchLevel(new Exception()), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException()), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException())), is(0));
        assertThat(matcher.matchLevel(new IllegalStateException(new IllegalArgumentException(new UnsupportedOperationException()))), is(3));
    }
}