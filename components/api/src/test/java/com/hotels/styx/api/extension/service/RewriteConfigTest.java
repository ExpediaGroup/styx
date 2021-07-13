/*
  Copyright (C) 2013-2021 Expedia Inc.

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
