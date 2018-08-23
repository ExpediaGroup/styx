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
package com.hotels.styx.client.applications;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import org.testng.annotations.Test;

import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.service.BackendService.DEFAULT_RESPONSE_TIMEOUT_MILLIS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BackendServiceTest {
    Set<Origin> originSet = newHashSet(newOriginBuilder("localhost", 123).build());

    @Test
    public void usesResponseTimeoutOfZeroToIndicateDefaultValue() {
        BackendService backendService = BackendService.newBackendServiceBuilder()
                .origins(originSet)
                .responseTimeoutMillis(0)
                .build();

        assertThat(backendService.responseTimeoutMillis(), is(DEFAULT_RESPONSE_TIMEOUT_MILLIS));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void rejectsNullPaths() throws Exception {
        BackendService.newBackendServiceBuilder()
                .path(null)
                .build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Invalid path. Path='/    # not set default used'")
    public void rejectsInvalidPaths() throws Exception {
        BackendService.newBackendServiceBuilder()
                .path("/    # not set default used")
                .build();
    }
}
