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

import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Optional;

import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ObjectFactoryTest {
    Path PLUGINS_FIXTURE_PATH = fixturesHome(ObjectFactoryTest.class, "/plugins");
    String SINGLE_PLUGIN = PLUGINS_FIXTURE_PATH.resolve("oneplugin").toString();
    String EMPTY = PLUGINS_FIXTURE_PATH.resolve("empty").toString();

    ObjectFactory factory = new ObjectFactory("testgrp.TestPluginModule", SINGLE_PLUGIN);

    @Test
    public void shouldLoadAllPluginsModuleInTheSpecifiedPath() {
        Optional<PluginFactory> pluginFactory = factory.newInstance(PluginFactory.class);
        assertThat(pluginFactory.get().getClass().getCanonicalName(), is("testgrp.TestPluginModule"));
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void errorsIfClassLoadedIsNotInstanceOfExpectedType() {
        factory.newInstance(Runnable.class);
    }

    @Test
    public void returnsAbsentIfNoSuchClass() {
        ObjectFactory factory = new ObjectFactory("testgrp.NonExistent", SINGLE_PLUGIN);

        assertThat(factory.newInstance(PluginFactory.class), isAbsent());
    }

    @Test
    public void returnsAbsentIfPathDoesNotExist() {
        ObjectFactory factory = new ObjectFactory("testgrp.TestPluginModule", EMPTY);

        Optional<PluginFactory> pluginFactory = factory.newInstance(PluginFactory.class);
        assertThat(pluginFactory, isAbsent());
    }
}