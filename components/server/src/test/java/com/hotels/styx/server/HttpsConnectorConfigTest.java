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
package com.hotels.styx.server;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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

    @Test
    public void isImmutable() throws Exception {
        String[] protocols = new String[] { "TLSv1.2" };
        List<String> cipherSuites = new ArrayList<String>() {{ add("A1"); }};

        HttpsConnectorConfig connector1 = new HttpsConnectorConfig.Builder()
                .protocols(protocols)
                .cipherSuites(cipherSuites)
                .build();

        protocols[0] = "TLSv1.1";
        cipherSuites.add("A2");

        assertThat(connector1.protocols(), equalTo(ImmutableList.of("TLSv1.2")));
        assertThat(connector1.ciphers(), equalTo(ImmutableList.of("A1")));
    }


    @Test
    public void equalsToConsidersProtocols() throws Exception {
        HttpsConnectorConfig connector1 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.2")
                .build();

        HttpsConnectorConfig connector2 = new HttpsConnectorConfig.Builder()
                .build();

        assertThat(connector1.equals(connector2), is(false));


        connector1 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.1", "TLSv1.2")
                .build();

        connector2 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.1", "TLSv1.2")
                .build();

        assertThat(connector1.equals(connector2), is(true));
    }

    @Test
    public void hashCodeConsidersProtocols() throws Exception {
        HttpsConnectorConfig connector1 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.2")
                .build();

        HttpsConnectorConfig connector2 = new HttpsConnectorConfig.Builder()
                .build();

        assertThat(connector1.hashCode() == connector2.hashCode(), is(false));


        connector1 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.1", "TLSv1.2")
                .build();

        connector2 = new HttpsConnectorConfig.Builder()
                .protocols("TLSv1.1", "TLSv1.2")
                .build();

        assertThat(connector1.hashCode() == connector2.hashCode(), is(true));
    }

}