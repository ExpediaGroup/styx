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
package com.hotels.styx.proxy.backends;

import com.hotels.styx.client.applications.BackendService;

class BackendServiceSupport {

    static Boolean networkConfigurationChanged(BackendService old, BackendService nueve) {
        boolean poolChanged = !old.connectionPoolConfig().equals(nueve.connectionPoolConfig());
        boolean tlsSettingsChanged = !old.tlsSettings().equals(nueve.tlsSettings());

        return poolChanged || tlsSettingsChanged;
    }

    static Boolean clientConfigurationChanged(BackendService old, BackendService nueve) {
        boolean stickySessionChanged = !old.stickySessionConfig().equals(nueve.stickySessionConfig());
        boolean rewritesChanged = !old.rewrites().equals(nueve.rewrites());
        boolean responseTimeoutChanged = old.responseTimeoutMillis() != nueve.responseTimeoutMillis();

        return stickySessionChanged || rewritesChanged || responseTimeoutChanged;
    }

    static Boolean originConfigurationChanged(BackendService old, BackendService nueve) {
        return !old.origins().equals(nueve.origins());
    }
}
