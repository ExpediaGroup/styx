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
package com.hotels.styx.proxy;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategyFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.MissingConfigurationException;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.testng.annotations.Test;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.proxy.LoadBalancingStrategyFactoryProvider.newProvider;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class LoadBalancingStrategyFactoryProviderTest {

    @Test
    public void loadsTheConfiguredStrategy() throws Exception {
        String yaml = "" +
                "loadBalancing:\n" +
                "  strategy: awesome\n" +
                "  strategies:\n" +
                "    awesome:\n" +
                "      factory: {class: \"com.hotels.styx.proxy.LoadBalancingStrategyFactoryProviderTest$NewAwesomeStrategy\"}\n";

        Configuration configuration = new YamlConfig(yaml);

        LoadBalancingStrategyFactoryProvider factoryProvider = newProvider(configuration);
        assertThat(factoryProvider.get(), is(instanceOf(NewAwesomeStrategy.class)));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void errorsIfCannotFindTheFactoryClass() throws Exception {
        String yaml = "" +
                "loadBalancing:\n" +
                "  strategy: awesome\n" +
                "  strategies:\n" +
                "    awesome:\n" +
                "      factory: {class: \"doesnot.really.exist\"}\n";

        Configuration configurations = new YamlConfig(yaml);

        LoadBalancingStrategyFactoryProvider factoryProvider = newProvider(configurations);
        assertThat(factoryProvider.get(), is(instanceOf(NewAwesomeStrategy.class)));
    }

    @Test(expectedExceptions = MissingConfigurationException.class)
    public void errorsIfTheSpecifiedStrategyFactoryKeyClassIsMissing() throws Exception {
        String yaml = "" +
                "loadBalancing:\n" +
                "  strategy: noentry\n";

        Configuration configurations = new YamlConfig(yaml);

        LoadBalancingStrategyFactoryProvider factoryProvider = newProvider(configurations);
        assertThat(factoryProvider.get(), is(instanceOf(NewAwesomeStrategy.class)));
    }

    @Test
    public void loadsRoundRobinAsDefaultStrategy() throws Exception {
        LoadBalancingStrategyFactoryProvider factoryProvider = newProvider(EMPTY_CONFIGURATION);
        assertThat(factoryProvider.get(), is(instanceOf(RoundRobinStrategy.Factory.class)));
    }

    public static class NewAwesomeStrategy implements LoadBalancingStrategyFactory, LoadBalancingStrategy {

        @Override
        public LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration) {
            return null;
        }

        @Override
        public LoadBalancingStrategy create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return null;
        }

        @Override
        public Iterable<ConnectionPool> vote(Context context) {
            return null;
        }

        @Override
        public Iterable<ConnectionPool> snapshot() {
            return null;
        }

        @Override
        public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        }
    }
}
