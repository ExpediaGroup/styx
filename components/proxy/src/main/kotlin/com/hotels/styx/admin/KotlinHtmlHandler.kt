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
package com.hotels.styx.admin

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE
import com.hotels.styx.api.HttpInterceptor
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponse
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.WebServiceHandler
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.handlers.PathPrefixRouter
import java.nio.charset.StandardCharsets.UTF_8

import kotlinx.html.*
import kotlinx.html.dom.*

internal class KotlinHtmlHandler(private val routeDatabase: StyxObjectStore<RoutingObjectRecord>) : WebServiceHandler {
    override fun handle(request: HttpRequest, context: HttpInterceptor.Context): Eventual<HttpResponse> {

        val objects = routeDatabase.entrySet()
                .map { Pair(it.key, it.value) }
                .toList()

        return Eventual.of(response(OK)
                .header(CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(renderPage(objects).serialize(), UTF_8)
                .build());
    }

    private fun renderPage(objects: List<Pair<String, RoutingObjectRecord>>) = document {
        val appNames: List<String> = objects
                .filter { it.second.type == "LoadBalancingGroup" }
                .map { it.first }

        val pathPrefixRouter = objects
                .find { (_, record) -> record.type == "PathPrefixRouter" }!!
                .second

        val pathPrefixMap = JsonNodeConfig(pathPrefixRouter.config)
                .`as`<PathPrefixRouter.PathPrefixRouterConfig>(PathPrefixRouter.PathPrefixRouterConfig::class.java)
                .routes()
                .filter { it.destination() is StyxObjectReference }
                .map {
                    val destination = it.destination() as StyxObjectReference
                    val pathPrefix = it.prefix()
                    Pair(destination.name() ?: "", pathPrefix ?: "")
                }
                .toMap()

        fun hostsFor(appName: String) = objects
                .filter { it.second.type == "HostProxy" }
                .filter { it.second.tags.contains(appName) }
                .toList()


        fun DIV.addTag(tag: String) {
            span {
                classes = setOf("badge")
                +tag
            }
        }

        fun TBODY.appTableMt(appName: String) {
            tr {
                td {
                    colSpan = "4"
                    h5 {
                        val prefix = pathPrefixMap.getOrDefault(appName, "")
                        +"$prefix -> $appName"
                    }
                }
            }

            hostsFor(appName)
                    .forEach { (originName, record) ->
                        tr {
                            val textColor = when (originState(record.tags)) {
                                "active" -> "green-text text-darken4"
                                "inactive" -> "red-text text-darken-3"
                                else -> "black-text"
                            }

                            td {
                                attributes.put("width", "5%")

                                i {
                                    classes = setOf("valign-wrapper left small material-icons pulse", textColor)

                                    +when (originState(record.tags)) {
                                        "active" -> "cloud_done"
                                        "inactive" -> "cloud_off"
                                        else -> "cloud_done"
                                    }
                                }
                            }

                            td {
                                attributes.put("width", "5%")

                                i {
                                    classes = setOf("valign-wrapper left small material-icons blue-text")
                                    +if (isSecureHost(record)) "lock" else "lock_open"
//                                    +if (isSecureHost(record)) "https" else "http"
                                }
                            }

                            td {
                                span {
                                    classes = setOf("valign-wrapper title grey-text text-darken-3")
                                    +originName
                                }
                            }

                            td {
                                div { tagsExceptStatusAndAppname(appName, record.tags).forEach { addTag(it) } }
                            }
                        }
                    }
        }

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
                        +"Application Dashboard"
                    }
                    p {
                        classes = setOf("grey-text text-darken-3 center-align")
                        +"(application router compatibility mode)"
                    }

                    div {
                        table {
                            tbody {
                                appNames.sorted()
                                        .forEach { appTableMt(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun originState(tags: Set<String>): String {
        return tags.find { it.startsWith("state:") }
                ?.let { it.split(":")[1] }
                .orEmpty()
    }

    // TODO: When checking for load balancing group membership,
    //  we should check for application's "hosts" parameter, not its name:
    private fun tagsExceptStatusAndAppname(appName: String, tags: Set<String>) = tags
            .filterNot { it.startsWith("state:active") }
            .filterNot { it.startsWith("state:inactive") }
            .filterNot { it == appName }

    private fun isSecureHost(record: RoutingObjectRecord) = record
            .config
            .get("tlsSettings") != null
}