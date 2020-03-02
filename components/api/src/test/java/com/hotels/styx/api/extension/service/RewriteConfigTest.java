package com.hotels.styx.api.extension.service;

import org.hamcrest.CoreMatchers;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class RewriteConfigTest {

    @Test
    public void testSubstitutions() {
        String urlPattern = "\\/foo\\/(a|b|c)(\\/.*)?";

        RewriteConfig config = new RewriteConfig(urlPattern, "/bar/$1$2");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("/bar/b/something"));

        config = new RewriteConfig(urlPattern, "/bar/$1/x$2");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("/bar/b/x/something"));

        config = new RewriteConfig(urlPattern, "/bar/$1/x$2/y");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("/bar/b/x/something/y"));

        config = new RewriteConfig(urlPattern, "$1/x$2/y");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("b/x/something/y"));

        config = new RewriteConfig(urlPattern, "$1$2/y");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("b/something/y"));

        config = new RewriteConfig(urlPattern, "$1$2");
        assertThat(config.rewrite("/foo/b/something").get(), CoreMatchers.equalTo("b/something"));
    }
}
