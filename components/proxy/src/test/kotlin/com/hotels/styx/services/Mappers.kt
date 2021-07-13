/*
  Copyright (C) 2013-2021 Expedia Inc.

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
