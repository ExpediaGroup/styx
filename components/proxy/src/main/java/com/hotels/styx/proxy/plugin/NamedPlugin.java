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

import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import com.hotels.styx.api.HttpRequest;

/**
 * Represents a plugin that has been loaded into styx under its configured name.
 */
public final class NamedPlugin implements Plugin {
    private final String name;
    private final Plugin plugin;

    private volatile boolean enabled = true;

    private NamedPlugin(String name, Plugin plugin) {
        checkArgument(!(plugin instanceof NamedPlugin), "Cannot wrap %s in %s", NamedPlugin.class.getName(), NamedPlugin.class.getName());

        this.name = requireNonNull(name);
        this.plugin = requireNonNull(plugin);
    }

    public static NamedPlugin namedPlugin(String name, Plugin plugin) {
        return new NamedPlugin(name, plugin);
    }

    Plugin originalPlugin() {
        return plugin;
    }

    public String name() {
        return name;
    }

    /**
     * Enables or disables the plugin.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns true if the plugin is enabled, false if disabled.
     *
     * @return true if the plugin is enabled, false if disabled
     */
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void styxStarting() {
        plugin.styxStarting();
    }

    @Override
    public void styxStopping() {
        plugin.styxStopping();
    }

    @Override
    public Map<String, HttpHandler> adminInterfaceHandlers() {
        return plugin.adminInterfaceHandlers();
    }

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        if (enabled) {
            return plugin.intercept(request, chain);
        }

        return chain.proceed(request);
    }
}
