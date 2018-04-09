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
package com.hotels.styx.api.configuration;

import org.testng.annotations.Test;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class SystemSettingsTest {
    static final Setting<String> ENVIRONMENT = new SystemSettings.String("CONFIG_ENV");

    @Test(expectedExceptions = NoSystemPropertyDefined.class, expectedExceptionsMessageRegExp = "No system property CONFIG_ENV has been defined.")
    public void failsIfTheSystemSettingIsNotSet() {
        SystemSettings.valueOf(ENVIRONMENT);
    }

    @Test
    public void returnsTheDefaultValueIfTheSystemSettingIsNotSet() {
        assertThat(SystemSettings.valueOf(ENVIRONMENT, "test-lon"), is("test-lon"));
    }

    @Test
    public void returnsTheSystemSettingConfigured() {
        setProperty("CONFIG_ENV", "test-lon");

        try {
            assertThat(SystemSettings.valueOf(ENVIRONMENT), is("test-lon"));
        } finally {
            clearProperty("CONFIG_ENV");
        }
    }
}