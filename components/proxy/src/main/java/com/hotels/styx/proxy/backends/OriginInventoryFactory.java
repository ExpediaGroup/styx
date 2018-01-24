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

import com.hotels.styx.Environment;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * TBD.
 */
public class OriginInventoryFactory {
    private final Environment environment;
    private final int clientWorkerThreadsCount;
    private static final Logger LOGGER = LoggerFactory.getLogger(OriginInventoryFactory.class);

    public OriginInventoryFactory(Environment environment, int clientWorkerThreadsCount) {
        this.environment = requireNonNull(environment);
        this.clientWorkerThreadsCount = clientWorkerThreadsCount;
    }

    public OriginsInventory newInventory(BackendService backendService) {

        boolean requestLoggingEnabled = environment.styxConfig().get("request-logging.outbound.enabled", Boolean.class)
                .orElse(false);

        boolean longFormat = environment.styxConfig().get("request-logging.outbound.longFormat", Boolean.class)
                .orElse(false);

        //TODO: origins inventory builder assumes that appId/originId tuple is unique and it will fail on metrics registration.
        return new OriginsInventory.Builder(backendService)
                .version(environment.buildInfo().releaseVersion())
                .eventBus(environment.eventBus())
                .metricsRegistry(environment.metricRegistry())
                .connectionFactory(new NettyConnectionFactory.Builder()
                        .name("Styx")
                        .clientWorkerThreadsCount(clientWorkerThreadsCount)
                        .metricRegistry(environment.metricRegistry())
                        .requestLoggingEnabled(requestLoggingEnabled)
                        .longFormat(longFormat)
                        .responseTimeoutMillis(backendService.responseTimeoutMillis())
                        .tlsSettings(backendService.tlsSettings().orElse(null)).build())
                .build();
    }
}
