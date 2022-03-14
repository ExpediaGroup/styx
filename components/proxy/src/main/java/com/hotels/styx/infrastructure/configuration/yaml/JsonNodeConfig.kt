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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hotels.styx.api.configuration.Configuration
import com.hotels.styx.api.configuration.ConversionException
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects
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
        this.mapper = ObjectMappers.addStyxMixins(Objects.requireNonNull(mapper))
    }

    override fun get(key: String): Optional<String> = get(key, String::class.java)

    override fun <T> get(property: String, tClass: Class<T>): Optional<T> =
        nodeAt(property)
            .map { node: JsonNode ->
                if (tClass == Path::class.java) {
                    return@map Paths.get(node.textValue()) as T
                }
                parseNodeToClass(node, tClass)
            }

    private fun nodeAt(property: String): Optional<JsonNode> = NodePath(property).findMatchingDescendant(rootNode)

    private fun <T> parseNodeToClass(node: JsonNode, tClass: Class<T>): T {
        val parser = node.traverse()
        return try {
            mapper.readValue(parser, tClass)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(ConversionException::class)
    override fun <X> `as`(type: Class<X>): X = parseNodeToClass(rootNode, type)

    override fun toString(): String = rootNode.toString()

    companion object {
        private val YAML_MAPPER = ObjectMappers.addStyxMixins(
            ObjectMapper(YAMLFactory()).registerKotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true)
    }
}
