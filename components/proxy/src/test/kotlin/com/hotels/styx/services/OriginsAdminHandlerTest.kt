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
import com.hotels.styx.*
import com.hotels.styx.api.ErrorResponse
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.*
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.infrastructure.configuration.json.mixins.ErrorResponseMixin
import com.hotels.styx.routing.RoutingMetadataDecorator
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.mockObject
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.mockk
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets.UTF_8

class OriginsAdminHandlerTest : FeatureSpec({

    val mapper = ObjectMapper().addMixIn(ErrorResponse::class.java, ErrorResponseMixin::class.java)

    val store = StyxObjectStore<RoutingObjectRecord>()
    val mockObject = RoutingMetadataDecorator(mockObject())
    val handler = OriginsAdminHandler("testProvider", "/base/path", store)

    store.insert("app.active", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_ACTIVE)), mockk(), mockObject))
    store.insert("app.closed", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_CLOSED)), mockk(), mockObject))
    store.insert("app.unreachable", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_UNREACHABLE)), mockk(), mockObject))
    store.insert("app.nothostproxy", RoutingObjectRecord("NotHostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_UNREACHABLE)), mockk(), mockObject))

    fun getResponse(handler: OriginsAdminHandler, request: LiveHttpRequest) =
            Mono.from(Mono.from(handler.handle(request, null)).block().aggregate(1000)).block()

    fun expectFailure(request: HttpRequest, responseStatus: HttpResponseStatus, errorMessageCheck: (String) -> Unit) {
        val response = getResponse(handler, request.stream())
        response!!.status() shouldBe responseStatus

        val errorResponse = mapper.readValue(response.bodyAs(UTF_8), ErrorResponse::class.java)
        errorMessageCheck(errorResponse.errorMessage())
    }

    feature("OriginsAdminHandler returns origin state for GET request") {

        scenario("Returns active when active") {
            val request = HttpRequest.get("http://host:7777/base/path/app/active/state").build().stream()
            val response = getResponse(handler, request)
            response!!.status() shouldBe OK

            val objectState = mapper.readValue(response.bodyAs(UTF_8), String::class.java)
            objectState shouldBe STATE_ACTIVE
        }

        scenario("Returns unreachable when unreachable") {
            val request = HttpRequest.get("http://host:7777/base/path/app/unreachable/state").build().stream()
            val response = getResponse(handler, request)
            response!!.status() shouldBe OK

            val objectState = mapper.readValue(response.bodyAs(UTF_8), String::class.java)
            objectState shouldBe STATE_UNREACHABLE
        }

        scenario("Returns closed when closed") {
            val request = HttpRequest.get("http://host:7777/base/path/app/closed/state").build().stream()
            val response = getResponse(handler, request)
            response!!.status() shouldBe OK

            val objectState = mapper.readValue(response.bodyAs(UTF_8), String::class.java)
            objectState shouldBe STATE_CLOSED
        }

        scenario("Returns BAD_REQUEST when URL is not of the correct format") {
            expectFailure(HttpRequest.get("http://host:7777/base/path/not/valid/url").build(),
                    BAD_REQUEST) { it shouldBe "Incorrect URL format for /not/valid/url" }
        }

        scenario("Returns METHOD_NOT_ALLOWED if request is not GET or PUT") {
            expectFailure(HttpRequest.post("http://host:7777/base/path/appid/originid/state").build(),
                    METHOD_NOT_ALLOWED) { it shouldBe "Should be GET or PUT" }
        }

        scenario("Returns NOT_FOUND when there is no object with the requested name") {
            expectFailure(HttpRequest.get("http://host:7777/base/path/app/missing/state").build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.missing" }
        }

        scenario("Returns NOT_FOUND if the object with the requested name is not a HostProxy") {
            expectFailure(HttpRequest.get("http://host:7777/base/path/app/nothostproxy/state").build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.nothostproxy" }
        }
    }

    feature("OriginsAdminHandler updates origin state for PUT request") {

        fun expectStateChange(initialState: String, requestedState: String, expectedState: String) {
            store.insert("app.origin", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(initialState)), mockk(), mockObject))
            store.get("app.origin").get().tags shouldContain stateTag(initialState) // verify starting conditions
            val request = HttpRequest.put("http://host:7777/base/path/app/origin/state")
                    .body(mapper.writeValueAsString(requestedState), UTF_8)
                    .build().stream()
            val response = getResponse(handler, request)
            response!!.status() shouldBe OK
            mapper.readValue(response.bodyAs(UTF_8), String::class.java) shouldBe expectedState
            store.get("app.origin").get().tags shouldContain stateTag(expectedState)
        }

        scenario("Closing an active origin results in a closed state") {
            expectStateChange(STATE_ACTIVE, STATE_CLOSED, STATE_CLOSED)
        }

        scenario("Closing an unreachable origin results in a closed state") {
            expectStateChange(STATE_UNREACHABLE, STATE_CLOSED, STATE_CLOSED)
        }

        scenario("Closing a closed origin results in a closed state") {
            expectStateChange(STATE_CLOSED, STATE_CLOSED, STATE_CLOSED)
        }

        scenario("Activating an active origin results in an active state") {
            expectStateChange(STATE_ACTIVE, STATE_ACTIVE, STATE_ACTIVE)
        }

        scenario("Activating an unreachable origin results in an unreachable state") {
            expectStateChange(STATE_UNREACHABLE, STATE_ACTIVE, STATE_UNREACHABLE)
        }

        scenario("Activating a closed origin results in an active state") {
            expectStateChange(STATE_CLOSED, STATE_ACTIVE, STATE_ACTIVE)
        }

        scenario("Returns BAD_REQUEST when URL is not of the correct format") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/not/valid/url")
                    .body(mapper.writeValueAsString(STATE_CLOSED), UTF_8)
                    .build(),
                    BAD_REQUEST) { it shouldBe "Incorrect URL format for /not/valid/url" }
        }

        scenario("Returns BAD_REQUEST when the target state is not a recognized value") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/active/state")
                    .body(mapper.writeValueAsString("invalid"), UTF_8)
                    .build(),
                    BAD_REQUEST) { it shouldBe "Unrecognized target state: invalid" }
        }

        scenario("Returns BAD_REQUEST when the target state is not present") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/active/state")
                    .build(),
                    BAD_REQUEST) { it shouldStartWith  "Error handling state change request: " }
        }

        scenario("Returns BAD_REQUEST when the target state is not valid JSON text") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/active/state")
                    .body("Not valid JSON", UTF_8)
                    .build(),
                    BAD_REQUEST) { it shouldStartWith  "Error handling state change request: " }
        }

        scenario("Returns NOT_FOUND when there is no object with the requested name") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/missing/state")
                    .body(mapper.writeValueAsString(STATE_CLOSED), UTF_8)
                    .build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.missing" }
        }

        scenario("Returns NOT_FOUND if the object with the requested name is not a HostProxy") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/nothostproxy/state")
                    .body(mapper.writeValueAsString(STATE_CLOSED), UTF_8)
                    .build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.nothostproxy" }
        }
    }
})