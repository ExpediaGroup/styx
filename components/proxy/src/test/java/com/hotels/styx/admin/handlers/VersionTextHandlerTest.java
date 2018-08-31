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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.Resource;
import com.hotels.styx.common.io.ResourceFactory;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class VersionTextHandlerTest {
    @Test
    public void canProvideASingleVersionTextFile() {
        VersionTextHandler handler = new VersionTextHandler(resources("classpath:/versions/version1.txt"));

        FullHttpResponse response = waitForResponse(handler.handle( get("/version.txt").build(), HttpInterceptorContext.create()));

        assertThat(response.bodyAs(UTF_8), is("foo\n"));
    }

    @Test
    public void canCombineVersionTextFiles() {
        VersionTextHandler handler = new VersionTextHandler(resources("classpath:/versions/version1.txt", "classpath:/versions/version2.txt"));

        FullHttpResponse response = waitForResponse(handler.handle( get("/version.txt").build(), HttpInterceptorContext.create()));

        assertThat(response.bodyAs(UTF_8), is("foo\nbar\n"));
    }

    @Test
    public void nonExistentFilesAreIgnored() {
        VersionTextHandler handler = new VersionTextHandler(resources("classpath:/versions/version1.txt", "version-nonexistent.txt"));

        FullHttpResponse response = waitForResponse(handler.handle( get("/version.txt").build(), HttpInterceptorContext.create()));

        assertThat(response.bodyAs(UTF_8), is("foo\n"));
    }

    @Test
    public void returnsUnknownVersionIfNoFilesAreFound() {
        VersionTextHandler handler = new VersionTextHandler(resources("version-nonexistent1.txt", "version-nonexistent2.txt"));

        FullHttpResponse response = waitForResponse(handler.handle(get("/version.txt").build(), HttpInterceptorContext.create()));

        assertThat(response.bodyAs(UTF_8), is("Unknown version\n"));
    }

    private static Iterable<Resource> resources(String... filenames) {
        return stream(filenames)
                .map(ResourceFactory::newResource)
                .collect(toList());
    }

}