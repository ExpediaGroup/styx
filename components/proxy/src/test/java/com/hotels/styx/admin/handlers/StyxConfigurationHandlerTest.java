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

import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.File;

import static com.hotels.styx.support.Support.requestContext;
import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class StyxConfigurationHandlerTest {
    private static final String ORIGINS_FILE = fixturesHome() + "conf/origins/origins-development.yml";

    @Test
    public void outputsConfiguration() {
        String yaml = "" +
                "proxy:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 8080\n" +
                "loadBalancing:\n" +
                "  strategy: ROUND_ROBIN\n" +
                "originsFile: " + ORIGINS_FILE + "\n";

        HttpResponse adminPageResponse = Mono.from(browseForCurrentConfiguration(yaml, false)).block();

        assertThat(adminPageResponse.bodyAs(UTF_8), is("{\"proxy\":{\"connectors\":{\"http\":{\"port\":8080}}}," +
                "\"loadBalancing\":{\"strategy\":\"ROUND_ROBIN\"},\"originsFile\":\"" +
                formatPathLikeYamlConfig(ORIGINS_FILE) + "\"}\n"));
    }

    @Test
    public void outputsPrettifiedConfiguration() throws Exception {
        String yaml = "" +
                "proxy:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 8080\n" +
                "loadBalancing:\n" +
                "  strategy: ROUND_ROBIN\n" +
                "originsFile: " + ORIGINS_FILE + "\n";

        HttpResponse adminPageResponse = Mono.from(browseForCurrentConfiguration(yaml, true)).block();

        assertThat(adminPageResponse.bodyAs(UTF_8), is("{\n" +
                "  \"proxy\" : {\n" +
                "    \"connectors\" : {\n" +
                "      \"http\" : {\n" +
                "        \"port\" : 8080\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"loadBalancing\" : {\n" +
                "    \"strategy\" : \"ROUND_ROBIN\"\n" +
                "  },\n" +
                "  \"originsFile\" : \"" + formatPathLikeYamlConfig(ORIGINS_FILE) + "\"\n" +
                "}"));
    }

    private static String formatPathLikeYamlConfig(String path) {
        if (File.separator.equals("\\")) {
            return path.replace("\\", "\\\\");
        }
        return path;
    }

    private static Eventual<HttpResponse> browseForCurrentConfiguration(String yaml, boolean pretty) {
        return configurationBrowserHandler(yaml).handle(adminRequest(pretty), requestContext());
    }

    private static HttpRequest adminRequest(boolean pretty) {
        if (pretty) {
            return get("/?pretty=").build();
        } else {
            return get("/").build();
        }
    }

    private static StyxConfigurationHandler configurationBrowserHandler(String yaml) {
        return new StyxConfigurationHandler(StyxConfig.fromYaml(yaml, false));
    }
}
