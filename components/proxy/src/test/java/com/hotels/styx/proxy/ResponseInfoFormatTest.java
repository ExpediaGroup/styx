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
package com.hotels.styx.proxy;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.Configuration.MapBackedConfiguration;
import com.hotels.styx.client.StyxHeaderConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.hotels.styx.api.HttpRequest.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ResponseInfoFormatTest {
    private HttpRequest request;

    @BeforeMethod
    public void setUp() {
        request = get("/").build();
    }

    @Test
    public void defaultFormatDoesNotIncludeVersion() {
        String info = new ResponseInfoFormat(defaultEnvironment()).format(request);

        assertThat(info, is("noJvmRouteSet;" + request.id()));
    }

    @Test
    public void canConfigureCustomHeaderFormatWithVersion() {
        StyxHeaderConfig styxHeaderConfig = new StyxHeaderConfig(
                new StyxHeaderConfig.StyxHeader(
                        null,
                        "{VERSION};{REQUEST_ID};{INSTANCE}"),
                null,
                null);

        Environment environment = environment(new MapBackedConfiguration()
                .set("styxHeaders", styxHeaderConfig));

        String info = new ResponseInfoFormat(environment).format(request);

        assertThat(info, is("STYX-dev.0.0;" + request.id() + ";noJvmRouteSet"));
    }

    private static Environment defaultEnvironment() {
        return new Environment.Builder().build();
    }

    private static Environment environment(Configuration configuration) {
        return new Environment.Builder()
                .configuration(new StyxConfig(configuration))
                .build();
    }
}