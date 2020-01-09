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
package com.hotels.styx.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.MediaType.JSON_UTF_8
import com.hotels.styx.*
import com.hotels.styx.ErrorResponse
import com.hotels.styx.admin.handlers.UrlPatternRouter
import com.hotels.styx.admin.handlers.UrlPatternRouter.placeholders
import com.hotels.styx.api.*
import com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE
import com.hotels.styx.api.HttpResponse.response
import com.hotels.styx.api.HttpResponseStatus.*
import com.hotels.styx.common.http.handler.HttpAggregator
import com.hotels.styx.infrastructure.configuration.json.mixins.ErrorResponseMixin
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.Builtins.HEALTH_CHECK_MONITOR
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Handles URLs like: /admin/providers/<provider-name>/<app-id>/<origin-id>/state
 * The parent will pass anything like /admin/providers/provider-name/xxx... to this handler
 * This handler is configured with the base path /admin/providers/provider-name so that it can extract the remainder
 * One handler for all origins
 */
internal class OriginsAdminHandler(
        basePath: String,
        private val provider: String,
        private val routeDb: StyxObjectStore<RoutingObjectRecord>,
        private val serviceDb: StyxObjectStore<ProviderObjectRecord>) : HttpHandler {

    companion object {
        private val MAPPER = ObjectMapper().addMixIn(
                ErrorResponse::class.java, ErrorResponseMixin::class.java
        )
    }

    private val router = HttpAggregator(1000, UrlPatternRouter.Builder()
                .get("$basePath/:appid/:originid/state") { _, context ->
                    Eventual.of(getState(objectIdFrom(context)))
                }
                .put("$basePath/:appid/:originid/state") { request, context ->
                    Eventual.of(putState(objectIdFrom(context), request))
                }
                .build())

    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context): Eventual<LiveHttpResponse> = router.handle(request, context)

    private fun getState(objectId: String) : HttpResponse {
        val origin = findOrigin(objectId)
        return if (origin != null) {
            textValueResponse(stateTag.find(origin.tags) ?: "")
        } else {
            errorResponse(NOT_FOUND, "No origin found for ID $objectId")
        }
    }

    private fun putState(objectId: String, request: HttpRequest) : HttpResponse =
            try {
                val body = request.bodyAs(UTF_8)
                val value = MAPPER.readValue(body, String::class.java)
                try {
                    when (value) {
                        STATE_ACTIVE -> activate(objectId)
                        STATE_INACTIVE -> close(objectId)
                        else -> errorResponse(BAD_REQUEST, "Unrecognized target state: $value")
                    }
                } catch (e: HttpStatusException) {
                    errorResponse(e.status, e.message)
                }
            } catch (t: Throwable) {
                errorResponse(BAD_REQUEST, "Error handling state change request: ${t.localizedMessage}")
            }

    private fun activate(objectId: String) : HttpResponse {
        var newState = ""
        routeDb.compute(objectId) { origin ->
            if (!isValidOrigin(origin)) {
                throw HttpStatusException(NOT_FOUND, "No origin found for ID $objectId")
            } else { origin!! }

            val newActiveState = if (hasActiveHealthCheck(origin)) STATE_UNREACHABLE else STATE_ACTIVE
            newState = when(stateTag.find(origin.tags)) {
                STATE_INACTIVE -> newActiveState
                STATE_ACTIVE -> STATE_ACTIVE
                STATE_UNREACHABLE -> STATE_UNREACHABLE
                else -> newActiveState
            }
            updateStateTag(origin, newState)
        }
        return response(OK)
                .body(MAPPER.writeValueAsString(newState), UTF_8)
                .build()
    }

    private fun close(objectId: String) : HttpResponse {
        routeDb.compute(objectId) { origin ->
            if (!isValidOrigin(origin)) {
                throw HttpStatusException(NOT_FOUND, "No origin found for ID $objectId")
            }
            updateStateTag(origin!!, STATE_INACTIVE, true)
        }
        return response(OK)
                .body(MAPPER.writeValueAsString(STATE_INACTIVE), UTF_8)
                .build()
    }

    private fun updateStateTag(origin: RoutingObjectRecord, newValue: String, clearHealthcheck: Boolean = false) : RoutingObjectRecord {
        val oldTags = origin.tags
        val newTags = oldTags
                .filterNot{ clearHealthcheck && healthCheckTag.match(it) }
                .filterNot{ stateTag.match(it) }
                .plus(stateTag(newValue))
                .toSet()
        return if (oldTags != newTags) {
            origin.copy(tags = newTags)
        } else {
            origin
        }
    }

    private fun hasActiveHealthCheck(origin: RoutingObjectRecord) =
            lbGroupTag.find(origin.tags)?.let(::findActiveHealthCheckMonitor) != null

    private fun findActiveHealthCheckMonitor(appId: String) =
            serviceDb.entrySet().firstOrNull { (_, provider) ->
                provider.type == HEALTH_CHECK_MONITOR
                        && provider.tags.contains(targetTag(appId))
                        && (provider.styxService as HealthCheckMonitoringService).isRunning()
            }

    private fun errorResponse(status: HttpResponseStatus, message: String) =
            response(status)
                    .disableCaching()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(MAPPER.writeValueAsString(ErrorResponse(message)), UTF_8)
                    .build()

    private fun textValueResponse(message: String) =
            response(OK)
                    .disableCaching()
                    .addHeader(CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(MAPPER.writeValueAsString(message), UTF_8)
                    .build()

    private fun objectIdFrom(context: HttpInterceptor.Context) : String {
        val appId = placeholders(context)["appid"]
        val originId = placeholders(context)["originid"]
        return "$appId.$originId"
    }

    private fun findOrigin(objectId: String): RoutingObjectRecord? {
        val origin = routeDb.get(objectId).orElse(null)
        return if (isValidOrigin(origin)) {
            origin
        } else {
            null
        }
    }

    private fun isValidOrigin(origin: RoutingObjectRecord?) =
            origin?.tags?.contains(sourceTag(provider)) == true
                    && origin.type == "HostProxy"
}

class HttpStatusException(val status: HttpResponseStatus, override val message: String) : RuntimeException(message)