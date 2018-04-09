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
package com.hotels.styx.infrastructure.logging;

import org.testng.annotations.Test;

import static com.hotels.styx.infrastructure.logging.LOGBackConfigurer.initLogging;


public class LOGBackConfigurerTest {

    static final String NON_EXISTENT_LOGBACK = "classpath:non/existen/path/logback.xml";

    @Test
    public void canConfigureLoggingFromClasspath() {
        initLogging("classpath:conf/logback.xml", false);
    }

    @Test
    public void canConfigureLoggingFromURL() {
        initLogging(LOGBackConfigurerTest.class.getResource("/conf/logback.xml"), false);
    }

    @Test
    public void resolvesPlaceholdersBeforeInitializing() {
        System.setProperty("CONFIG_LOCATION", "classpath:");
        initLogging("${CONFIG_LOCATION}conf/logback.xml", false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void failsForInvalidLogbackPath() {
        initLogging(NON_EXISTENT_LOGBACK, false);
    }
}
