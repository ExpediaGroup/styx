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
package com.hotels.styx.support

import com.hotels.styx.metrics.StyxMetrics

import scala.compat.java8.OptionConverters._

sealed trait CodaHaleMetrics

case class Meter(count: Int,
                 m1Rate: Double,
                 m5Rate: Double,
                 m15Rate: Double,
                 meanRate: Double,
                 units: String) extends CodaHaleMetrics

case class Timer(count: Int,
                 max: Double,
                 mean: Double,
                 min: Double,
                 p50: Double,
                 p75: Double,
                 p95: Double,
                 p98: Double,
                 p99: Double,
                 p999: Double,
                 stdDev: Double,
                 m15Rate: Double,
                 m1Rate: Double,
                 m5Rate: Double,
                 meanRate: Double,
                 durationUnits: String,
                 rateUnits: String) extends CodaHaleMetrics

class CodaHaleMetricsFacade(val metrics: StyxMetrics) {
  def count(name: String) : Option[Long] = {
    metrics.counter(name).asScala.map(_.longValue())
  }

  def meter(name: String): Option[Meter] = {
    metrics.meter(name).asScala
  }

  def filterByName(regex : String) : CodaHaleMetricsFacade = {
    new CodaHaleMetricsFacade(metrics.filterByName(regex))
  }

  def gauge(name: String): Option[Int] = {
    metrics.gauge(name, classOf[Integer]).asScala.map(_.intValue)
  }

  override def toString: String = metrics.toString
}
