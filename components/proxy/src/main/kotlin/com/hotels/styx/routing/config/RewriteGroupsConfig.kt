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
package com.hotels.styx.routing.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.hotels.styx.api.Resource
import com.hotels.styx.api.extension.service.RewriteConfig
import com.hotels.styx.api.extension.service.RewriteRule
import com.hotels.styx.common.io.ResourceFactory.newResource
import com.hotels.styx.infrastructure.configuration.ConfigurationParser
import com.hotels.styx.infrastructure.configuration.ConfigurationSource
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat

@JsonDeserialize(using = RewriteGroupsConfigDeserializer::class)
class RewriteGroupsConfig(
    groups: Map<String, List<RewriteRule>>? = mapOf()
): HashMap<String, List<RewriteRule>>(groups)

private class RewriteGroupsConfigDeserializer: JsonDeserializer<RewriteGroupsConfig>() {

    companion object {
        private const val CONFIG_FILE = "configFile"
        private const val URL_PATTERN = "urlPattern"
        private const val REPLACEMENT = "replacement"
        private const val UNCHECKED_CAST = "UNCHECKED_CAST"
    }

    @Suppress(UNCHECKED_CAST)
    private val toRewriteConfigGroups: (Map.Entry<Any?, Any?>) -> Pair<String, List<RewriteConfig>> = {
        val configs: List<RewriteConfig> = (it.value as List<Map<String, String>>)
            .map { configNode ->
                RewriteConfig(
                    configNode[URL_PATTERN],
                    configNode[REPLACEMENT]
                )
            }
        it.key as String to configs
    }

    override fun deserialize(jsonParser: JsonParser?, context: DeserializationContext): RewriteGroupsConfig {
        val node: JsonNode = jsonParser?.readValueAsTree() ?: return RewriteGroupsConfig()
        val yamlConfig: YamlConfiguration =
            if (node.has(CONFIG_FILE))
                loadYamlConfiguration(newResource(node.get(CONFIG_FILE).asText()))
            else
                YamlConfiguration(node)

        val groups: Map<String, List<RewriteConfig>> =
            yamlConfig.`as`(Map::class.java)
                .map(toRewriteConfigGroups)
                .toMap()

        return RewriteGroupsConfig(groups)
    }

    private fun loadYamlConfiguration(yaml: Resource): YamlConfiguration =
        ConfigurationParser.Builder<YamlConfiguration>()
            .format(YamlConfigurationFormat.YAML)
            .overrides(System.getProperties())
            .build()
            .parse(ConfigurationSource.configSource(yaml))
}
