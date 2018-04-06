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
package com.hotels.styx;

import com.google.common.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.hotels.styx.StartupConfig.CONFIG_FILE_LOCATION_VAR_NAME;
import static com.hotels.styx.StartupConfig.LOGBACK_CONFIG_LOCATION_VAR_NAME;
import static com.hotels.styx.StartupConfig.STYX_HOME_VAR_NAME;
import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class StartupConfigTest {

    @BeforeMethod
    public void clearSystemProperties() {
        clearProperty(STYX_HOME_VAR_NAME);
        clearProperty(CONFIG_FILE_LOCATION_VAR_NAME);
        clearProperty(LOGBACK_CONFIG_LOCATION_VAR_NAME);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "No system property STYX_HOME has been defined.")
    public void configurationLoadingFailsIfStyxHomeIsNotSpecified() {
        StartupConfig.load();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "STYX_HOME=/undefined is not a readable configuration path.")
    public void configurationLoadingFailsIfStyxHomeDoesNotPointToReadableLocation() {
        setProperty(STYX_HOME_VAR_NAME, "/undefined");
        StartupConfig.load();
    }

    @Test
    public void readsConfigurationFromDefaultLocation() {
        setProperty(STYX_HOME_VAR_NAME, fixturesHome());
        StartupConfig config = StartupConfig.load();

        assertThat(config.styxHome(), is(Paths.get(fixturesHome())));
    }

    @Test
    public void willLoadLogbackFromDefaultLocationIfNotSpecified() throws URISyntaxException {
        String styxHome = fixturesHome();
        setProperty(STYX_HOME_VAR_NAME, styxHome);

        StartupConfig startupConfig = StartupConfig.load();

        assertThat(startupConfig.logConfigLocation().url().toURI(), is(realPathOf("conf/logback.xml").toUri()));
    }

    @Test
    public void shouldLoadLogbackFromClasspath() {
        String styxHome = fixturesHome();
        setProperty(STYX_HOME_VAR_NAME, styxHome);

        StartupConfig startupConfig = newStartupConfigBuilder()
                .styxHome(styxHome)
                .build();

        assertThat(startupConfig.logConfigLocation().url().getFile(), is(StartupConfigTest.class.getResource("/conf/logback.xml").getFile()));
    }

    @Test
    public void logbackLocationCanBeOverridden() {
        String styxHome = fixturesHome();
        setProperty(STYX_HOME_VAR_NAME, styxHome);
        setProperty(LOGBACK_CONFIG_LOCATION_VAR_NAME, "/conf/foobar.xml");

        StartupConfig startupConfig = StartupConfig.load();

        assertThat(startupConfig.logConfigLocation().path(), is(new File("/conf/foobar.xml").getPath()));
    }

    @Test
    public void yamlConfigFileUsesSuppliedValue() {
        setProperty(STYX_HOME_VAR_NAME, fixturesHome());
        setProperty(CONFIG_FILE_LOCATION_VAR_NAME, "conf/foobar.yml");

        StartupConfig config = StartupConfig.load();

        assertThat(config.configFileLocation().path(), is("conf/foobar.yml".replace("/", File.separator)));
    }

    @Test
    public void usesDefaultConfigurationFile() {
        setProperty(STYX_HOME_VAR_NAME, fixturesHome());

        StartupConfig config = StartupConfig.load();
        Path fullPath = new File(Resources.getResource(".").getFile(), "conf/default.yml").toPath();

        assertThat(config.configFileLocation().path(), is(fullPath.toString()));
    }

    private static Path realPathOf(String logback) {
        return Paths.get(fixturesHome()).resolve(logback);
    }
}
