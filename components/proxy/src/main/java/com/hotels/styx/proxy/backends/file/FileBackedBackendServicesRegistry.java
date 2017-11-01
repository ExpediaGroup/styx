/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy.backends.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.applications.BackendServices;
import com.hotels.styx.infrastructure.FileBackedRegistry;
import com.hotels.styx.infrastructure.MemoryBackedRegistry;
import com.hotels.styx.infrastructure.Registry;
import com.hotels.styx.infrastructure.YamlReader;

import java.util.List;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.client.applications.BackendServices.newBackendServices;

/**
 * File backed {@link com.hotels.styx.client.applications.BackendService} registry.
 */
public class FileBackedBackendServicesRegistry extends FileBackedRegistry<BackendService> {
    private final String originsFileName;

    /**
     * Factory for creating a {@link FileBackedBackendServicesRegistry}.
     */
    public static class Factory implements Registry.Factory<BackendService> {
        @Override
        public Registry<BackendService> create(Environment environment, Configuration registryConfiguration) {
            return registryConfiguration.get("originsFile", String.class)
                    .map(Factory::registry)
                    .orElseThrow(() -> new ConfigurationException(
                            "missing [services.registry.factory.config.originsFile] config value for factory class FileBackedBackendServicesRegistry.Factory"));
        }

        private static Registry<BackendService> registry(String originsFile) {
            return originsFile.isEmpty()
                    ? emptyRegistry()
                    : new FileBackedBackendServicesRegistry(originsFile);
        }

        private static Registry<BackendService> emptyRegistry() {
            return new MemoryBackedRegistry<>();
        }
    }

    public FileBackedBackendServicesRegistry(String originsFile) {
        this(newResource(originsFile));
    }

    public FileBackedBackendServicesRegistry(Resource resource) {
        super(resource, new YAMLBackendServicesParser());

        this.originsFileName = resource.absolutePath();
    }

    private static class YAMLBackendServicesParser implements Parser<BackendService> {
        private final YamlReader<List<BackendService>> delegate = new YamlReader<>();

        @Override
        public Iterable<BackendService> read(byte[] content) {
            try {
                return readBackendServices(content);
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        private BackendServices readBackendServices(byte[] content) throws Exception {
            return newBackendServices(delegate.read(content, new TypeReference<List<BackendService>>() {
            }));
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("originsFileName", originsFileName)
                .toString();
    }
}
