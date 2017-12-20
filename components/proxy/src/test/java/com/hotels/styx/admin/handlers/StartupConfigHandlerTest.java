/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.admin.handlers;

import com.hotels.styx.StartupConfig;
import com.hotels.styx.api.messages.FullHttpResponse;
import org.testng.annotations.Test;

import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.api.HttpRequest.Builder.get;
import static com.hotels.styx.support.api.BlockingObservables.waitForResponse;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static java.nio.charset.StandardCharsets.UTF_8;

public class StartupConfigHandlerTest {
    @Test
    public void outputsExpectedData(){
        StartupConfig startupConfig = newStartupConfigBuilder()
                .styxHome("/foo")
                .configFileLocation("/bar/configure-me.yml")
                .logbackConfigLocation("/baz/logback-conf.xml")
                .build();

        StartupConfigHandler handler = new StartupConfigHandler(startupConfig);

        FullHttpResponse response = waitForResponse(handler.handle(get("/").build()));

        assertThat(response.status(), is(OK));
        assertThat(response.bodyAs(UTF_8), is("<html><body>" +
                "Styx Home='/foo'" +
                "<br />Config File Location='/bar/configure-me.yml'" +
                "<br />Log Config Location='/baz/logback-conf.xml'" +
                "</body></html>"));
    }
}