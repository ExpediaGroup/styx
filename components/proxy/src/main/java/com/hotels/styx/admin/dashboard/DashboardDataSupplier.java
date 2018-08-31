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
package com.hotels.styx.admin.dashboard;

import com.hotels.styx.Environment;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.Version;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;
import com.hotels.styx.api.extension.service.spi.Registry;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static com.hotels.styx.StyxConfig.NO_JVM_ROUTE_SET;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Supplier of the {@link com.hotels.styx.admin.dashboard.DashboardData}.
 */
public class DashboardDataSupplier implements Supplier<DashboardData>, Registry.ChangeListener<BackendService> {
    private static final Logger LOG = getLogger(DashboardDataSupplier.class);

    private volatile DashboardData data;
    private final Registry<BackendService> backendServicesRegistry;
    private final Environment environment;
    private final String jvmRouteName;
    private final Version buildInfo;

    public DashboardDataSupplier(Registry<BackendService> backendServicesRegistry, Environment environment, StyxConfig styxConfig) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry);
        this.environment = requireNonNull(environment);
        this.jvmRouteName = styxConfig.get("jvmRouteName", String.class).orElse(NO_JVM_ROUTE_SET);
        this.buildInfo = environment.buildInfo();
        this.data = updateDashboardData(backendServicesRegistry);

        this.backendServicesRegistry.addListener(this);
    }

    @Override
    public void onChange(Registry.Changes<BackendService> changes) {
        LOG.info("received new services changes set {}", changes);
        data = updateDashboardData(backendServicesRegistry);
        environment.eventBus().post(new GetOriginsInventorySnapshot());
    }

    private DashboardData updateDashboardData(Registry<BackendService> backendServices) {
        if (this.data != null) {
            this.data.unregister();
        }

        return new DashboardData(environment.metricRegistry(), backendServices, jvmRouteName, buildInfo, environment.eventBus());
    }

    @Override
    public DashboardData get() {
        return data;
    }

}
