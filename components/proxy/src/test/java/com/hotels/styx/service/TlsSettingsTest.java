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
package com.hotels.styx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.extension.service.Certificate;
import com.hotels.styx.api.extension.service.TlsSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hotels.styx.api.extension.service.Certificate.certificate;
import static com.hotels.styx.common.Collections.listOf;
import static com.hotels.styx.common.Collections.setOf;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TlsSettingsTest {

    private final ObjectMapper mapper = addStyxMixins(new ObjectMapper());

    @BeforeEach
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
                .protocols(listOf("TLSv1.2"))
                .cipherSuites(listOf("TLS_RSA_WITH_AES_128_CBC_SHA"))
                .sendSni(false)
                .sniHost("some.sni.host")
                .build();

        String result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tlsSettings);

        assertThat(result, containsString("\"trustAllCerts\" : true"));
        assertThat(result, containsString("\"sslProvider\" : \"JDK\""));
        assertThat(result, containsString("\"addlCerts\" : [ ]"));

        // trustStorePath is platform dependent - thus match only until just before the root path:
        assertThat(result, containsString("\"trustStorePath\" : \""));
        assertThat(result, containsString("\"trustStorePassword\" : \"bar"));

        assertThat(result, containsString("TLS_RSA_WITH_AES_128_CBC_SHA"));

        assertThat(result, containsString("\"sendSni\" : " + tlsSettings.sendSni()));
        assertThat(result, containsString("\"sniHost\" : \"" + tlsSettings.sniHost().orElse("") + "\""));

    }

    @Test
    public void appliesDefaultTruststoreSettings() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings.trustAllCerts(), is(true));
        assertThat(tlsSettings.sslProvider(), is("JDK"));
        assertThat(tlsSettings.additionalCerts().isEmpty(), is(true));
        assertThat(tlsSettings.trustStorePath(), endsWith(new File("security/cacerts").getPath()));
        assertThat(tlsSettings.trustStorePassword(), is("".toCharArray()));
        assertThat(tlsSettings.protocols(), is(Collections.emptyList()));
        assertThat(tlsSettings.cipherSuites(), is(Collections.emptyList()));
        assertThat(tlsSettings.sendSni(), is(true));
        assertThat(tlsSettings.sniHost(), is(Optional.empty()));
    }

    @Test
    public void trustStorePasswordMustBeSuppliedWhenRemoteAuthenticationIsEnabled() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new TlsSettings.Builder()
                .trustAllCerts(false)
                .build());
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

    @Test
    public void equalsToConsidersProtocols() throws Exception {
        TlsSettings tlsSettings1 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        TlsSettings tlsSettings2 = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings1.equals(tlsSettings2), is(false));

        tlsSettings1 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        tlsSettings2 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        assertThat(tlsSettings1.equals(tlsSettings2), is(true));
    }

    @Test
    public void equalsToConsidersCipherSuites() throws Exception {
        TlsSettings tlsSettings1 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        TlsSettings tlsSettings2 = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings1.equals(tlsSettings2), is(false));

        tlsSettings1 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        tlsSettings2 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        assertThat(tlsSettings1.equals(tlsSettings2), is(true));
    }

    @Test
    public void hashCodeConsidersProtocols() throws Exception {
        TlsSettings tlsSettings1 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        TlsSettings tlsSettings2 = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings1.hashCode() == tlsSettings2.hashCode(), is(false));

        tlsSettings1 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        tlsSettings2 = new TlsSettings.Builder()
                .protocols(listOf("TLSv1", "TLSv1.1"))
                .build();

        assertThat(tlsSettings1.hashCode() == tlsSettings2.hashCode(), is(true));
    }

    @Test
    public void hashCodeConsidersCipherSuites() throws Exception {
        TlsSettings tlsSettings1 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        TlsSettings tlsSettings2 = new TlsSettings.Builder()
                .build();

        assertThat(tlsSettings1.hashCode() == tlsSettings2.hashCode(), is(false));

        tlsSettings1 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        tlsSettings2 = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .build();

        assertThat(tlsSettings1.hashCode() == tlsSettings2.hashCode(), is(true));
    }

    @Test
    public void toStringPrintsAttributeNames() throws Exception {
        TlsSettings tlsSettings = new TlsSettings.Builder()
                .cipherSuites(listOf("x", "y"))
                .protocols(listOf("TLSv1", "TLSv1.2"))
                .build();

        assertThat(tlsSettings.toString(), containsString("protocols=[TLSv1, TLSv1.2]"));
        assertThat(tlsSettings.toString(), containsString("cipherSuites=[x, y]"));
    }

    @Test
    public void isImmutable() throws Exception {
        List<String> protocols = new ArrayList<String>() {{
            add("TLSv1");
        }};
        List<String> cipherSuites = new ArrayList<String>() {{
            add("x");
        }};
        Certificate[] certificates = new Certificate[]{certificate("x", "x")};

        TlsSettings tlsSettings = new TlsSettings.Builder()
                .additionalCerts(certificates)
                .protocols(protocols)
                .cipherSuites(cipherSuites)
                .build();

        protocols.add("TLSv1.2");
        cipherSuites.add("y");
        certificates[0] = certificate("y", "y");

        assertThat(tlsSettings.protocols(), equalTo(listOf("TLSv1")));
        assertThat(tlsSettings.cipherSuites(), equalTo(listOf("x")));
        assertThat(tlsSettings.additionalCerts(), equalTo(setOf(certificate("x", "x"))));
    }
}
