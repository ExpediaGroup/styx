/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.spi.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.util.Objects

/**
 * Factory/configuration block.
 */
class SpiExtension @JsonCreator constructor(
    @JsonProperty("factory") factory: SpiExtensionFactory?,
    @JsonProperty("config") config: JsonNode?,
    @JsonProperty("enabled") enabled: Boolean?
) {
    private val factory: SpiExtensionFactory
    private val config: JsonNode
    private val enabled: Boolean

    init {
        this.factory = requireNotNull(factory) { "Factory attribute missing" }
        this.config = config ?: NullNode.getInstance()
        this.enabled = enabled == null
    }

    fun factory(): SpiExtensionFactory = factory

    fun config(): JsonNode = config

    fun enabled(): Boolean = enabled

    fun <T> config(configClass: Class<T>?): T = MAPPER.readValue(config.traverse(), configClass)

    override fun hashCode(): Int = Objects.hashCode(factory)

    override fun equals(obj: Any?): Boolean =
        if (this === obj) {
            true
        } else if (obj == null || javaClass != obj.javaClass) {
            false
        } else {
            val other = obj as SpiExtension
            factory == other.factory
        }

    override fun toString(): String =
        StringBuilder(32)
            .append(this.javaClass.simpleName)
            .append("{factory=")
            .append(factory)
            .append('}')
            .toString()

    companion object {
        private val MAPPER =
            ObjectMapper(YAMLFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
