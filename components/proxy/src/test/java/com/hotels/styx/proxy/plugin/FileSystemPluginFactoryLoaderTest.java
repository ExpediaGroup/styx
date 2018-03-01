/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.configuration.ConfigurationException;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.infrastructure.configuration.ObjectFactory;
import org.testng.annotations.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class FileSystemPluginFactoryLoaderTest {
    final Path pluginsPath = fixturesHome(FileSystemPluginFactoryLoader.class, "/plugins");
    final PluginFactoryLoader pluginFactoryLoader = new FileSystemPluginFactoryLoader();

    @Test
    public void pluginLoaderLoadsPluginFromJarFile() {
        ObjectFactory factory = new ObjectFactory("testgrp.TestPluginModule", pluginsPath.toString());

        PluginFactory plugin = pluginFactoryLoader.load(new PluginMetadata("pluginA", factory, null));

        assertThat(plugin, is(not(nullValue())));
        assertThat(plugin.getClass().getName(), is("testgrp.TestPluginModule"));
    }

    @Test(expectedExceptions = ConfigurationException.class, expectedExceptionsMessageRegExp =
            "Could not load a plugin factory for configuration=PluginMetadata\\{" +
                    "name=pluginA, " +
                    "factory=ObjectFactory\\{" +
                    "factoryClass=incorrect.plugin.class.name.TestPluginModule, " +
                    "classPath=.*[\\\\/]components[\\\\/]proxy[\\\\/]target[\\\\/]test-classes[\\\\/]plugins[\\\\/]oneplugin[\\\\/]testPluginA-1.0-SNAPSHOT.jar" +
                    "\\}\\}")
    public void providesMeaningfulErrorMessageWhenConfiguredFactoryClassCannotBeLoaded() throws URISyntaxException {
        String jarFile = "/plugins/oneplugin/testPluginA-1.0-SNAPSHOT.jar";
        Path pluginsPath = fixturesHome(PluginSuppliersTest.class, jarFile);

        ObjectFactory factory = new ObjectFactory("incorrect.plugin.class.name.TestPluginModule", pluginsPath.toString());
        PluginMetadata metadata = new PluginMetadata("pluginA", factory, null);
        new FileSystemPluginFactoryLoader().load(metadata);
    }
}