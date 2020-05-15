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
package com.hotels.styx;

import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.client.StyxHeaderConfig;
import com.hotels.styx.common.Strings;
import com.hotels.styx.common.io.ResourceFactory;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;
import com.hotels.styx.proxy.ProxyServerConfig;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

import static com.hotels.styx.ServerConfigSchema.validateServerConfiguration;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static java.lang.System.getProperty;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Styx configuration.
 */
public final class StyxConfig implements Configuration {
    private static final Logger LOG = getLogger(StyxConfig.class);
    private static final String VALIDATE_SERVER_CONFIG_PROPERTY = "validateServerConfig";

    public static final String NO_JVM_ROUTE_SET = "noJvmRouteSet";

    private final Configuration configuration;
    private final ProxyServerConfig proxyServerConfig;
    private final AdminServerConfig adminServerConfig;
    private final StyxHeaderConfig styxHeaderConfig;

    public StyxConfig() {
        this(EMPTY_CONFIGURATION);
    }

    public StyxConfig(Configuration configuration) {
        this.configuration = requireNonNull(configuration);
        this.adminServerConfig = get("admin", AdminServerConfig.class).orElseGet(AdminServerConfig::new);
        this.proxyServerConfig = get("proxy", ProxyServerConfig.class).orElseGet(ProxyServerConfig::new);
        this.styxHeaderConfig = get("styxHeaders", StyxHeaderConfig.class).orElseGet(StyxHeaderConfig::new);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> tClass) {
        return configuration.get(key, tClass);
    }

    @Override
    public <X> X as(Class<X> type) {
        return configuration.as(type);
    }

    @Override
    public Optional<String> get(String key) {
        return get(key, String.class);
    }

    public StyxHeaderConfig styxHeaderConfig() {
        return styxHeaderConfig;
    }

    public ProxyServerConfig proxyServerConfig() {
        return proxyServerConfig;
    }

    public AdminServerConfig adminServerConfig() {
        return adminServerConfig;
    }

    public int port() {
        return proxyServerConfig().httpConnectorConfig().get().port();
    }

    public Optional<String> applicationsConfigurationPath() {
        return get("originsFile", String.class);
    }

    public Iterable<Resource> versionFiles(StartupConfig startupConfig) {
        Path rootPath = startupConfig.styxHome();

        String versionFilesAsString = get("userDefined.versionFiles", String.class)
                .orElseGet(() -> rootPath.resolve("styx-version.txt").toString());

        Iterable<String> versionFiles = versionFiles(versionFilesAsString);

        return stream(versionFiles.spliterator(), false)
                .map(ResourceFactory::newResource)
                .collect(toList());
    }

    /**
     * Parses StyxConfig from YAML. The system property "validateServerConfig" will be checked
     * to determine if validation should be performed. A setting of "no", or "n" will disable it.
     * If the property has any other value, or is absent, validation will be enabled.
     *
     * @param yamlText YAML
     * @return parsed StyxConfig
     */
    public static StyxConfig fromYaml(String yamlText) {
        return fromYaml(yamlText, !skipServerConfigValidation());
    }

    /**
     * Parses StyxConfig from YAML. Validation can be enabled or disabled.
     *
     * @param yamlText YAML
     * @param validateConfiguration true to validate, false to ignore invalid config
     * @return parsed StyxConfig
     */
    public static StyxConfig fromYaml(String yamlText, boolean validateConfiguration) {
        YamlConfiguration configuration = loadYamlConfiguration(yamlText);

        if (validateConfiguration) {
            validateServerConfiguration(configuration).ifPresent(it -> {
                throw new SchemaValidationException(it);
            });
        } else {
            LOG.warn("Styx config validation disabled");
        }

        return new StyxConfig(configuration);
    }

    public static StyxConfig defaultConfig() {
        return new StyxConfig();
    }

    @Override
    public String toString() {
        return configuration.toString();
    }

    private static boolean skipServerConfigValidation() {
        String validate = getProperty(VALIDATE_SERVER_CONFIG_PROPERTY, "yes");
        return "n".equals(validate) || "no".equals(validate);
    }

    private static Iterable<String> versionFiles(String versionFilesAsString) {
        return stream(versionFilesAsString.split(","))
                .filter(Strings::isNotEmpty)
                .map(String::trim)
                .collect(toList());
    }

    private static YamlConfiguration loadYamlConfiguration(String yaml) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .overrides(System.getProperties())
                .build()
                .parse(configSource(yaml));
    }
}
