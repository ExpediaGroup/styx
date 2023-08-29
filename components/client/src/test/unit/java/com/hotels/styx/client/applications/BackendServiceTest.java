/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.client.applications;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.DEFAULT_RESPONSE_TIMEOUT_MILLIS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BackendServiceTest {
    Set<Origin> originSet = Set.of(newOriginBuilder("localhost", 123).build());

    @Test
    public void usesResponseTimeoutOfZeroToIndicateDefaultValue() {
        BackendService backendService = BackendService.newBackendServiceBuilder()
                .origins(originSet)
                .responseTimeoutMillis(0)
                .build();

        assertThat(backendService.responseTimeoutMillis(), is(DEFAULT_RESPONSE_TIMEOUT_MILLIS));
    }

    @Test
    public void rejectsNullPaths() throws Exception {
        assertThrows(NullPointerException.class,
                () -> BackendService.newBackendServiceBuilder()
                .path(null)
                .build());
    }

    @Test
    public void rejectsInvalidPaths() throws Exception {
        Exception e = assertThrows(IllegalArgumentException.class,
            () -> BackendService.newBackendServiceBuilder()
                .path("/    # not set default used")
                .build());
        assertEquals("Invalid path. Path='/    # not set default used'", e.getMessage());
    }

    @Test
    public void usesHttp11IfHttpVersionIsNotProvided() {
        BackendService backendService = BackendService.newBackendServiceBuilder()
                .build();

        assertThat(backendService.httpVersion(), is("HTTP/1.1"));
    }
}
