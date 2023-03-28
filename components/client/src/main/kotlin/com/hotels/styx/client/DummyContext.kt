/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.client

import com.hotels.styx.api.HttpInterceptor
import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.Executor

/**
 * Passed in as an argument when a [HttpInterceptor.Context] is needed, but there is no context available
 * (because the call was not made within an interceptor chain).
 */
object DummyContext : HttpInterceptor.Context {
    override fun add(key: String, value: Any) {
        // Empty by design
    }

    @Deprecated("Deprecated in Java", ReplaceWith("getIfAvailable"))
    override fun <T : Any> get(key: String, clazz: Class<T>): T? = null

    override fun isSecure(): Boolean = false

    override fun clientAddress(): Optional<InetSocketAddress> = Optional.empty()

    override fun executor(): Executor = Executor { it.run() }
}
