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
package com.hotels.styx.infrastructure.configuration;

import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.ConfigurationFactory;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.testng.annotations.Test;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigurationSupplierTest {
    @Test
    public void loadTheDefaultConfigurationIfNoConfigurationFactoryIsSet() {
        ConfigurationSupplier supplier = new ConfigurationSupplier(StyxConfig.defaultConfig());

        assertThat(supplier.get(), is(EMPTY_CONFIGURATION));
    }

    @Test
    public void createsConfigurationUsingConfigurationFactory() {
        Configuration staticConfiguration = new YamlConfig("" +
                "configuration:\n" +
                "  factory:\n" +
                "    class: " + TestConfigurationFactory.class.getName() + "\n");

        ConfigurationSupplier supplier = new ConfigurationSupplier(staticConfiguration);

        Configuration configuration = supplier.get();
        assertThat(configuration.get("foo"), isValue("bar"));
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "(?s).*JsonMappingException.*")
    public void throwsExceptionIfFactoryClassIsAbsent() {
        Configuration configuration = new YamlConfig("" +
                "configuration:\n" +
                "  factory:\n");

        ConfigurationSupplier supplier = new ConfigurationSupplier(configuration);
        supplier.get();
    }

    public static class TestConfigurationFactory implements ConfigurationFactory {
        @Override
        public Configuration create(Configuration staticConfiguration) {
            return new Configuration.MapBackedConfiguration().set("foo", "bar");
        }
    }
}