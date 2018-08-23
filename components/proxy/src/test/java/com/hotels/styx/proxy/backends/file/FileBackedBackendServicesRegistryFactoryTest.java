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
package com.hotels.styx.proxy.backends.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotels.styx.AggregatedConfiguration;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.proxy.backends.file.FileChangeMonitor.FileMonitorSettings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.google.common.io.Files.createTempDir;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.delete;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileBackedBackendServicesRegistryFactoryTest {

    private File tempDir;
    private Path monitoredFile;
    private Environment environment;

    @BeforeMethod
    public void setUp() throws Exception {
        tempDir = createTempDir();
        monitoredFile = Paths.get(tempDir.toString(), "origins.yml");
        write(monitoredFile, "content-v1");
        environment = new com.hotels.styx.Environment.Builder().build();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        delete(monitoredFile);
        delete(tempDir.toPath());
    }


    @Test
    public void instantiatesFromYaml() {

        environment = new com.hotels.styx.Environment.Builder()
                .configuration(StyxConfig.fromYaml("config: {originsFile: '${CONFIG_LOCATION:classpath:}/conf/origins/backend-factory-origins.yml'}"))
                .build();

        JsonNodeConfig factoryConfig = new JsonNodeConfig(environment.configuration().get("config", JsonNode.class).get());

        Registry registry = new FileBackedBackendServicesRegistry.Factory().create(environment, factoryConfig);

        assertThat(registry != null, is(true));
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp = "empty .services.registry.factory.config.originsFile. config value for factory class FileBackedBackendServicesRegistry.Factory")
    public void requiresOriginsFileToBeSet() {
        Configuration configuration = mockConfiguration(Optional.of(""));

        new FileBackedBackendServicesRegistry.Factory().create(environment, configuration);
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp = "missing .services.registry.factory.config.originsFile. config value for factory class FileBackedBackendServicesRegistry.Factory")
    public void requiresOriginsFileToBeNonEmpty() {
        Configuration configuration = mockConfiguration(Optional.empty());

        new FileBackedBackendServicesRegistry.Factory().create(environment, configuration);
    }

    @Test
    public void fileMonitorIsTurnedOffByDefault() {
        Configuration configuration = mockConfiguration(Optional.of("/styx/config/path/origins.yml"), Optional.empty());

        FileBackedBackendServicesRegistry registry = (FileBackedBackendServicesRegistry)new FileBackedBackendServicesRegistry.Factory().create(environment, configuration);
        assertThat(registry.monitor(), is(FileMonitor.DISABLED));
    }

    @Test
    public void createsWithFileChangeMonitor() {
        Configuration configuration = mockConfiguration(Optional.of(monitoredFile.toString()), Optional.of(new FileMonitorSettings(true)));

        FileBackedBackendServicesRegistry registry = (FileBackedBackendServicesRegistry)new FileBackedBackendServicesRegistry.Factory().create(environment, configuration);
        assertThat(registry.monitor(), instanceOf(FileChangeMonitor.class));
    }

    private Configuration mockConfiguration(Optional<String> path) {
        Configuration configuration = mock(Configuration.class);
        when(configuration.get(eq("originsFile"), eq(String.class))).thenReturn(path);
        return configuration;
    }

    private Configuration mockConfiguration(Optional<String> path, Optional<FileMonitorSettings> monitorSettings) {
        Configuration configuration = mock(Configuration.class);
        when(configuration.get(eq("originsFile"), eq(String.class))).thenReturn(path);
        when(configuration.get(eq("monitor"), eq(FileMonitorSettings.class))).thenReturn(monitorSettings);
        return configuration;
    }

    private List<String> ids(Iterable<BackendService> backendServices) {
        return stream(backendServices.spliterator(), false)
                .map(BackendService::id)
                .map(Id::toString)
                .collect(toList());
    }

    private Environment environment(String configPath) {
        Resource config = newResource(configPath);

        AggregatedConfiguration configuration = new AggregatedConfiguration(new StyxConfig(new YamlConfig(config)));

        return new com.hotels.styx.Environment.Builder()
                .aggregatedConfiguration(configuration)
                .build();
    }

    void write(Path path, String text) throws Exception {
        copy(new ByteArrayInputStream(text.getBytes(UTF_8)), path, REPLACE_EXISTING);
    }

}
