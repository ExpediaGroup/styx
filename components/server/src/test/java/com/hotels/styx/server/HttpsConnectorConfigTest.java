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
package com.hotels.styx.server;

import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpsConnectorConfigTest {

    @Test
    public void shouldBeConfiguredOnlyWhenCertificateFileAndCertificateKeyFileAreSet() {
        HttpsConnectorConfig connectorConfig = new HttpsConnectorConfig.Builder()
                .port(2000)
                .certificateFile("server.pem")
                .certificateKeyFile("key.pem")
                .build();

        assertThat(connectorConfig.isConfigured(), is(true));
    }


    @Test
    public void connectorIsNotConfiguredIfMissingCertificateFile() {
        HttpsConnectorConfig connectorConfig = new HttpsConnectorConfig.Builder()
                .port(2000)
                .certificateKeyFile("key.pem")
                .build();

        assertThat(connectorConfig.isConfigured(), is(false));
    }

    @Test
    public void connectorIsNotConfiguredIfMissingCertificateKeyFile() {
        HttpsConnectorConfig connectorConfig = new HttpsConnectorConfig.Builder()
                .port(2000)
                .certificateFile("server.pem")
                .build();

        assertThat(connectorConfig.isConfigured(), is(false));
    }
}