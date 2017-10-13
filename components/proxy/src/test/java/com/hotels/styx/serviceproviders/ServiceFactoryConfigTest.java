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
package com.hotels.styx.serviceproviders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotels.styx.api.Environment;
import com.hotels.styx.support.api.SimpleEnvironment;
import com.hotels.styx.api.configuration.ServiceFactory;
import com.hotels.styx.api.configuration.Configuration;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ServiceFactoryConfigTest {
    @Test
    public void createsServiceFromConfiguration() throws IOException {
        Environment environment = new SimpleEnvironment.Builder().build();

        JsonNode jsonNode = new ObjectMapper().readTree("{\"configValue\":\"expectedValue\"}");

        ServiceFactoryConfig factoryConfig = new ServiceFactoryConfig(true, MyFactory.class.getName(), jsonNode);

        assertThat(factoryConfig.loadService(environment, String.class), is("expectedValue"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void refusesToLoadDisabledService() throws IOException {
        Environment environment = new SimpleEnvironment.Builder().build();

        JsonNode jsonNode = new ObjectMapper().readTree("{\"configValue\":\"expectedValue\"}");

        ServiceFactoryConfig factoryConfig = new ServiceFactoryConfig(false, MyFactory.class.getName(), jsonNode);

        factoryConfig.loadService(environment, String.class);
    }

    public static class MyFactory implements ServiceFactory<String> {
        @Override
        public String create(Environment environment, Configuration serviceConfiguration) {
            return serviceConfiguration.get("configValue").orElseThrow(() -> new RuntimeException("Configuration Value Not Found"));
        }
    }
}