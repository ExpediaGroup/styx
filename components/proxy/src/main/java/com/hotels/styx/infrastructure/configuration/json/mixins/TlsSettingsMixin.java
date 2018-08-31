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
package com.hotels.styx.infrastructure.configuration.json.mixins;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.hotels.styx.api.extension.service.Certificate;
import com.hotels.styx.api.extension.service.TlsSettings;

import java.util.List;
import java.util.Set;


/**
 * Jackson annotations for {@link TlsSettings}.
 */
@JsonDeserialize(builder = TlsSettings.Builder.class)
public interface TlsSettingsMixin {
    @JsonProperty("trustAllCerts")
    boolean trustAllCerts();

    @JsonProperty("authenticate")
    boolean authenticate();

    @JsonProperty("sslProvider")
    String sslProvider();

    @JsonProperty("addlCerts")
    Set<Certificate> additionalCerts();

    @JsonProperty("trustStorePath")
    String trustStorePath();

    @JsonProperty("trustStorePassword")
    char[] trustStorePassword();

    @JsonProperty("protocols")
    List<String> protocols();

    @JsonProperty("cipherSuites")
    List<String> cipherSuites();

    /**
     * The builder for SSL settings.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    interface Builder {
        @JsonProperty("trustAllCerts")
        Builder trustAllCerts(boolean trustAllCerts);

        @JsonProperty("authenticate")
        Builder authenticate(boolean authenticate);

        @JsonProperty("sslProvider")
        Builder sslProvider(String sslProvider);

        @JsonProperty("addlCerts")
        Builder additionalCerts(Certificate... certificates);

        @JsonProperty("trustStorePath")
        Builder trustStorePath(String trustStorePath);

        @JsonProperty("trustStorePassword")
        Builder trustStorePassword(String trustStorePwd);

        @JsonProperty("protocols")
        Builder protocols(List<String> protocols);

        @JsonProperty("cipherSuites")
        Builder cipherSuites(List<String> cipherSuites);
    }
}

