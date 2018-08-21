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

import com.hotels.styx.api.extension
import com.hotels.styx.api.extension.service.HealthCheckConfig.{DEFAULT_HEALTHY_THRESHOLD_VALUE, DEFAULT_HEALTH_CHECK_INTERVAL, DEFAULT_TIMEOUT_VALUE, DEFAULT_UNHEALTHY_THRESHOLD_VALUE}

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.{Duration, _}

case class HealthCheckConfig(uri: Option[String],
                             interval: Duration = Duration(DEFAULT_HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS),
                             timeout: Duration = Duration(DEFAULT_TIMEOUT_VALUE, MILLISECONDS),
                             healthyThreshold: Int = DEFAULT_HEALTHY_THRESHOLD_VALUE,
                             unhealthyThreshold: Int = DEFAULT_UNHEALTHY_THRESHOLD_VALUE
                            ) {
  def asJava: extension.service.HealthCheckConfig = extension.service.HealthCheckConfig.newHealthCheckConfigBuilder()
    .uri(uri.orNull)
    .interval(interval.toMillis)
    .timeout(timeout.toMillis)
    .healthyThreshold(healthyThreshold)
    .unhealthyThreshold(unhealthyThreshold)
    .build()
}

object HealthCheckConfig {
  def fromJava(from: extension.service.HealthCheckConfig): HealthCheckConfig =
    HealthCheckConfig(
      uri = from.uri().asScala,
      interval = Duration(from.intervalMillis(), TimeUnit.MILLISECONDS),
      timeout = Duration(from.timeoutMillis(), TimeUnit.MILLISECONDS),
      healthyThreshold = from.healthyThreshold(),
      unhealthyThreshold = from.unhealthyThreshold()
    )
}
