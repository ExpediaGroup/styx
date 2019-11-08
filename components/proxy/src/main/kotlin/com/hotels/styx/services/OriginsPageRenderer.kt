package com.hotels.styx.services

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.lbGroupTag
import com.hotels.styx.matchLbGroupTag
import com.hotels.styx.routing.RoutingObjectRecord
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
import kotlinx.html.h2
import kotlinx.html.h5
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.i
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.title
import kotlinx.html.tr

internal class OriginsPageRenderer(val provider: String, val routeDatabase: StyxObjectStore<RoutingObjectRecord>) {

    fun render() = document {
        append.html {
            head {
                meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
                title("Styx admin interface")
                link { rel = "stylesheet"; type = "text/css"; href = "https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/css/materialize.min.css" }
                link { rel = "stylesheet"; href = "https://fonts.googleapis.com/icon?family=Material+Icons" }
                script { src = "https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/css/materialize.min.css" }
            }
            body {
                div {
                    classes = setOf("container")
                    h2 {
                        classes = setOf("title grey-text text-darken-3 center-align")
                        +"Application Dashboard: $provider"
                    }
                    p {
                        classes = setOf("grey-text text-darken-3 center-align")
                        +"(application router compatibility mode)"
                    }

                    val objects = routeDatabase.entrySet()
                            .map { it.key to it.value }
                            .filter { (_, record) -> record.tags.contains(sourceTag(provider))}
                            .toList()

                    val appNames = applications(objects).sorted()

                    val router = pathPrefixRouter(objects)

                    div {
                        table {
                            tbody {
                                appNames.forEach {
                                    val prefix = pathPrefixMap(router).getOrDefault(it, "")
                                    val origins = originRecords(objects, it)

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
                h5 {
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
            originNameField(originName)
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

            i {
                classes = setOf("valign-wrapper left small material-icons pulse", textColor)

                +when (originState(tags)) {
                    "active" -> "cloud_done"
                    "inactive" -> "cloud_off"
                    else -> "cloud_done"
                }
            }
        }
    }

    fun TR.originSecurityField(config: JsonNode) {
        td {
            attributes.put("width", "5%")

            i {
                classes = setOf("valign-wrapper left small material-icons blue-text")
                +if (isSecureHost(config)) "lock" else "lock_open"
            }
        }
    }

    fun TR.originNameField(name: String) {
        td {
            span {
                classes = setOf("valign-wrapper title grey-text text-darken-3")
                +name
            }
        }
    }

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

    private fun String?.isNotNull() = this != null

    private fun originRecords(objects: List<Pair<String, RoutingObjectRecord>>, appName: String) = objects
            .filter { (_, record) -> record.type == "HostProxy" }
            .filter { (_, record) -> record.tags.contains(lbGroupTag(appName)) }
            .toList()

    private fun applications(objects: List<Pair<String, RoutingObjectRecord>>) = objects
            .filter { it.second.type == "LoadBalancingGroup" }
            .map { it.first }

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
            .filter { matchLbGroupTag(it) == null }

    private fun isSecureHost(config: JsonNode) = config.get("tlsSettings") != null
}
