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
package com.hotels.styx.api.extension.service;

import com.hotels.styx.api.extension.service.HealthCheckConfig;
import org.testng.annotations.Test;

import static com.hotels.styx.api.extension.service.HealthCheckConfig.newHealthCheckConfigBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HealthCheckConfigTest {

    @Test
    public void shouldBeEnabledIfHasUriSet() throws Exception {
        HealthCheckConfig healthCheckConfig = newHealthCheckConfigBuilder()
                .uri("/someuri")
                .build();
        assertThat(healthCheckConfig.isEnabled(), is(true));
        assertThat(healthCheckConfig.getUri(), is("/someuri"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Invalid health check URI. URI='/version.txt   # default used'")
    public void rejectsInvalidURIs() throws Exception {
        newHealthCheckConfigBuilder()
                .uri("/version.txt   # default used")
                .build();
    }

    @Test
    public void shouldBeDisabledIfHasNotUriSet() throws Exception {
        HealthCheckConfig healthCheckConfig = newHealthCheckConfigBuilder()
                .build();
        assertThat(healthCheckConfig.isEnabled(), is(false));
    }
}