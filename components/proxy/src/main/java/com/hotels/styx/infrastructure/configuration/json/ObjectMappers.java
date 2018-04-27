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
package com.hotels.styx.infrastructure.configuration.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.api.client.OriginsSnapshot;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.Certificate;
import com.hotels.styx.api.service.ConnectionPoolSettings;
import com.hotels.styx.api.service.HealthCheckConfig;
import com.hotels.styx.api.service.RewriteConfig;
import com.hotels.styx.api.service.StickySessionConfig;
import com.hotels.styx.api.service.TlsSettings;
import com.hotels.styx.infrastructure.configuration.json.mixins.*;


/**
 * Collection of methods that helps to work with {@link ObjectMapper} objects in styx libraries.
 */
public class ObjectMappers {
    /**
     * Adds annotation mixins required to serialize/deserialize with json styx domain objects from styx-api module.
     * @param objectMapper
     * @return objectMapper with additional annotation mixins
     */
    public static ObjectMapper addStyxMixins(ObjectMapper objectMapper) {
        objectMapper.addMixIn(BackendService.class, BackendServiceMixin.class)
                .addMixIn(BackendService.Builder.class, BackendServiceMixin.Builder.class)
                .addMixIn(HealthCheckConfig.class, HealthCheckConfigMixin.class)
                .addMixIn(HealthCheckConfig.Builder.class, HealthCheckConfigMixin.Builder.class)
                .addMixIn(Certificate.class, CertificateMixin.class)
                .addMixIn(ConnectionPoolSettings.class, ConnectionPoolSettingsMixin.class)
                .addMixIn(RewriteConfig.class, RewriteConfigMixin.class)
                .addMixIn(StickySessionConfig.class, StickySessionConfigMixin.class)
                .addMixIn(TlsSettings.class, TlsSettingsMixin.class)
                .addMixIn(Origin.class, OriginMixin.class)
                .addMixIn(OriginsSnapshot.class, OriginsSnapshotMixin.class)
                .addMixIn(Id.class, IdMixin.class);
        return objectMapper;
    }
}
