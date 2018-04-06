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
package com.hotels.styx.admin;

import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.admin.AdminServerConfig.DEFAULT_ADMIN_PORT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AdminServerConfigTest {
    @Test
    public void usesDefaultPortIfNoConnectors() {
        String yaml = "" +
                "admin:\n" +
                "  maxConnectionsCount: 123\n";

        YamlConfig yamlConfig = new YamlConfig(yaml);

        Optional<AdminServerConfig> admin = yamlConfig.get("admin", AdminServerConfig.class);

        assertThat(admin.get().httpConnectorConfig().get().port(), is(DEFAULT_ADMIN_PORT));
    }

    @Test
    public void usesConfiguredPortIfPresent() {
        String yaml = "" +
                "admin:\n" +
                "  connectors:\n" +
                "    http:\n" +
                "      port: 1234\n";

        YamlConfig yamlConfig = new YamlConfig(yaml);

        Optional<AdminServerConfig> admin = yamlConfig.get("admin", AdminServerConfig.class);

        assertThat(admin.get().httpConnectorConfig().get().port(), is(1234));
    }
}