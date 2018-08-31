/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.server.routing;

import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AntlrMatcherTest {

    @Test
    public void matchesHttpProtocol() throws Exception {
        AntlrMatcher matcher = AntlrMatcher.antlrMatcher("protocol() == 'https'");
        assertThat(matcher.apply(get("/path").secure(true).build()), is(true));
        assertThat(matcher.apply(get("/path").secure(false).build()), is(false));
    }

}