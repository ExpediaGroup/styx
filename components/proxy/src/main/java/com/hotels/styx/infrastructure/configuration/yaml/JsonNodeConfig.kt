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
package com.hotels.styx.infrastructure.configuration.yaml

import com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hotels.styx.api.configuration.Configuration
import com.hotels.styx.api.configuration.ConversionException
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers.addStyxMixins
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional

/**
 * Configuration from a jackson JSON node. This class does not do any JSON parsing - it is just a bridge between the
 * jackson API and Styx API.
 */
class JsonNodeConfig private constructor(rootNode: JsonNode?, mapper: ObjectMapper) : Configuration {
    private val mapper: ObjectMapper
    private val rootNode: JsonNode

    /**
     * Construct an instance from a JSON node.
     *
     * @param rootNode a JSON node
     */
    constructor(rootNode: JsonNode?) : this(rootNode, YAML_MAPPER)

    /**
     * Construct an instance from a JSON node.
     *
     * @param rootNode a JSON node
     * @param mapper   mapper to convert JSON into objects
     */
    init {
        this.rootNode = rootNode ?: NullNode.getInstance()
        this.mapper = addStyxMixins(mapper)
    }

    override fun get(key: String): Optional<String> = get(key, String::class.java)

    override fun <T> get(property: String, tClass: Class<T>): Optional<T> =
        nodeAt(property)
            .map { node ->
                if (tClass == Path::class.java) {
                    Paths.get(node.textValue()) as T
                } else {
                    parseNodeToClass(node, tClass)
                }
            }

    private fun nodeAt(property: String): Optional<JsonNode> = NodePath(property).findMatchingDescendant(rootNode)

    private fun <T> parseNodeToClass(node: JsonNode, tClass: Class<T>): T = mapper.readValue(node.traverse(), tClass)

    @Throws(ConversionException::class)
    override fun <X> `as`(type: Class<X>): X = parseNodeToClass(rootNode, type)

    override fun toString(): String = rootNode.toString()

    companion object {
        private val YAML_MAPPER = addStyxMixins(
            ObjectMapper(YAMLFactory()).registerKotlinModule())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(AUTO_CLOSE_SOURCE, true)
    }
}
