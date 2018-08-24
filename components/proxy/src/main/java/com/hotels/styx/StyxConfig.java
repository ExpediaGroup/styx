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

import com.google.common.base.Splitter;
import com.hotels.styx.admin.AdminServerConfig;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.common.io.ResourceFactory;
import com.hotels.styx.client.StyxHeaderConfig;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;
import com.hotels.styx.proxy.ProxyServerConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.hotels.styx.StartupConfig.defaultStartupConfig;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Styx configuration.
 */
public final class StyxConfig implements Configuration {
    public static final String NO_JVM_ROUTE_SET = "noJvmRouteSet";

    private final StartupConfig startupConfig;
    private final Configuration configuration;

    private final ProxyServerConfig proxyServerConfig;
    private final AdminServerConfig adminServerConfig;
    private final StyxHeaderConfig styxHeaderConfig;

    public StyxConfig() {
        this(defaultStartupConfig(), EMPTY_CONFIGURATION);
    }

    public StyxConfig(Configuration configuration) {
        this(defaultStartupConfig(), configuration);
    }

    public StyxConfig(String yaml) {
        this(loadYamlConfiguration(yaml));
    }

    public StyxConfig(StartupConfig startupConfig, Configuration configuration) {
        this.startupConfig = requireNonNull(startupConfig);
        this.configuration = requireNonNull(configuration);
        this.adminServerConfig = get("admin", AdminServerConfig.class).orElseGet(AdminServerConfig::new);
        this.proxyServerConfig = get("proxy", ProxyServerConfig.class).orElseGet(ProxyServerConfig::new);

        this.styxHeaderConfig = get("styxHeaders", StyxHeaderConfig.class).orElseGet(StyxHeaderConfig::new);
    }

    private static Configuration loadYamlConfiguration(String yaml) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .format(YAML)
                .build()
                .parse(configSource(yaml));
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
        return this.adminServerConfig;
    }

    public int port() {
        return proxyServerConfig.httpConnectorConfig().get().port();
    }

    public Optional<String> applicationsConfigurationPath() {
        return get("originsFile", String.class);
    }

    public Path rootPath() {
        return this.startupConfig.styxHome();
    }

    public String logConfigLocation() {
        return Paths.get(startupConfig.logConfigLocation().url().getFile()).toString();
    }

    public StartupConfig startupConfig() {
        return startupConfig;
    }

    public Iterable<Resource> versionFiles() {
        String versionFilesAsString = get("versionFiles", String.class)
                .orElseGet(() -> rootPath().resolve("styx-version.txt").toString());

        Iterable<String> versionFiles = versionFiles(versionFilesAsString);

        return stream(versionFiles.spliterator(), false)
                .map(ResourceFactory::newResource)
                .collect(toList());
    }

    private static Iterable<String> versionFiles(String versionFilesAsString) {
        return Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .split(versionFilesAsString);
    }

    public static StyxConfig fromYaml(String yamlText) {
        return new StyxConfig(yamlText);
    }

    public static StyxConfig defaultConfig() {
        return new StyxConfig();
    }

    @Override
    public String toString() {
        return configuration.toString();
    }
}
