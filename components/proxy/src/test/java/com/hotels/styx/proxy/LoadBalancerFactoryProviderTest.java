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
package com.hotels.styx.proxy;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancerFactory;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.configuration.MissingConfigurationException;
import com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import org.testng.annotations.Test;

import java.util.Optional;

import static com.hotels.styx.api.configuration.Configuration.EMPTY_CONFIGURATION;
import static com.hotels.styx.proxy.LoadBalancingStrategyFactoryProvider.newProvider;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class LoadBalancerFactoryProviderTest {

    @Test
    public void loadsTheConfiguredStrategy() {
        String yaml = "" +
                "loadBalancing:\n" +
                "  strategy: awesome\n" +
                "  strategies:\n" +
                "    awesome:\n" +
                "      factory: {class: \"com.hotels.styx.proxy.LoadBalancerFactoryProviderTest$NewAwesomeStrategy\"}\n";

        Configuration configuration = new YamlConfig(yaml);

        LoadBalancingStrategyFactoryProvider factoryProvider = newProvider(configuration);
        assertThat(factoryProvider.get(), is(instanceOf(NewAwesomeStrategy.class)));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void errorsIfCannotFindTheFactoryClass() {
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
    public void errorsIfTheSpecifiedStrategyFactoryKeyClassIsMissing() {
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
        assertThat(factoryProvider.get(), is(instanceOf(BusyConnectionsStrategy.Factory.class)));
    }

    public static class NewAwesomeStrategy implements LoadBalancerFactory, LoadBalancer {

        @Override
        public LoadBalancer create(Environment environment, Configuration strategyConfiguration, ActiveOrigins activeOrigins) {
            return null;
        }

        @Override
        public Optional<RemoteHost> choose(LoadBalancer.Preferences preferences) {
            return null;
        }
    }
}
