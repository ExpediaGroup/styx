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

import com.hotels.styx.AggregatedConfiguration;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.Resource;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.common.StyxFutures;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static com.hotels.styx.api.io.ResourceFactory.newResource;
import static com.hotels.styx.serviceproviders.ServiceProvision.loadService;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class FileBackedBackendServicesRegistryFactoryTest {
    @Test
    public void instantiatesFromYaml() throws IOException {
        Environment environment = environment("classpath:conf/environment/backend-factory-config.yml");

        FileBackedBackendServicesRegistry registry = loadService(environment.configuration(), environment, "services.factories.backendServiceRegistry", FileBackedBackendServicesRegistry.class).get();

        StyxFutures.await(registry.start());

        Iterable<BackendService> backendServices = registry.get();

        List<String> backendIds = ids(backendServices);

        assertThat(backendIds, containsInAnyOrder("backend-factory-test-origin1", "backend-factory-test-origin2"));
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
}
