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
package com.hotels.styx.proxy.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.api.Environment;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.support.api.SimpleEnvironment;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.collect.Iterables.getFirst;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import com.hotels.styx.api.HttpRequest;

public class PluginSuppliersTest {
    Path FIXTURES_CLASS_PATH = fixturesHome(PluginSuppliersTest.class, "/plugins");

    private MetricRegistry styxMetricsRegistry;

    @BeforeMethod
    public void setUp() throws Exception {
        styxMetricsRegistry = new CodaHaleMetricRegistry();
    }

    @Test
    public void suppliesConfiguredPlugin() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";


        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        NamedPlugin plugin = firstPlugin(pluginSuppliers);

        assertThat(plugin.originalPlugin(), is(instanceOf(MyPlugin.class)));
        assertThat(plugin.name(), is("myPlugin"));
        assertThat(((MyPlugin) plugin.originalPlugin()).myPluginConfig, is(new MyPluginConfig("test-foo-bar")));
    }

    @Test
    public void providesNamesForAllLoadedPlugins() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin0,myPlugin1,myPlugin2\n" +
                "  all:\n" +
                "    myPlugin0:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance1\n" +
                "    myPlugin1:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance2\n" +
                "    myPlugin2:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance3\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        Iterable<NamedPlugin> plugins = pluginSuppliers.fromConfigurations();

        List<String> pluginNames = stream(plugins.spliterator(), false)
                .map(NamedPlugin::name)
                .collect(toList());

        assertThat(pluginNames, contains("myPlugin0", "myPlugin1", "myPlugin2"));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void throwsExceptionIfThereIsNoPluginMatchingActiveName() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    otherPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        pluginSuppliers.fromConfigurations();
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void throwsExceptionIfFactoryClassDoesNotExist() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "  - name: myPlugin\n" +
                "    factory:\n" +
                "      class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactoryDoesNotExist\n" +
                "      classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    config:\n" +
                "      testConfiguration: test-foo-bar\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        pluginSuppliers.fromConfigurations();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "(?s).*No active plugin specified.*")
    public void throwsExceptionIfNoActivePluginSpecified() {
        String yaml = "" +
                "plugins:\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        pluginSuppliers.fromConfigurations();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "(?s).*No list of all plugins specified.*")
    public void throwsExceptionIfListOfPluginsNotSpecified() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        pluginSuppliers.fromConfigurations();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "1 plugins could not be loaded")
    public void throwsExceptionIfFactoryFailsToLoadPlugin() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$FailingPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n";

        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

        pluginSuppliers.fromConfigurations();
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "3 plugins could not be loaded")
    public void attemptsToLoadAllPluginsEvenIfSomeFail() {
        LoggingTestSupport log = new LoggingTestSupport(PluginSuppliers.class);

        try {
            String yaml = "" +
                    "plugins:\n" +
                    "  active: myPlugin1,myPlugin2,myPlugin3\n" +
                    "  all:\n" +
                    "    myPlugin1:\n" +
                    "      factory:\n" +
                    "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$FailingPluginFactory\n" +
                    "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                    "    myPlugin2:\n" +
                    "      factory:\n" +
                    "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$FailingPluginFactory\n" +
                    "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                    "    myPlugin3:\n" +
                    "      factory:\n" +
                    "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$FailingPluginFactory\n" +
                    "        classPath: " + FIXTURES_CLASS_PATH + "\n";

            PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());

            pluginSuppliers.fromConfigurations();
        } catch (RuntimeException e) {
            assertThat(log.log(), hasItem(loggingEvent(ERROR, "Could not load plugin myPlugin1.*", RuntimeException.class, "plugin factory error")));
            throw e;
        } finally {
            log.stop();
        }
    }

    @Test
    public void appliesDefaultMetricsScopeForPlugins() throws Exception {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin, myAnotherPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n" +
                "    myAnotherPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.proxy.plugin.PluginSuppliersTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";


        PluginSuppliers pluginSuppliers = new PluginSuppliers(environment(yaml), new FileSystemPluginFactoryLoader());
        firstPlugin(pluginSuppliers);

        assertThat(styxMetricsRegistry.counter("styx.plugins.myPlugin.initialised").getCount(), is(1L));
        assertThat(styxMetricsRegistry.counter("styx.plugins.myAnotherPlugin.initialised").getCount(), is(1L));
    }

    private Environment environment(String yaml) {
        return new SimpleEnvironment.Builder()
                .configuration(new MyConfiguration(yaml))
                .metricRegistry(styxMetricsRegistry)
                .build();
    }

    private static NamedPlugin firstPlugin(PluginSuppliers pluginSuppliers) {
        return getFirst(pluginSuppliers.fromConfigurations(), null);
    }

    public static class MyConfiguration extends YamlConfig {
        public MyConfiguration(String yaml) {
            super(yaml);
        }
    }

    // Instantiated reflexively.
    @SuppressWarnings("unused")
    public static class MyPluginFactory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            MyPluginConfig myPluginConfig = environment.pluginConfig(MyPluginConfig.class);
            MetricRegistry metrics = environment.metricRegistry();

            return new MyPlugin(myPluginConfig, metrics);
        }
    }

    static class MyPluginConfig {
        private final String testConfiguration;

        public MyPluginConfig(@JsonProperty("testConfiguration") String testConfiguration) {
            this.testConfiguration = testConfiguration;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyPluginConfig that = (MyPluginConfig) o;
            return Objects.equals(testConfiguration, that.testConfiguration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testConfiguration);
        }
    }

    static class MyPlugin implements Plugin {
        private final MyPluginConfig myPluginConfig;

        public MyPlugin(MyPluginConfig myPluginConfig, MetricRegistry metrics) {
            this.myPluginConfig = myPluginConfig;
            metrics.counter("initialised").inc();
        }

        @Override
        public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return null;
        }
    }

    // Instantiated reflexively.
    @SuppressWarnings("unused")
    public static class FailingPluginFactory implements PluginFactory {
        @Override
        public Plugin create(Environment environment) {
            throw new RuntimeException("plugin factory error");
        }
    }
}
