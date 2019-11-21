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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.MediaType.JSON_UTF_8
import com.hotels.styx.*
import com.hotels.styx.api.*
import com.hotels.styx.api.HttpMethod.*
import com.hotels.styx.api.HttpResponseStatus.*
import com.hotels.styx.infrastructure.configuration.json.mixins.ErrorResponseMixin
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import java.lang.RuntimeException
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Handles URLs like: /admin/providers/<provider-name>/<app-id>/<origin-id>/state
 * The parent will pass anything like /admin/providers/provider-name/xxx... to this handler
 * This handler is configured with the base path /admin/providers/provider-name so that it can extract the remainder
 * One handler for all origins
 */
internal class OriginsAdminHandler(
        private val provider: String,
        private val basePath: String,
        private val routeDb: StyxObjectStore<RoutingObjectRecord>) : HttpHandler {

    companion object {
        private val ORIGIN_REGEX = "^/([^/]+)/([^/]+)/state$".toRegex() // /app-id/origin-id/state
        private val MAPPER = ObjectMapper().addMixIn(
                ErrorResponse::class.java, ErrorResponseMixin::class.java
        )
    }

    override fun handle(request: LiveHttpRequest, context: HttpInterceptor.Context?): Eventual<LiveHttpResponse> {
        if (!request.path().startsWith(basePath)) {
            return Eventual.of(errorResponse(BAD_REQUEST, "Incorrect URL format"))
        }
        val remainingPath = request.path().substring(basePath.length)
        val requestData = ORIGIN_REGEX.matchEntire(remainingPath)?.groupValues
                ?: return Eventual.of(errorResponse(BAD_REQUEST, "Incorrect URL format for $remainingPath"))

        return handle(objectIdFrom(requestData[1], requestData[2]), request.method(), request)
    }

    private fun handle(objectId: String, method: HttpMethod, request: LiveHttpRequest) =
            when (method) {
                GET -> getState(objectId)
                PUT -> putState(objectId, request)
                else -> Eventual.of(errorResponse(METHOD_NOT_ALLOWED, "Should be GET or PUT"))
            }

    private fun getState(objectId: String) : Eventual<LiveHttpResponse> {
        val origin = findOrigin(objectId)
        return Eventual.of(if (origin != null) {
            textValueResponse(stateTagValue(origin.tags) ?: "")
        } else {
            errorResponse(NOT_FOUND, "No origin found for ID $objectId")
        })
    }

    private fun putState(objectId: String, request: LiveHttpRequest) : Eventual<LiveHttpResponse> {
        return request.aggregate(100)
                .map { it.bodyAs(UTF_8) }
                .map { MAPPER.readValue(it, String::class.java) }
                .map {
                    try {
                        when (it) {
                            STATE_ACTIVE -> activate(objectId)
                            STATE_CLOSED -> close(objectId)
                            else -> errorResponse(BAD_REQUEST, "Unrecognized target state: $it")
                        }
                    } catch (e: HttpStatusException) {
                        errorResponse(e.status, e.message)
                    }
                }
                .onError { t ->
                    Eventual.of(errorResponse(BAD_REQUEST, "Error handling state change request: ${t.localizedMessage}"))
                }
    }

    private fun activate(objectId: String) : LiveHttpResponse {
        var newState = ""
        routeDb.compute(objectId) { origin ->
            if (!isValidOrigin(origin)) {
                throw HttpStatusException(NOT_FOUND, "No origin found for ID $objectId")
            }
            newState = when(stateTagValue(origin!!.tags)) {
                STATE_CLOSED, STATE_ACTIVE -> STATE_ACTIVE
                STATE_UNREACHABLE -> STATE_UNREACHABLE
                else -> STATE_ACTIVE
            }
            updateStateTag(origin, newState)
        }
        return HttpResponse.response(OK)
                .body(MAPPER.writeValueAsString(newState), UTF_8)
                .build().stream()
    }

    private fun close(objectId: String) : LiveHttpResponse {
        routeDb.compute(objectId) { origin ->
            if (!isValidOrigin(origin)) {
                throw HttpStatusException(NOT_FOUND, "No origin found for ID $objectId")
            }
            updateStateTag(origin!!, STATE_CLOSED, true)
        }
        return HttpResponse.response(OK)
                .body(MAPPER.writeValueAsString(STATE_CLOSED), UTF_8)
                .build().stream()
    }

    private fun updateStateTag(origin: RoutingObjectRecord, newValue: String, clearHealthcheck: Boolean = false) : RoutingObjectRecord {
        val oldTags = origin.tags
        val newTags = oldTags
                .filterNot{ clearHealthcheck && isHealthcheckTag(it) }
                .filterNot(::isStateTag)
                .plus(stateTag(newValue)).toSet()
        return if (oldTags != newTags) {
            origin.copy(tags = newTags)
        } else {
            origin
        }
    }

    private fun errorResponse(status: HttpResponseStatus, message: String) =
            HttpResponse.response(status)
                    .disableCaching()
                    .addHeader(HttpHeaderNames.CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(MAPPER.writeValueAsString(ErrorResponse(message)), UTF_8)
                    .build().stream()

    private fun textValueResponse(message: String) =
            HttpResponse.response(OK)
                    .disableCaching()
                    .addHeader(HttpHeaderNames.CONTENT_TYPE, JSON_UTF_8.toString())
                    .body(MAPPER.writeValueAsString(message), UTF_8)
                    .build().stream()

    private fun objectIdFrom(appId: String, originId: String) = "$appId.$originId"

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