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
package com.hotels.styx.api.plugins.spi;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper for exceptions thrown from plugins.
 */
public class PluginException extends RuntimeException {
    private final String pluginName;

    public PluginException(String pluginName) {
        super(pluginName);
        this.pluginName = requireNonNull(pluginName);
    }

    public PluginException(String message, String pluginName) {
        super(pluginName + ": " + message);
        this.pluginName = requireNonNull(pluginName);
    }

    public PluginException(String message, Throwable cause, String pluginName) {
        super(pluginName + ": " + message, cause);
        this.pluginName = requireNonNull(pluginName);
    }

    public PluginException(Throwable cause, String pluginName) {
        super(pluginName + ": " + cause.getMessage(), cause);
        this.pluginName = requireNonNull(pluginName);
    }

    public String pluginName() {
        return pluginName;
    }
}
