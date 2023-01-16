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
package com.hotels.styx.api.extension.service

import java.lang.StringBuilder

/**
 * Programmatically configurable TCP keepalive settings.
 */
data class TcpKeepAliveSettings(
    val keepAliveIdleTimeSeconds: Int?,
    val keepAliveIntervalSeconds: Int?,
    val keepAliveRetryCount: Int?
) {
    private constructor(builder: Builder) : this(
        keepAliveIdleTimeSeconds = builder.keepAliveIdleTimeSeconds,
        keepAliveIntervalSeconds = builder.keepAliveIntervalSeconds,
        keepAliveRetryCount = builder.keepAliveRetryCount
    )

    /**
     * A builder for [TcpKeepAliveSettings].
     */
    class Builder(
        var keepAliveIdleTimeSeconds: Int? = null,
        var keepAliveIntervalSeconds: Int? = null,
        var keepAliveRetryCount: Int? = null
    ) {
        constructor(tcpKeepAliveSettings: TcpKeepAliveSettings) : this() {
            this.keepAliveIdleTimeSeconds = tcpKeepAliveSettings.keepAliveIdleTimeSeconds
            this.keepAliveIntervalSeconds = tcpKeepAliveSettings.keepAliveIntervalSeconds
            this.keepAliveRetryCount = tcpKeepAliveSettings.keepAliveRetryCount
        }

        fun keepAliveIdleTimeSeconds(keepAliveIdleTimeSeconds: Int?) = apply {
            this.keepAliveIdleTimeSeconds = keepAliveIdleTimeSeconds
        }

        fun keepAliveIntervalSeconds(keepAliveIntervalSeconds: Int?) = apply {
            this.keepAliveIntervalSeconds = keepAliveIntervalSeconds
        }

        fun keepAliveRetryCount(keepAliveRetryCount: Int?) = apply {
            this.keepAliveRetryCount = keepAliveRetryCount
        }

        fun build() = TcpKeepAliveSettings(this)
    }

    fun keepAliveIdleTimeSeconds(): Int? = keepAliveIdleTimeSeconds

    fun keepAliveIntervalSeconds(): Int? = keepAliveIntervalSeconds

    fun keepAliveRetryCount(): Int? = keepAliveRetryCount

    fun newCopy(): Builder = Builder(this)

    override fun toString(): String = StringBuilder(128)
        .append(this.javaClass.simpleName)
        .append("{keepAliveIdleTimeSeconds=")
        .append(keepAliveIdleTimeSeconds)
        .append(", keepAliveIntervalSeconds=")
        .append(keepAliveIntervalSeconds)
        .append(", keepAliveRetryCount=")
        .append(keepAliveRetryCount)
        .append("}")
        .toString()
}
