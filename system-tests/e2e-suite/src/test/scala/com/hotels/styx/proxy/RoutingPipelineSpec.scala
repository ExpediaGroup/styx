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
package com.hotels.styx.proxy

import com.hotels.styx.StyxProxySpec
import com.hotels.styx.routing.handlers.StaticResponseHandler.StaticResponseConfig
//import com.hotels.styx.support.configuration.routing.{ConfigBlock, InterceptorPipelineConfig, StaticResponseConfig}
import com.hotels.styx.support.configuration.{ProxyConfig, StyxConfig}
import org.scalatest.{BeforeAndAfter, FunSpec}

//
//
//class RoutingPipelineSpec extends FunSpec with StyxProxySpec with BeforeAndAfter {
//
//  private val y = ConfigBlock("StaticResponse", "", StaticResponseConfig(200, "Hello, world"))
//
////  val pipeline = InterceptorPipelineConfig(List("rewrite", "log"), y)
//
//  override def styxConfig: StyxConfig = StyxConfig(
//    proxyConfig = ProxyConfig(requestTimeoutMillis = 300),
//
//  )
//}
//
