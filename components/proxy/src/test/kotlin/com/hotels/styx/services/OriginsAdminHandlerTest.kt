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
import com.hotels.styx.ErrorResponse
import com.hotels.styx.HEALTHCHECK_FAILING
import com.hotels.styx.STATE_ACTIVE
import com.hotels.styx.STATE_INACTIVE
import com.hotels.styx.STATE_UNREACHABLE
import com.hotels.styx.api.HttpRequest
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST
import com.hotels.styx.api.HttpResponseStatus.NOT_FOUND
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.extension.service.spi.StyxService
import com.hotels.styx.healthCheckTag
import com.hotels.styx.infrastructure.configuration.json.mixins.ErrorResponseMixin
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.RoutingMetadataDecorator
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.config.Builtins.HEALTH_CHECK_MONITOR
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.ProviderObjectRecord
import com.hotels.styx.StyxObjectRecord
import com.hotels.styx.mockObject
import com.hotels.styx.requestContext
import com.hotels.styx.sourceTag
import com.hotels.styx.stateTag
import com.hotels.styx.targetTag
import com.hotels.styx.wait
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import java.nio.charset.StandardCharsets.UTF_8

class OriginsAdminHandlerTest : FeatureSpec({

    val mapper = ObjectMapper().addMixIn(ErrorResponse::class.java, ErrorResponseMixin::class.java)

    val store = StyxObjectStore<RoutingObjectRecord>()
    val serviceDb = StyxObjectStore<StyxObjectRecord<StyxService>>()
    val mockObject = RoutingMetadataDecorator(mockObject())
    val handler = OriginsAdminHandler("/base/path", "testProvider", store, serviceDb)

    fun mockHealthCheck(running: Boolean) =
            mockk<HealthCheckMonitoringService>() {
                every { isRunning() } returns running
            }

    store.insert("app.active", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_ACTIVE)), mockk(), mockObject))
    store.insert("app.closed", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_INACTIVE)), mockk(), mockObject))
    store.insert("app.unreachable", RoutingObjectRecord("HostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_UNREACHABLE)), mockk(), mockObject))
    store.insert("app.nothostproxy", RoutingObjectRecord("NotHostProxy", setOf(sourceTag("testProvider"), stateTag(STATE_UNREACHABLE)), mockk(), mockObject))

    serviceDb.insert("hc.running", ProviderObjectRecord(HEALTH_CHECK_MONITOR, setOf(targetTag("app.origin.hc.running")), mockk(), mockHealthCheck(true)))
    serviceDb.insert("hc.stopped", ProviderObjectRecord(HEALTH_CHECK_MONITOR, setOf(targetTag("app.origin.hc.stopped")), mockk(), mockHealthCheck(false)))

    fun getResponse(handler: OriginsAdminHandler, request: LiveHttpRequest) = handler
            .handle(request, requestContext())
            .wait()

    fun expectFailure(request: HttpRequest, responseStatus: HttpResponseStatus, errorMessageCheck: (String?) -> Unit) {
        val response = getResponse(handler, request.stream())
        response!!.status() shouldBe responseStatus

        val responseBody = response.bodyAs(UTF_8)
        val errorResponse = if (responseBody.isNotEmpty()) { mapper.readValue(responseBody, ErrorResponse::class.java) } else { null }
        val errorMessage = errorResponse?.errorMessage
        errorMessageCheck(errorMessage)
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
            objectState shouldBe STATE_INACTIVE
        }

        scenario("Returns NOT_FOUND when URL is not of the correct format") {
            expectFailure(HttpRequest.get("http://host:7777/base/path/not/valid/url").build(),
                    NOT_FOUND) { it shouldBe null }
        }

        scenario("Returns NOT_FOUND if request is not GET or PUT") {
            expectFailure(HttpRequest.post("http://host:7777/base/path/appid/originid/state").build(),
                    NOT_FOUND) { it shouldBe null }
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

        fun expectStateChange(initialState: String, requestedState: String, expectedState: String, expectHealthTagCleared: Boolean, appId: String = "testGroup") {
            store.insert("app.origin", RoutingObjectRecord("HostProxy",
                    setOf(sourceTag("testProvider"),
                            lbGroupTag(appId),
                            stateTag(initialState),
                            healthCheckTag(Pair(HEALTHCHECK_FAILING, 2))!!), mockk(), mockObject))

            val request = HttpRequest.put("http://host:7777/base/path/app/origin/state")
                    .body(mapper.writeValueAsString(requestedState), UTF_8)
                    .build().stream()
            val response = getResponse(handler, request)

            response!!.status() shouldBe OK
            mapper.readValue(response.bodyAs(UTF_8), String::class.java) shouldBe expectedState
            val tags = store.get("app.origin").get().tags
            tags shouldContain stateTag(expectedState)
            if (expectHealthTagCleared) {
                healthCheckTag.find(tags) shouldBe null
            } else {
                healthCheckTag.find(tags) shouldBe Pair(HEALTHCHECK_FAILING, 2)
            }
        }

        scenario("Disabling an active origin results in an inactive state") {
            expectStateChange(STATE_ACTIVE, STATE_INACTIVE, STATE_INACTIVE, true)
        }

        scenario("Disabling an unreachable origin results in an inactive state") {
            expectStateChange(STATE_UNREACHABLE, STATE_INACTIVE, STATE_INACTIVE, true)
        }

        scenario("Disabling an inactive origin results in an inactive state") {
            expectStateChange(STATE_INACTIVE, STATE_INACTIVE, STATE_INACTIVE, true)
        }

        scenario("Activating an active origin results in an active state") {
            expectStateChange(STATE_ACTIVE, STATE_ACTIVE, STATE_ACTIVE, false)
        }

        scenario("Activating an unreachable origin results in an unreachable state") {
            expectStateChange(STATE_UNREACHABLE, STATE_ACTIVE, STATE_UNREACHABLE, false)
        }

        scenario("Activating an unhealthchecked, inactive origin results in an active state") {
            expectStateChange(STATE_INACTIVE, STATE_ACTIVE, STATE_ACTIVE, false)
        }

        scenario("Activating a healthchecked, inactive origin results in an unreachable state") {
            expectStateChange(STATE_INACTIVE, STATE_ACTIVE, STATE_UNREACHABLE, false, "app.origin.hc.running")
        }

        scenario("Activating a healthchecked (but the healthchecker is stopped), inactive origin results in an unreachable state") {
            expectStateChange(STATE_INACTIVE, STATE_ACTIVE, STATE_ACTIVE, false, "app.origin.hc.stopped")
        }

        scenario("Returns NOT_FOUND when URL is not of the correct format") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/not/valid/url")
                    .body(mapper.writeValueAsString(STATE_INACTIVE), UTF_8)
                    .build(),
                    NOT_FOUND) { it shouldBe null }
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
                    .body(mapper.writeValueAsString(STATE_INACTIVE), UTF_8)
                    .build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.missing" }
        }

        scenario("Returns NOT_FOUND if the object with the requested name is not a HostProxy") {
            expectFailure(HttpRequest.put("http://host:7777/base/path/app/nothostproxy/state")
                    .body(mapper.writeValueAsString(STATE_INACTIVE), UTF_8)
                    .build(),
                    NOT_FOUND) { it shouldBe "No origin found for ID app.nothostproxy" }
        }
    }
})