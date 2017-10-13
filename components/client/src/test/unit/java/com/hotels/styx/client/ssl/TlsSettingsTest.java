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
package com.hotels.styx.client.ssl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

public class TlsSettingsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeMethod
    public void setUp() throws Exception {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
    }

    @Test
    public void serialisesAllAttributes() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .trustAllCerts(true)
                .sslProvider("JDK")
                .trustStorePassword("bar")
                .build();

        String result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tlsSettings);

        assertThat(result, containsString("\"trustAllCerts\" : true"));
        assertThat(result, containsString("\"sslProvider\" : \"JDK\""));
        assertThat(result, containsString("\"addlCerts\" : [ ]"));

        // trustStorePath is platform dependent - thus match only until the root path:
        assertThat(result, containsString("\"trustStorePath\" : \"/"));
        assertThat(result, containsString("\"trustStorePassword\" : \"bar"));
    }

    @Test
    public void appliesDefaultTruststoreSettings() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings.trustAllCerts(), is(true));
        assertThat(tlsSettings.sslProvider(), is("JDK"));
        assertThat(tlsSettings.additionalCerts().isEmpty(), is(true));
        assertThat(tlsSettings.trustStorePath(), endsWith("security/cacerts"));
        assertThat(tlsSettings.trustStorePassword(), is("".toCharArray()));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void trustStorePasswordMustBeSuppliedWhenRemoteAuthenticationIsEnabled() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .trustAllCerts(false)
                .build();
    }

    @Test
    public void obtainsTrustStorePathFromSystemProperty() throws Exception {
        System.setProperty("javax.net.ssl.trustStore", "/path/to/myTruststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "myPassword");

        TlsSettings tlsSettings = new TlsSettings.Builder().build();

        assertThat(tlsSettings.trustStorePath(), is("/path/to/myTruststore"));
        assertThat(tlsSettings.trustStorePassword(), is("myPassword".toCharArray()));
    }

    @Test
    public void serialisesTrustStorePathAndPassword() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .trustStorePath("/path/to/truststore")
                .trustStorePassword("foobar")
                .build();

        String result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tlsSettings);

        assertThat(result, containsString("\"trustStorePath\" : \"/path/to/truststore\""));
        assertThat(result, containsString("\"trustStorePassword\" : \"foobar\""));
    }

}
