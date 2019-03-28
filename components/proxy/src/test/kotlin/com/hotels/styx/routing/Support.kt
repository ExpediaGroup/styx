package com.hotels.styx.routing

import com.hotels.styx.infrastructure.configuration.yaml.YamlConfig
import com.hotels.styx.routing.config.RouteHandlerDefinition

fun configBlock(text: String) = YamlConfig(text).get("config", RouteHandlerDefinition::class.java).get()
