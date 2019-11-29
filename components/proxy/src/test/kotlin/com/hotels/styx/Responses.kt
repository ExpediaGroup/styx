/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.LiveHttpResponse
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets


fun Eventual<LiveHttpResponse>.wait(maxBytes: Int = 100*1024, debug: Boolean = false) = this.toMono()
        .flatMap { it.aggregate(maxBytes).toMono() }
        .doOnNext {
            if (debug) {
                println("${it.status()} - ${it.headers()} - ${it.bodyAs(StandardCharsets.UTF_8)}")
            }
        }
        .block()
