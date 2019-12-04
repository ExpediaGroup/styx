/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.RoutingConfigParser.toRoutingConfigNode
import com.hotels.styx.routing.config.StyxObjectDefinition
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.PathPrefixRouter
import com.hotels.styx.sourceTag
import kotlinx.html.DIV
import kotlinx.html.TBODY
import kotlinx.html.TR
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.dom.document
import kotlinx.html.dom.serialize
import kotlinx.html.h3
import kotlinx.html.h6
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.title
import kotlinx.html.tr

internal class OriginsPageRenderer(val assetsRoot: String, val provider: String, val routeDatabase: StyxObjectStore<RoutingObjectRecord>) {

    fun render() = document {
        append.html {
            head {
                meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                title("Styx admin interface")
                link { rel = "stylesheet"; type = "text/css"; href = "$assetsRoot/materialize/css/materialize.min.css" }
                script { src = "$assetsRoot/materialize/js/materialize.min.js" }
            }
            body {
                div {
                    classes = setOf("container")
                    h3 {
                        classes = setOf("title grey-text text-darken-3 left-align")
                        +"Configured Services"
                    }

                    h6 {
                        classes = setOf("title grey-text text-darken-3 left-align")
                        +"Provider: $provider"
                    }

                    div {
                        classes = setOf("divider")
                    }

                    val objects = routeDatabase.entrySet()
                            .map { it.key to it.value }
                            .filter { (_, record) -> record.tags.contains(sourceTag(provider)) }
                            .toList()

                    val appNames = applications(objects).sorted()

                    val router = pathPrefixRouter(objects)

                    div {
                        table {
                            tbody {
                                appNames.forEach {
                                    val prefix = pathPrefixMap(router).getOrDefault(it, "")
                                    val originsTag = originsTag(it, objects)
                                    val origins = originRecords(objects, originsTag)

                                    appTitle(prefix, it)
                                    origins.forEach {
                                        originRecord(it)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.serialize()

    fun TBODY.appTitle(prefix: String, appName: String) {
        tr {
            td {
                colSpan = "4"
                h6 {
                    +"$prefix -> $appName"
                }
            }
        }
    }

    fun TBODY.originRecord(origin: Pair<String, RoutingObjectRecord>) {
        val originName = origin.first
        val record = origin.second

        tr {
            originStatusField(record.tags)
            originSecurityField(record.config)
            originNameField(originName, record)
            originTagsField(record.tags)
        }
    }

    fun TR.originStatusField(tags: Set<String>) {
        td {
            val textColor = when (originState(tags)) {
                "active" -> "green-text text-darken4"
                "inactive" -> "red-text text-darken-3"
                else -> "black-text"
            }

            attributes.put("width", "5%")

            span {
                classes = setOf("badge", textColor)
                // TODO: Test `isBlank()` case:
                +if (originState(tags).isBlank()) "active" else originState(tags)
            }
        }
    }

    fun TR.originSecurityField(config: JsonNode) {
        td {
            attributes.put("width", "5%")

            span {
                classes = setOf("badge blue-text")
                +if (isSecureHost(config)) "https" else "http"
            }
        }
    }

    fun TR.originNameField(name: String, record: RoutingObjectRecord) {
        td {
            span {
                classes = setOf("valign-wrapper title grey-text text-darken-3")
                +name
            }
            span {
                classes = setOf("valign-wrapper title grey-text")
                +originHost(record.config)
            }
        }
    }

    private fun originHost(config: JsonNode) = config.get("host").textValue()

    fun TR.originTagsField(tags: Set<String>) {
        td {
            div { tagsExceptStatusAndAppname(tags).forEach { routingObjectTag(it) } }
        }
    }

    fun DIV.routingObjectTag(tag: String) {
        span {
            classes = setOf("badge")
            +tag
        }
    }

    private fun pathPrefixMap(rootRecord: RoutingObjectRecord) = JsonNodeConfig(rootRecord.config)
            .`as`<PathPrefixRouter.PathPrefixRouterConfig>(PathPrefixRouter.PathPrefixRouterConfig::class.java)
            .routes()
            .filter { it.destination() is StyxObjectReference }
            .map {
                val destination = it.destination() as StyxObjectReference
                val pathPrefix = it.prefix()
                Pair(destination.name() ?: "", pathPrefix ?: "")
            }
            .toMap()

    private fun originRecords(objects: List<Pair<String, RoutingObjectRecord>>, originsTag: String) = objects
            .filter { (_, record) -> record.type == "HostProxy" }
            .filter { (_, record) -> record.tags.contains(lbGroupTag(originsTag)) }
            .toList()

    private fun applications(objects: List<Pair<String, RoutingObjectRecord>>) = objects
            .filter { (_, record) -> isApplicationObject(record) }
            .map { it.first }

    private fun isApplicationObject(record: RoutingObjectRecord) = record.type == "LoadBalancingGroup" ||
            (record.type == "InterceptorPipeline" && embeddedLoadBalancingGroup(record))

    private fun embeddedLoadBalancingGroup(record: RoutingObjectRecord) = if (record.type == "InterceptorPipeline") {
        JsonNodeConfig(record.config)
                .get("handler", JsonNode::class.java)
                .get()
                .let { toRoutingConfigNode(it) }
                .let { it is StyxObjectDefinition && it.type() == "LoadBalancingGroup" }
    } else {
        false
    }

    private fun originsTag(appName: String, objects: List<Pair<String, RoutingObjectRecord>>): String = objects
            .find { (name, _) -> name == appName }
            .let {
                checkNotNull(it) { "Application '$appName' is not found in routing database." }

                val (_, record) = it

                if (record.type == "LoadBalancingGroup") {
                    record.config.get("origins").textValue()
                } else {
                    JsonNodeConfig(record.config)
                            .get("handler", JsonNode::class.java)
                            .get()
                            .let { toRoutingConfigNode(it) }
                            .let {
                                check(it is StyxObjectDefinition) { "Object is not an embedded LoadBalancingGroup" }
                                it.config().get("origins").textValue()
                            }
                }
            }

    private fun pathPrefixRouter(objects: List<Pair<String, RoutingObjectRecord>>) = objects
            .find { (_, record) -> record.type == "PathPrefixRouter" }!!
            .second

    private fun originState(tags: Set<String>) = tags
            .find { it.startsWith("state:") }
            ?.let { it.split(":")[1] }
            .orEmpty()

    private fun tagsExceptStatusAndAppname(tags: Set<String>) = tags
            .filterNot { it.startsWith("state:active") }
            .filterNot { it.startsWith("state:inactive") }
            .filter { lbGroupTag.valueOf(it) == null }

    private fun isSecureHost(config: JsonNode) = config.get("tlsSettings") != null
}
