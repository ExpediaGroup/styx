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