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
package com.hotels.styx.startup;

import com.hotels.styx.configstore.ConfigStore;
import com.hotels.styx.proxy.ProxyServerConfig;
import com.hotels.styx.server.HttpServer;

import static com.hotels.styx.startup.ProxyStatusNotifications.ProxyStatus.COMPLETE;
import static com.hotels.styx.startup.ProxyStatusNotifications.ProxyStatus.DISABLED;
import static com.hotels.styx.startup.ProxyStatusNotifications.ProxyStatus.FAILED;
import static com.hotels.styx.startup.ProxyStatusNotifications.ProxyStatus.INCOMPLETE;
import static java.util.Objects.requireNonNull;

/**
 * For setting the proxy startup status in the config store.
 */
public final class ProxyStatusNotifications {
    public static final String PROXY_HTTP_STATUS_KEY = "startup.proxy.http";
    public static final String PROXY_HTTPS_STATUS_KEY = "startup.proxy.https";

    private ProxyStatusNotifications() {
    }

    /**
     * Notify config store of the initial state of both proxy connectors - are they in progress (incomplete) or disabled.
     *
     * @param configStore config store
     * @param config      proxy server config
     */
    public static void notifyProxyStatus(ConfigStore configStore, ProxyServerConfig config) {
        configStore.set(PROXY_HTTP_STATUS_KEY, config.httpConnectorConfig().map(any -> INCOMPLETE).orElse(DISABLED));
        configStore.set(PROXY_HTTPS_STATUS_KEY, config.httpsConnectorConfig().map(any -> INCOMPLETE).orElse(DISABLED));
    }

    /**
     * Notify the config store when the proxy server has finished starting up.
     *
     * @param configStore config store
     * @param server      proxy server
     */
    public static void notifyProxyStarted(ConfigStore configStore, HttpServer server) {
        if (server.httpAddress() != null) {
            configStore.set(PROXY_HTTP_STATUS_KEY, COMPLETE);
        }

        if (server.httpsAddress() != null) {
            configStore.set(PROXY_HTTPS_STATUS_KEY, COMPLETE);
        }
    }

    /**
     * Notify the config store when the proxy server has failed to start up.
     *
     * @param configStore config store
     */
    public static void notifyProxyFailed(ConfigStore configStore) {
        configStore.set(PROXY_HTTP_STATUS_KEY, FAILED);
        configStore.set(PROXY_HTTPS_STATUS_KEY, FAILED);
    }

    /**
     * Status for the proxy to be in during start-up.
     */
    public enum ProxyStatus {
        INCOMPLETE("incomplete"),
        COMPLETE("complete"),
        FAILED("failed"),
        DISABLED("disabled");

        private final String description;

        ProxyStatus(String description) {
            this.description = requireNonNull(description);
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
