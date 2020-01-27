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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.StartupConfig;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.io.ClasspathResource;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.RegExMatcher.matchesRegex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LoggingConfigurationHandlerTest {
    @Test
    public void showsErrorMessageInContentIfLogConfigFileDoesNotExist() {
        StartupConfig startupConfig = newStartupConfigBuilder()
                .logbackConfigLocation("/foo/bar")
                .build();
        LoggingConfigurationHandler handler = new LoggingConfigurationHandler(startupConfig.logConfigLocation());

        HttpResponse response = Mono.from(handler.handle(get("/").build(), requestContext())).block();

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), matchesRegex("Could not load resource=.*foo[\\\\/]bar'"));
    }

    @Test
    public void showsLogConfigContent() throws IOException {
        StartupConfig startupConfig = newStartupConfigBuilder()
                .logbackConfigLocation(fixturesHome() + "/conf/environment/styx-config-test.yml")
                .build();
        LoggingConfigurationHandler handler = new LoggingConfigurationHandler(startupConfig.logConfigLocation());

        HttpResponse response = Mono.from(handler.handle(get("/").build(), requestContext())).block();

        String expected = Resources.load(new ClasspathResource("conf/environment/styx-config-test.yml", LoggingConfigurationHandlerTest.class));

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is(expected));
    }
}