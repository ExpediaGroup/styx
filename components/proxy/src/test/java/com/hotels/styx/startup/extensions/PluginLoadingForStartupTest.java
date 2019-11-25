/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup.extensions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.metrics.codahale.CodaHaleMetricRegistry;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static ch.qos.logback.classic.Level.ERROR;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PluginLoadingForStartupTest {
    private static final Path FIXTURES_CLASS_PATH = fixturesHome(PluginLoadingForStartupTest.class, "/");

    private MetricRegistry styxMetricsRegistry;

    @BeforeEach
    public void setUp() {
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
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";


        List<NamedPlugin> plugins = PluginLoadingForStartup.loadPlugins(environment(yaml));

        NamedPlugin plugin = plugins.get(0);

        assertThat(plugin.originalPlugin(), is(instanceOf(MyPlugin.class)));
        assertThat(plugin.name(), is("myPlugin"));
        assertThat(((MyPlugin) plugin.originalPlugin()).myPluginConfig, is(new MyPluginConfig("test-foo-bar")));
    }

    @Test
    public void providesNamesForAllActivatedPlugins() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin0,myPlugin2\n" +
                "  all:\n" +
                "    myPlugin0:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance1\n" +
                "    myPlugin1:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance2\n" +
                "    myPlugin2:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance3\n";

        List<NamedPlugin> plugins = PluginLoadingForStartup.loadPlugins(environment(yaml));

        List<String> pluginNames = plugins.stream()
                .map(NamedPlugin::name)
                .collect(toList());

        assertThat(pluginNames, contains("myPlugin0", "myPlugin2"));
    }

    @Test
    public void providesNamesForAllPluginsWhenHttpPipelineAttributeIsPresent() {
        String yaml = "" +
                "plugins:\n" +
                "  all:\n" +
                "    myPlugin0:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance1\n" +
                "    myPlugin1:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance2\n" +
                "    myPlugin2:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: instance3\n" +
                "" +
                "httpPipeline: root";

        List<NamedPlugin> plugins = PluginLoadingForStartup.loadPlugins(environment(yaml));

        List<String> pluginNames = plugins.stream()
                .map(NamedPlugin::name)
                .collect(toList());

        assertThat(pluginNames, contains("myPlugin0", "myPlugin1", "myPlugin2"));
    }

    @Test
    public void throwsExceptionIfThereIsNoPluginMatchingActiveName() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    otherPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";

        assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
    }

    @Test
    public void throwsExceptionIfFactoryClassDoesNotExist() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "  - name: myPlugin\n" +
                "    factory:\n" +
                "      class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactoryDoesNotExist\n" +
                "      classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    config:\n" +
                "      testConfiguration: test-foo-bar\n";

        assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
    }

    @Test
    public void throwsExceptionIfListOfPluginsNotSpecified() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n";

        Exception e = assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
        assertThat(e.getMessage(), matchesPattern("(?s).*No list of all plugins specified.*"));
    }

    @Test
    public void throwsExceptionIfFactoryFailsToLoadPlugin() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$FailingPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n";

        Exception e = assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
        assertThat(e.getMessage(), matchesPattern("1 plugin\\(s\\) could not be loaded: failedPlugins=\\[myPlugin\\]; failureCauses=\\[myPlugin: java.lang.RuntimeException: plugin factory error\\]"));
    }

    @Test
    public void attemptsToLoadAllPluginsEvenIfSomePluginFactoriesCannotBeLoaded() {
        LoggingTestSupport log = new LoggingTestSupport(FailureHandling.class);

        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin1,myPlugin2,myPlugin3\n" +
                "  all:\n" +
                "    myPlugin1:\n" +
                "      factory:\n" +
                "        class: BadClassName\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    myPlugin2:\n" +
                "      factory:\n" +
                "        class: BadClassName\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    myPlugin3:\n" +
                "      factory:\n" +
                "        class: BadClassName\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n";

        Exception e = assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
        assertThat(e.getMessage(), matchesPattern("3 plugin\\(s\\) could not be loaded: failedPlugins=\\[myPlugin1, myPlugin2, myPlugin3\\]; failureCauses=\\[" +
                "myPlugin1: com.hotels.styx.api.configuration.ConfigurationException: Could not load a plugin factory.*, " +
                "myPlugin2: com.hotels.styx.api.configuration.ConfigurationException: Could not load a plugin factory.*, " +
                "myPlugin3: com.hotels.styx.api.configuration.ConfigurationException: Could not load a plugin factory.*\\]"));
        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Could not load plugin: pluginName=myPlugin1; factoryClass=.*", ConfigurationException.class, "Could not load a plugin factory for.*")));
        log.stop();
    }

    @Test
    public void attemptsToLoadAllPluginsEvenIfSomePluginFactoriesFailDuringExecution() {
        LoggingTestSupport log = new LoggingTestSupport(FailureHandling.class);

        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin1,myPlugin2,myPlugin3\n" +
                "  all:\n" +
                "    myPlugin1:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$FailingPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    myPlugin2:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$FailingPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "    myPlugin3:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$FailingPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n";

        Exception e = assertThrows(RuntimeException.class, () -> PluginLoadingForStartup.loadPlugins(environment(yaml)));
        assertThat(e.getMessage(), matchesPattern( "3 plugin\\(s\\) could not be loaded: failedPlugins=\\[myPlugin1, myPlugin2, myPlugin3\\]; failureCauses=\\[" +
                "myPlugin1: java.lang.RuntimeException: plugin factory error, " +
                "myPlugin2: java.lang.RuntimeException: plugin factory error, " +
                "myPlugin3: java.lang.RuntimeException: plugin factory error\\]"));

        assertThat(log.log(), hasItem(loggingEvent(ERROR, "Could not load plugin: pluginName=myPlugin1; factoryClass=.*", RuntimeException.class, "plugin factory error")));
        log.stop();
    }

    @Test
    public void appliesDefaultMetricsScopeForPlugins() {
        String yaml = "" +
                "plugins:\n" +
                "  active: myPlugin, myAnotherPlugin\n" +
                "  all:\n" +
                "    myPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n" +
                "    myAnotherPlugin:\n" +
                "      factory:\n" +
                "        class: com.hotels.styx.startup.extensions.PluginLoadingForStartupTest$MyPluginFactory\n" +
                "        classPath: " + FIXTURES_CLASS_PATH + "\n" +
                "      config:\n" +
                "        testConfiguration: test-foo-bar\n";


        PluginLoadingForStartup.loadPlugins(environment(yaml));

        assertThat(styxMetricsRegistry.counter("styx.plugins.myPlugin.initialised").getCount(), is(1L));
        assertThat(styxMetricsRegistry.counter("styx.plugins.myAnotherPlugin.initialised").getCount(), is(1L));
    }

    private com.hotels.styx.Environment environment(String yaml) {
        return new com.hotels.styx.Environment.Builder()
                .configuration(new StyxConfig(new MyConfiguration(yaml)))
                .metricRegistry(styxMetricsRegistry)
                .build();
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
        public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
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