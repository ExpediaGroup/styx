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
package com.hotels.styx.support.configuration

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.{MILLISECONDS, SECONDS}

import com.hotels.styx.api.extension
import com.hotels.styx.api.extension.service.StickySessionConfig.Builder

import scala.concurrent.duration.Duration

case class StickySessionConfig(enabled: Boolean = StickySessionConfigDefaults.enabled,
                               timeout: Duration = StickySessionConfigDefaults.timeout
                              ) {
  def asJava: extension.service.StickySessionConfig = new Builder()
    .enabled(enabled)
    .timeout(timeout.toMillis.toInt, MILLISECONDS)
    .build()
}

object StickySessionConfigDefaults {
  private val defaults = new Builder().build()
  val enabled = defaults.stickySessionEnabled()
  val timeout = Duration(defaults.stickySessionTimeoutSeconds(), SECONDS)
}

object StickySessionConfig {
  def fromJava(from: extension.service.StickySessionConfig): StickySessionConfig =
    StickySessionConfig(
      enabled = from.stickySessionEnabled(),
      timeout = Duration(from.stickySessionTimeoutSeconds(), TimeUnit.SECONDS)
    )
}
