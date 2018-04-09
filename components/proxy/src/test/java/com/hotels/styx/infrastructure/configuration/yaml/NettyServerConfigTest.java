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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.hotels.styx.server.HttpsConnectorConfig;
import com.hotels.styx.server.netty.NettyServerConfig;
import org.testng.annotations.Test;

import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NettyServerConfigTest {
    @Test
    public void shouldCreateServerConfiguration() {
        String yaml = "" +
                "proxy:\n" +
                "  connectors:\n" +
                "      http:\n" +
                "        port: 8080\n" +
                "      https:\n" +
                "        port: 8443\n" +
                "        certificateFile: example.keystore\n" +
                "        certificateKeyFile: example\n" +
                "        validateCerts: true";

        YamlConfig yamlConfig = new YamlConfig(yaml);

        NettyServerConfig serverConfig = yamlConfig.get("proxy", NettyServerConfig.class).get();

        assertThat(serverConfig.httpConnectorConfig().get().port(), is(8080));
        HttpsConnectorConfig httpsConfig = new HttpsConnectorConfig.Builder()
                .port(8443)
                .certificateFile("example.keystore")
                .certificateKeyFile("example")
                .build();
        assertThat(httpsConnectorConfig(serverConfig), is(httpsConfig));
    }

    private HttpsConnectorConfig httpsConnectorConfig(NettyServerConfig serverConfig) {
        return stream(serverConfig.connectors().spliterator(), false)
                .filter(object -> object instanceof HttpsConnectorConfig)
                .findFirst()
                .map(HttpsConnectorConfig.class::cast)
                .get();
    }
}