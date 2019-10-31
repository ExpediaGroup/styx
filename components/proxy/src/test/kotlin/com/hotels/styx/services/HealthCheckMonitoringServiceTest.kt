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

import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.routing.CaptureList
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.failingMockObject
import com.hotels.styx.routing.mockObject
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.withClue
import io.kotlintest.milliseconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

class HealthCheckMonitoringServiceTest : FeatureSpec({

    fun createdTag(tag: String) = tag.matches("created:.*".toRegex())

    feature("Lifecycle management") {
        val scheduledFuture = mockk<ScheduledFuture<Void>>(relaxed = true)

        val executor = mockk<ScheduledExecutorService> {
            every { scheduleAtFixedRate(any(), any(), any(), any()) } returns scheduledFuture
        }

        val objectStore = StyxObjectStore<RoutingObjectRecord>()
                .apply {
                    record("aaa-01", "X", setOf("aaa"), mockk(), mockk())
                    record("aaa-02", "x", setOf("aaa", "state:active"), mockk(), mockk())
                    record("aaa-03", "x", setOf("aaa", "state:inactive"), mockk(), mockk())
                    record("aaa-04", "x", setOf("aaa", "state:inactive"), mockk(), mockk())
                }

        val monitor = HealthCheckMonitoringService(
                objectStore = objectStore,
                application = "aaa",
                urlPath = "/",
                period = 100.milliseconds,
                activeThreshold = 2,
                inactiveThreshold = 2,
                executor = executor
        )

        scenario("Starts scheduled executor") {
            monitor.start().get()
            verify { executor.scheduleAtFixedRate(any(), 100, 100, MILLISECONDS) }
        }

        scenario("Changes inactive tags to active when health check is stopped") {
            monitor.stop().get()

            verify { scheduledFuture.cancel(false) }

            objectStore["aaa-01"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly("aaa")
            objectStore["aaa-02"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly("aaa", "state:active")
            objectStore["aaa-03"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly("aaa", "state:active:0")
            objectStore["aaa-04"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly("aaa", "state:active:0")
        }
    }

    feature("Discovery of monitored objects") {

        scenario("Obtains objects tagged with application name") {
            discoverMonitoredObjects("aaa", StyxObjectStore<RoutingObjectRecord>()
                    .apply {
                        record("aaa-01", "X", setOf("aaa"), mockk(), mockk())
                        record("aaa-02", "x", setOf("aaa"), mockk(), mockk())
                    }).let {
                it.size shouldBe 2
                it.map { it.first }.shouldContainAll("aaa-01", "aaa-02")
            }
        }

        scenario("Returns nothing when empty object store is created") {
            discoverMonitoredObjects("aaa", styxObjectStore {}).size shouldBe 0
        }

        scenario("Returns nothing when tagged applications are not found") {
            discoverMonitoredObjects("aaa", StyxObjectStore<RoutingObjectRecord>()
                    .apply {
                        record("bbb-01", "X", setOf("bbb"), mockk(), mockk())
                        record("ccc-02", "x", setOf("ccc", "state:disabled"), mockk(), mockk())
                    }).size shouldBe 0
        }
    }

    feature("Extracting health check state from tags") {
        objectHealthFrom("") shouldBe Optional.empty()
        objectHealthFrom("state:") shouldBe Optional.empty()
        objectHealthFrom("state:abc") shouldBe Optional.empty()
        objectHealthFrom("state:abc:0") shouldBe Optional.empty()

        objectHealthFrom("state:active:") shouldBe Optional.empty()
        objectHealthFrom("state:active:ab") shouldBe Optional.empty()
        objectHealthFrom("state:active:-1") shouldBe Optional.empty()
        objectHealthFrom("state:active") shouldBe Optional.of(ObjectActive(0))
        objectHealthFrom("state:active:2") shouldBe Optional.of(ObjectActive(2))
        objectHealthFrom("state:active:124") shouldBe Optional.of(ObjectActive(124))
        objectHealthFrom("state:active:124x") shouldBe Optional.empty()

        objectHealthFrom("state:inactive:") shouldBe Optional.empty()
        objectHealthFrom("state:inactive:ab") shouldBe Optional.empty()
        objectHealthFrom("state:inactive:-1") shouldBe Optional.empty()
        objectHealthFrom("state:inactive") shouldBe Optional.of(ObjectInactive(0))
        objectHealthFrom("state:inactive:2") shouldBe Optional.of(ObjectInactive(2))
        objectHealthFrom("state:inactive:124") shouldBe Optional.of(ObjectInactive(124))
        objectHealthFrom("state:inactive:124x") shouldBe Optional.empty()
    }

    fun StyxObjectStore<RoutingObjectRecord>.tagsOf(key: String) = this.get(key).get().tags

    fun tagClue(objectStore: StyxObjectStore<RoutingObjectRecord>, key: String) = "object '$key' is tagged with ${objectStore.tagsOf(key)}"

    feature("Health check monitoring") {
        val scheduledFuture = mockk<ScheduledFuture<Void>>(relaxed = true)

        val executor = mockk<ScheduledExecutorService> {
            every { scheduleAtFixedRate(any(), any(), any(), any()) } returns scheduledFuture
        }

        val probeRequests = mutableListOf<LiveHttpRequest>()

        val handler01 = mockObject("handler-01", CaptureList(probeRequests))
        val handler02 = mockObject("handler-02", CaptureList(probeRequests))

        val objectStore = StyxObjectStore<RoutingObjectRecord>()
                .apply {
                    record("aaa-01", "X", setOf("aaa"), mockk(), handler01)
                    record("aaa-02", "x", setOf("aaa"), mockk(), handler02)
                }

        val monitor = HealthCheckMonitoringService(objectStore, "aaa", "/healthCheck.txt", 100.milliseconds, 3, 3, executor)

        scenario("Probes discovered objects at specified URL") {
            monitor.runChecks("aaa", objectStore)

            verify(exactly = 1) { handler01.handle(any(), any()) }
            verify(exactly = 1) { handler01.handle(any(), any()) }

            probeRequests.map { it.url().path() } shouldBe (listOf("/healthCheck.txt", "/healthCheck.txt"))
        }

        scenario("... and re-tags after each probe") {
            withClue(tagClue(objectStore, "aaa-01")) {
                objectStore.get("aaa-01").get().tags shouldContain "state:inactive:1"
            }

            withClue(tagClue(objectStore, "aaa-02")) {
                objectStore.get("aaa-02").get().tags shouldContain "state:inactive:1"
            }
        }

        scenario("... marks objects active after N successful probes") {
            monitor.runChecks("aaa", objectStore)
            monitor.runChecks("aaa", objectStore)
            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-01")) {
                objectStore.get("aaa-01").get().tags shouldContain "state:active:0"
            }

            withClue(tagClue(objectStore, "aaa-02")) {
                objectStore.get("aaa-02").get().tags shouldContain "state:active:0"
            }
        }

        scenario("... failed health check increments failure count for reachable origins") {
            objectStore.apply {
                record("aaa-03", "X", setOf("aaa", "state:active"), mockk(), failingMockObject())
                record("aaa-04", "X", setOf("aaa", "state:inactive"), mockk(), failingMockObject())
            }

            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-03")) {
                objectStore.get("aaa-03").get().tags shouldContain "state:active:1"
            }
            withClue(tagClue(objectStore, "aaa-04")) {
                objectStore.get("aaa-04").get().tags shouldContain "state:inactive:0"
            }

            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-03")) {
                objectStore.get("aaa-03").get().tags shouldContain "state:active:2"
            }

            withClue(tagClue(objectStore, "aaa-04")) {
                objectStore.get("aaa-04").get().tags shouldContain "state:inactive:0"
            }
        }
    }

    feature("retagging") {
        scenario("Re-tag an active object") {
            reTag(setOf("aaa", "state:active:0"), ObjectActive(1))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                "aaa",
                                "state:active:1")
                    }
        }

        scenario("Re-tag an inactive object") {
            reTag(setOf("aaa", "state:inactive:0"), ObjectActive(1))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                "aaa",
                                "state:active:1")
                    }
        }

        scenario("Check for incomplete tag") {
            tagIsIncomplete(setOf("aaa", "state:active", "bbb")).shouldBeTrue()
            tagIsIncomplete(setOf("aaa", "state:inactive", "bbb")).shouldBeTrue()

            tagIsIncomplete(setOf("aaa", "state:active:0", "bbb")).shouldBeFalse()
            tagIsIncomplete(setOf("aaa", "state:inactive:1", "bbb")).shouldBeFalse()
        }
    }
})
