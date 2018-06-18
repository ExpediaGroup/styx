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
package com.hotels.styx

import com.hotels.styx.api.service.BackendService
import com.hotels.styx.infrastructure.MemoryBackedRegistry
import com.hotels.styx.support.configuration.StyxBackend

trait BackendServicesRegistrySupplier {

  def setBackends(registry: MemoryBackedRegistry[BackendService], pathAndbackends: (String, StyxBackend)*) = {
    resetBackendRoutes(registry)

    pathAndbackends.foreach {
      case (path, backend) =>
        registry.add(backend.toBackend(path).asJava)
    }
  }

  def resetBackendRoutes(registry: MemoryBackedRegistry[BackendService]) = {
    registry.reset()
  }

}
