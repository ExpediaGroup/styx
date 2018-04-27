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
package testgrp;

import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.slf4j.Logger;

import static depend.ExampleDependency.exampleDependencyProperty;
import static org.slf4j.LoggerFactory.getLogger;

@SuppressWarnings("unused")
public class TestPluginModule implements PluginFactory {
    private static final Logger LOGGER = getLogger(TestPluginModule.class);

    @Override
    public Plugin create(PluginFactory.Environment environment) {
        // If this line executes without error then the dependency-relationship is intact.
        LOGGER.info(exampleDependencyProperty);

        return new TestPlugin(environment);
    }
}
