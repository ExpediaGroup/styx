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
package com.hotels.styx.infrastructure.configuration.json.mixins

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.hotels.styx.api.extension.service.TcpKeepAliveSettings

/**
 * Jackson annotations for [TcpKeepAliveSettings].
 */
@JsonDeserialize(builder = TcpKeepAliveSettings.Builder::class)
interface TcpKeepAliveSettingsMixin {
    @JsonProperty("keepAliveIdleTimeSeconds")
    fun keepAliveIdleTimeSeconds(): Int?

    @JsonProperty("keepAliveIntervalSeconds")
    fun keepAliveIntervalSeconds(): Int?

    @JsonProperty("keepAliveRetryCount")
    fun keepAliveRetryCount(): Int?

    /**
     * The builder for TcpKeepAliveSettings.
     */
    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    interface Builder {
        @JsonProperty("keepAliveIdleTimeSeconds")
        fun keepAliveIdleTimeSeconds(keepAliveIdleTimeSeconds: Int): Builder

        @JsonProperty("keepAliveIntervalSeconds")
        fun keepAliveIntervalSeconds(keepAliveIntervalSeconds: Int): Builder

        @JsonProperty("keepAliveRetryCount")
        fun keepAliveRetryCount(keepAliveRetryCount: Int): Builder
    }
}
