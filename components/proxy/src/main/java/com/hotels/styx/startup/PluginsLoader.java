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
package com.hotels.styx.startup;

import com.google.common.collect.ImmutableList;
import com.hotels.styx.api.Environment;
import com.hotels.styx.proxy.plugin.NamedPlugin;
import com.hotels.styx.proxy.plugin.PluginSuppliers;

import java.util.List;

/**
 * Loads plugins from environment.
 */
public interface PluginsLoader {
    PluginsLoader PLUGINS_FROM_CONFIG = environment -> ImmutableList.copyOf(new PluginSuppliers(environment).fromConfigurations());

    List<NamedPlugin> load(Environment environment);
}
