package com.hotels.styx.services

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers

internal fun <T> JsonNode.get(targetType: Class<T>): T = MAPPER.readValue(this.traverse(), targetType)

internal fun <T> JsonNode.get(child: String, targetType: Class<T>): T = MAPPER.readValue(this.get(child).traverse(), targetType)

internal fun <T> JsonNode.get(child: String, targetType: TypeReference<T>): T = MAPPER.readValue(this.get(child).traverse(), targetType)

private val MAPPER = ObjectMappers
        .addStyxMixins(ObjectMapper(YAMLFactory()))
        .registerKotlinModule()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true)
