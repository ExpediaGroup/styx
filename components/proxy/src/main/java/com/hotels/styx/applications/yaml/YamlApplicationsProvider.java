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
package com.hotels.styx.applications.yaml;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.applications.ApplicationsProvider;
import com.hotels.styx.applications.BackendServices;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.common.io.ResourceFactory.newResource;
import static com.hotels.styx.applications.BackendServices.newBackendServices;
import static com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins;
import static java.lang.String.format;

/**
 * Provides applications by reading from a YAML file.
 *
 */
public class YamlApplicationsProvider implements ApplicationsProvider {
    private static final ObjectMapper MAPPER = addStyxMixins(new ObjectMapper(new YAMLFactory()));
    private static final CollectionType TYPE = MAPPER.getTypeFactory().constructCollectionType(List.class, BackendService.class);

    private final BackendServices backendServices;

    private YamlApplicationsProvider(String yamlText) {
        this.backendServices = newBackendServices(readApplicationsFromText(yamlText));
    }

    public YamlApplicationsProvider(Resource resource) {
        this.backendServices = newBackendServices(readApplicationsFromResource(resource));
    }

    private static Iterable<BackendService> readApplicationsFromText(String yamlText) {
        try {
            return MAPPER.readValue(yamlText, TYPE);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private static Iterable<BackendService> readApplicationsFromResource(Resource resource) {
        try (InputStream stream = resource.inputStream()) {
            return MAPPER.readValue(stream, TYPE);
        } catch (JsonMappingException e) {
            throw new RuntimeException(format("Invalid YAML from %s: %s", resource, e.getLocalizedMessage()), e);
        } catch (IOException e) {
            throw new RuntimeException(format("Unable to load YAML from %s: %s", resource, e.getLocalizedMessage()), e);
        }
    }

    public static YamlApplicationsProvider loadFromPath(String path) {
        return new YamlApplicationsProvider(newResource(path));
    }

    public static YamlApplicationsProvider loadFromText(String yamlText) {
        return new YamlApplicationsProvider(yamlText);
    }

    public static BackendServices loadApplicationsFrom(String path) {
        return loadFromPath(path).get();
    }

    @Override
    public BackendServices get() {
        return backendServices;
    }
}
