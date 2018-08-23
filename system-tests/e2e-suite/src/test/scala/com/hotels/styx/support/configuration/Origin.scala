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

case class Origin(host: String,
                  port: Int,
                  id: String = Origin.default.id().toString,
                  appId: String = Origin.default.applicationId().toString
                 ) {
  def asJava(): com.hotels.styx.api.extension.Origin =
    com.hotels.styx.api.extension.Origin.newOriginBuilder(host, port)
      .applicationId(appId)
      .id(id)
      .build()

  def hostAsString: String = s"$host:$port"
}

object Origin {
  val default = com.hotels.styx.api.extension.Origin.newOriginBuilder("localhost", 0).build()

  def fromJava(from: com.hotels.styx.api.extension.Origin): Origin =
    Origin(from.host().getHostText, from.host().getPort, from.id().toString, from.applicationId().toString)
}
