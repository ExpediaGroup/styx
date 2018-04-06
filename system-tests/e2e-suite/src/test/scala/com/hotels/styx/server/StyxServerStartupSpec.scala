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
package com.hotels.styx.server

import com.hotels.styx.StyxClientSupplier
import com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry
import com.hotels.styx.support.ResourcePaths.fixturesHome
import com.hotels.styx.support.configuration.StyxConfig
import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, ShouldMatchers}

class StyxServerStartupSpec extends FunSpec
  with StyxClientSupplier
  with ShouldMatchers
  with Eventually {

  val origins = fixturesHome(classOf[StyxServerStartupSpec], "/conf/origins/origins-incorrect.yml")
  val fileBasedBackendsRegistry = FileBackedBackendServicesRegistry.create(origins.toString)

  it ("should not start up when incorrect origins file is encountered") {
    an [IllegalStateException] should be thrownBy StyxConfig().startServer(fileBasedBackendsRegistry)
  }

}
