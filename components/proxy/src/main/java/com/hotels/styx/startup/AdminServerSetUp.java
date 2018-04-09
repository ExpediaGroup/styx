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

import com.hotels.styx.admin.AdminServerBuilder;
import com.hotels.styx.api.service.BackendService;
import com.hotels.styx.api.service.spi.Registry;
import com.hotels.styx.server.HttpServer;

/**
 * Used to set-up the administration server for Styx.
 */
public final class AdminServerSetUp {
    private AdminServerSetUp() {
    }

    public static HttpServer createAdminServer(StyxServerComponents config) {
        // This comment was originally in the class StyxServer
        // TODO: Pass all backend Service Registries to AdminServerBuilder:
        // - only one backendServicesRegistry is passed in to the admin interface. Instead we
        //   should pass all of them:
        return new AdminServerBuilder(config.environment())
                .backendServicesRegistry((Registry<BackendService>) config.services().get("backendServiceRegistry"))
                .plugins(config.plugins())
                .build();
    }
}
