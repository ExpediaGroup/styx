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

import com.hotels.styx.api.Resource;
import com.hotels.styx.api.io.FileResource;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;

public class FileSystemPluginsSupplierTest {
    Path PLUGINS_FIXTURE_PATH = fixturesHome(FileSystemPluginsSupplierTest.class, "/plugins");
    Path SINGLE_PLUGIN = PLUGINS_FIXTURE_PATH.resolve("oneplugin");
    Path EMPTY = PLUGINS_FIXTURE_PATH.resolve("empty");

    @Test
    public void shouldLoadAllPluginsModuleInTheSpecifiedPath() {
        System.out.println("single plugin: " + SINGLE_PLUGIN);
        FileSystemPluginsSupplier pluginsSupplier = FileSystemPluginsSupplier.fromDirectoryListing(SINGLE_PLUGIN);
        PluginFactory pluginModule = getOnlyElement(pluginsSupplier.get());
        assertThat(pluginModule.getClass().getCanonicalName(), is("testgrp.TestPluginModule"));
    }

    @Test
    public void shouldLoadAllPluginModulesFromSpecifiedJarFiles() {
        String jarFile = "/plugins/oneplugin/testPluginA-1.0-SNAPSHOT.jar";
        Path pluginJarFile = fixturesHome(FileSystemPluginsSupplierTest.class, jarFile);

        List<Resource> jarFiles = singletonList(fileResource(pluginJarFile));

        FileSystemPluginsSupplier pluginsSupplier = new FileSystemPluginsSupplier(jarFiles);
        PluginFactory pluginModule = getOnlyElement(pluginsSupplier.get());
        assertThat(pluginModule.getClass().getCanonicalName(), is("testgrp.TestPluginModule"));
    }

    @Test
    public void emptyPluginsFolderResultsInZeroPluginsLoaded() {
        FileSystemPluginsSupplier pluginsSupplier = FileSystemPluginsSupplier.fromDirectoryListing(EMPTY);
        assertThat(pluginsSupplier.get(), is(emptyIterable()));
    }

    private Resource fileResource(Path pluginJarFile) {
        return new FileResource(pluginJarFile.toFile());
    }
}
