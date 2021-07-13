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
package com.hotels.styx;

import com.hotels.styx.api.Resource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.hotels.styx.common.Preconditions.checkArgument;
import static com.hotels.styx.common.Preconditions.checkNotEmpty;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static java.nio.file.Files.isReadable;

/**
 * Startup configuration values for {@link StyxServer}.
 * <p/>
 * In order start properly {@link StyxServer} you need to set the following env properties.
 * <p/>
 * <ul>
 * <li>STYX_HOME - Location where styx is installed</li>
 * </ul>
 */
public final class StartupConfig {
    static final String STYX_HOME_VAR_NAME = "STYX_HOME";
    static final String CONFIG_FILE_LOCATION_VAR_NAME = "CONFIG_FILE_LOCATION";
    static final String LOGBACK_CONFIG_LOCATION_VAR_NAME = "LOG_CONFIG_LOCATION";

    private final Path styxHome;
    private final Resource logConfigLocation;
    private final Resource configFileLocation;

    private StartupConfig(Builder builder) {
        this.styxHome = builder.styxHome();
        this.configFileLocation = builder.configFileLocation();
        this.logConfigLocation = builder.logbackConfigLocation();
    }

    static StartupConfig defaultStartupConfig() {
        return new StartupConfig.Builder().build();
    }

    public static StartupConfig load() {
        String styxHome = System.getProperty(STYX_HOME_VAR_NAME);

        checkArgument(styxHome != null, "No system property %s has been defined.", STYX_HOME_VAR_NAME);
        checkArgument(isReadable(Paths.get(styxHome)), "%s=%s is not a readable configuration path.", STYX_HOME_VAR_NAME, styxHome);

        String configFileLocation = System.getProperty(CONFIG_FILE_LOCATION_VAR_NAME);

        if (configFileLocation == null) {
            configFileLocation = Paths.get(styxHome).resolve("conf/default.yml").toString();
        }

        String logbackConfigLocation = System.getProperty(LOGBACK_CONFIG_LOCATION_VAR_NAME);

        if (logbackConfigLocation == null) {
            logbackConfigLocation = Paths.get(styxHome).resolve("conf/logback.xml").toString();
        }

        return new Builder()
                .styxHome(styxHome)
                .configFileLocation(configFileLocation)
                .logbackConfigLocation(logbackConfigLocation)
                .build();
    }

    public static Builder newStartupConfigBuilder() {
        return new Builder();
    }

    public Path styxHome() {
        return styxHome;
    }

    public Resource logConfigLocation() {
        return logConfigLocation;
    }

    public Resource configFileLocation() {
        return configFileLocation;
    }

    @Override
    public String toString() {
        return new StringBuilder(96)
                .append(this.getClass().getSimpleName())
                .append("{" + STYX_HOME_VAR_NAME + "=")
                .append(styxHome)
                .append(", " + CONFIG_FILE_LOCATION_VAR_NAME + "=")
                .append(configFileLocation)
                .append(", " + LOGBACK_CONFIG_LOCATION_VAR_NAME + "=")
                .append(logConfigLocation)
                .append('}')
                .toString();
    }

    /**
     * Builds StartupConfig.
     */
    public static final class Builder {
        private String styxHome = ".";
        private String configFileLocation = "classpath:conf/default.yml";
        private String logbackConfigLocation = "classpath:conf/logback.xml";

        private Builder() {
        }

        Path styxHome() {
            return Paths.get(styxHome);
        }

        Resource configFileLocation() {
            return newResource(configFileLocation);
        }

        Resource logbackConfigLocation() {
            return newResource(logbackConfigLocation);
        }

        public Builder styxHome(String styxHome) {
            checkNotEmpty(styxHome);
            this.styxHome = styxHome;
            return this;
        }

        public Builder configFileLocation(String configFileLocation) {
            checkNotEmpty(configFileLocation);
            this.configFileLocation = configFileLocation;
            return this;
        }

        public Builder logbackConfigLocation(String logbackConfigLocation) {
            checkNotEmpty(logbackConfigLocation);
            this.logbackConfigLocation = logbackConfigLocation;
            return this;
        }

        public StartupConfig build() {
            return new StartupConfig(this);
        }
    }
}
