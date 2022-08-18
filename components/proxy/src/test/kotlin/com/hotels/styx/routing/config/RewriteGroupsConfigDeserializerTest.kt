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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.hotels.styx.api.extension.service.RewriteConfig
import com.hotels.styx.support.ResourcePaths
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class RewriteGroupsConfigDeserializerTest : StringSpec() {

    companion object {
        private val REWRITES_FILE = ResourcePaths.fixturesHome() + "conf/rewrites.yml"
        private val OBJECT_MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
    }

    init {
        "deserialize when rewrite list is passed directly to styx config" {
            val yaml = """
                someGroup:
                    - urlPattern: "/foo/(.*)"
                      replacement: "/bar/$1"
                    - urlPattern: "/ping/(.*)"
                      replacement: "/pong/$1"
                anotherGroup:
                    - urlPattern: "/hey/(.*)"
                      replacement: "/hi/$1"
            """
            val rewriteGroupsConfig = OBJECT_MAPPER.readValue(yaml, RewriteGroupsConfig::class.java)
            rewriteGroupsConfig.size shouldBe 2
            rewriteGroupsConfig["someGroup"] shouldBe listOf(
                RewriteConfig("/foo/(.*)", "/bar/$1"),
                RewriteConfig("/ping/(.*)", "/pong/$1")
            )
            rewriteGroupsConfig["anotherGroup"] shouldBe listOf(
                RewriteConfig("/hey/(.*)", "/hi/$1")
            )
        }

        "deserialize when rewrite config file path is passed" {
            val yaml = """
                configFile: "$REWRITES_FILE"
            """
            val rewriteGroupsConfig = OBJECT_MAPPER.readValue(yaml, RewriteGroupsConfig::class.java)
            rewriteGroupsConfig.size shouldBe 2
            rewriteGroupsConfig["someGroup"] shouldBe listOf(
                RewriteConfig("/foo/(.*)", "/bar/$1"),
                RewriteConfig("/ping/(.*)", "/pong/$1")
            )
            rewriteGroupsConfig["anotherGroup"] shouldBe listOf(
                RewriteConfig("/hey/(.*)", "/hi/$1")
            )
        }
    }
}
