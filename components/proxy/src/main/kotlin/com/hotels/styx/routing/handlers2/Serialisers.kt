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
package com.hotels.styx.routing.handlers2

import com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hotels.styx.InetServer
import com.hotels.styx.infrastructure.configuration.json.ObjectMappers
import com.hotels.styx.routing.RoutingObject
import com.hotels.styx.routing.RoutingObjectYamlRecord
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config2.StyxObject
import com.hotels.styx.routing.config2.RoutingObjectDeserialiser
import com.hotels.styx.routing.config2.RoutingObjectRecordDeserialiser
import com.hotels.styx.routing.config2.ServerObjectDeserialiser
import com.hotels.styx.routing.config2.ServerObjectRecordDeserialiser
import com.hotels.styx.routing.config2.StyxObjectSerialiserModifier


fun objectMmapper(descriptors: Map<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>>) = ObjectMappers.addStyxMixins(ObjectMapper(YAMLFactory()))
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(KotlinModule())
        .configure(AUTO_CLOSE_SOURCE, true)
        .registerModule(SimpleModule().also {
            it.setSerializerModifier(StyxObjectSerialiserModifier())
            it.addDeserializer(StyxObject::class.java, RoutingObjectDeserialiser(descriptors))
            it.addDeserializer(RoutingObjectYamlRecord::class.java, RoutingObjectRecordDeserialiser(descriptors))
        })

fun serverObjectMmapper(descriptors: Map<String, Builtins.StyxObjectDescriptor<StyxObject<InetServer>>>) = ObjectMappers.addStyxMixins(ObjectMapper(YAMLFactory()))
        .disable(FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(KotlinModule())
        .configure(AUTO_CLOSE_SOURCE, true)
        .registerModule(SimpleModule().also {
            it.setSerializerModifier(StyxObjectSerialiserModifier())
            it.addDeserializer(StyxObject::class.java, ServerObjectDeserialiser(descriptors))
            it.addDeserializer(RoutingObjectYamlRecord::class.java, ServerObjectRecordDeserialiser(descriptors))
        })
