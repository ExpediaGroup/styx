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
package com.hotels.styx.server

import com.hotels.styx.api.HttpInterceptor
import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor


/**
 * ConcurrentHashMap backed implementation of HttpInterceptor.Context.
 *
 * @param secure true if the request was received via SSL
 * @param clientAddress address that request came from, or null if not-applicable
 */
class HttpInterceptorContext(private val secure: Boolean, private val clientAddress: InetSocketAddress?, private val executor: Executor?) :
    HttpInterceptor.Context {
    private val context: MutableMap<String, Any> = ConcurrentHashMap()

    override fun add(key: String, value: Any) {
        context[key] = value
    }

    @Deprecated("Deprecated in Java")
    override fun <T> get(key: String, clazz: Class<T>): T = context[key] as T

    override fun isSecure() = secure

    override fun clientAddress() = Optional.ofNullable(clientAddress)

    override fun executor(): Executor? = executor

    companion object {
        // Visible for testing
        @Deprecated("use the constructor instead")
        @JvmStatic
        fun create(): HttpInterceptor.Context = HttpInterceptorContext(false, null) { it.run() }
    }
}
