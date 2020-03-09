/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.executors

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.ExecutorFactory
import com.hotels.styx.NettyExecutor
import com.hotels.styx.config.schema.SchemaDsl
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig

class NettyExecutorFactory : ExecutorFactory {
    private fun parseConfig(configuration: JsonNode) = JsonNodeConfig(configuration).`as`(NettyExecutorConfig::class.java)

    override fun create(name: String, configuration: JsonNode): NettyExecutor {
        val config = parseConfig(configuration)
        return NettyExecutor.create(config.namePattern, config.threads)
    }

    companion object {
        @JvmField
        val SCHEMA = SchemaDsl.`object`(
                SchemaDsl.field("threads", SchemaDsl.integer()),
                SchemaDsl.field("namePattern", SchemaDsl.string())
        )
    }
}

private val mapper = ObjectMappers.addStyxMixins(ObjectMapper(YAMLFactory()))
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true)

internal data class NettyExecutorConfig(
        val threads: Int = 0,
        val namePattern: String = "netty-executor") {
    fun asJsonNode(): JsonNode = mapper.readTree(mapper.writeValueAsString(this))
}
