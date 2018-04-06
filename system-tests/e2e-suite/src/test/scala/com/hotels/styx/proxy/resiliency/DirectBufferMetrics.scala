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
package com.hotels.styx.proxy.resiliency

import java.util.Optional

import com.hotels.styx.utils.MetricsSnapshot

object DirectBufferMetrics {

  def directBufferMetrics(adminPort: Int) = {
    val snapshot = MetricsSnapshot.downloadFrom("localhost", adminPort)
    def toScalaOption[T](opt: Optional[T]) = if (opt.isPresent) Some(opt.get) else None

    for {
      capacity <- toScalaOption(snapshot.gaugeValue("jvm.bufferpool.direct.capacity"))
      count <- toScalaOption(snapshot.gaugeValue("jvm.bufferpool.direct.count"))
      used <- toScalaOption(snapshot.gaugeValue("jvm.bufferpool.direct.used"))
    } yield DirectBufferMetrics(capacity, count, used)
  }

}

case class DirectBufferMetrics(capacity: Int, count: Int, used: Int)
