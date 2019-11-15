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

import com.hotels.styx.STATE_CLOSED
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.lbGroupTag
import com.hotels.styx.routing.CaptureList
import com.hotels.styx.routing.RoutingObjectRecord
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.routing.failingMockObject
import com.hotels.styx.routing.mockObject
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.withClue
import io.kotlintest.milliseconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
                    record("aaa-01", "X", setOf(lbGroupTag("aaa")), mockk(), mockk())
                    record("aaa-02", "x", setOf(lbGroupTag("aaa"), "state=active"), mockk(), mockk())
                    record("aaa-03", "x", setOf(lbGroupTag("aaa"), "state=active", "health=fail:1"), mockk(), mockk())
                    record("aaa-04", "x", setOf(lbGroupTag("aaa"), "state=unreachable"), mockk(), mockk())
                    record("aaa-05", "x", setOf(lbGroupTag("aaa"), "state=unreachable", "health=success:1"), mockk(), mockk())
                    record("aaa-06", "x", setOf(lbGroupTag("aaa"), "state=closed"), mockk(), mockk())
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

        scenario("Changes unreachable tag to active, and removes any health tags, when health check is stopped") {
            monitor.stop().get()

            verify { scheduledFuture.cancel(false) }

            objectStore["aaa-01"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"))
            objectStore["aaa-02"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"), "state=active")
            objectStore["aaa-03"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"), "state=active")
            objectStore["aaa-04"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"), "state=active")
            objectStore["aaa-05"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"), "state=active")
            objectStore["aaa-06"].get().tags.filterNot{ createdTag(it) }.shouldContainExactly(lbGroupTag("aaa"), "state=closed")
        }
    }

    feature("Discovery of monitored objects") {

        scenario("Obtains objects tagged with application name") {
            discoverMonitoredObjects("aaa", StyxObjectStore<RoutingObjectRecord>()
                    .apply {
                        record("aaa-01", "X", setOf(lbGroupTag("aaa")), mockk(), mockk())
                        record("aaa-02", "x", setOf(lbGroupTag("aaa")), mockk(), mockk())
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
                        record("bbb-01", "X", setOf(lbGroupTag("bbb")), mockk(), mockk())
                        record("ccc-02", "x", setOf(lbGroupTag("ccc"), "state=disabled"), mockk(), mockk())
                    }).size shouldBe 0
        }
    }

    feature("Extracting health check state from tags") {
        objectHealthFrom(null, null) shouldBe ObjectUnreachable(0)
        objectHealthFrom("", null) shouldBe ObjectOther("")
        objectHealthFrom("abc", null) shouldBe ObjectOther("abc")
        objectHealthFrom("abc", "fail" to 0) shouldBe ObjectOther("abc")

        objectHealthFrom("closed", null) shouldBe ObjectOther(STATE_CLOSED)
        objectHealthFrom("closed", "fail" to 3) shouldBe ObjectOther(STATE_CLOSED)
        objectHealthFrom("closed", "success" to 7) shouldBe ObjectOther(STATE_CLOSED)

        objectHealthFrom("active", "fail" to -1) shouldBe ObjectActive(0)
        objectHealthFrom("active", null) shouldBe ObjectActive(0)
        objectHealthFrom("active", "fail" to 2) shouldBe ObjectActive(2)
        objectHealthFrom("active", "fail" to 124) shouldBe ObjectActive(124)

        objectHealthFrom("unreachable", "success" to -1) shouldBe ObjectUnreachable(0)
        objectHealthFrom("unreachable", null) shouldBe ObjectUnreachable(0)
        objectHealthFrom("unreachable", "success" to 2) shouldBe ObjectUnreachable(2)
        objectHealthFrom("unreachable", "success" to 124) shouldBe ObjectUnreachable(124)
    }

    fun StyxObjectStore<RoutingObjectRecord>.tagsOf(key: String) = this.get(key).get().tags

    fun tagClue(objectStore: StyxObjectStore<RoutingObjectRecord>, key: String) = "object '$key' is tagged with ${objectStore.tagsOf(key)}"

    fun isStateOrHealthTag(tag: String) = tag.matches("state=.*".toRegex()) || tag.matches("health=.*".toRegex())

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
                    record("aaa-01", "X", setOf(lbGroupTag("aaa")), mockk(), handler01)
                    record("aaa-02", "x", setOf(lbGroupTag("aaa")), mockk(), handler02)
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
                objectStore.get("aaa-01").get().tags.shouldContainAll("state=unreachable", "health=success:1")
            }

            withClue(tagClue(objectStore, "aaa-02")) {
                objectStore.get("aaa-02").get().tags.shouldContainAll("state=unreachable", "health=success:1")
            }
        }

        scenario("... marks objects active after N successful probes") {
            monitor.runChecks("aaa", objectStore)
            monitor.runChecks("aaa", objectStore)
            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-01")) {
                objectStore.get("aaa-01").get().tags
                        .filter { isStateOrHealthTag(it) }
                        .shouldContainExactly("state=active")
            }

            withClue(tagClue(objectStore, "aaa-02")) {
                objectStore.get("aaa-02").get().tags
                        .filter { isStateOrHealthTag(it) }
                        .shouldContainExactly("state=active")
            }
        }

        scenario("... failed health check increments failure count for reachable origins") {
            objectStore.apply {
                record("aaa-03", "X", setOf(lbGroupTag("aaa"), "state=active"), mockk(), failingMockObject())
                record("aaa-04", "X", setOf(lbGroupTag("aaa"), "state=unreachable"), mockk(), failingMockObject())
            }

            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-03")) {
                objectStore.get("aaa-03").get().tags.shouldContainAll("state=active", "health=fail:1")
            }
            withClue(tagClue(objectStore, "aaa-04")) {
                objectStore.get("aaa-04").get().tags
                        .filter { isStateOrHealthTag(it) }
                        .shouldContainExactly("state=unreachable")
            }

            monitor.runChecks("aaa", objectStore)

            withClue(tagClue(objectStore, "aaa-03")) {
                objectStore.get("aaa-03").get().tags.shouldContainAll("state=active", "health=fail:2")
            }

            withClue(tagClue(objectStore, "aaa-04")) {
                objectStore.get("aaa-04").get().tags
                        .filter { isStateOrHealthTag(it) }
                        .shouldContainExactly("state=unreachable")
            }
        }
    }

    feature("retagging") {
        scenario("Re-tag an active object") {
            reTag(setOf(lbGroupTag("aaa"), "state=active", "health=fail:0"), ObjectActive(1))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                lbGroupTag("aaa"),
                                "state=active",
                                "health=fail:1")
                    }
        }

        scenario("Re-tag an unreachable object") {
            reTag(setOf(lbGroupTag("aaa"), "state=unreachable", "health=success:0"), ObjectActive(1))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                lbGroupTag("aaa"),
                                "state=active",
                                "health=fail:1")
                    }
        }

        scenario("Re-tag a failed active object as unreachable") {
            reTag(setOf(lbGroupTag("aaa"), "state=active", "health=fail:1"), ObjectUnreachable(0))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                lbGroupTag("aaa"),
                                "state=unreachable")
                    }
        }

        scenario("Re-tag a successful unreachable object as active") {
            reTag(setOf(lbGroupTag("aaa"), "state=unreachable", "health=success:1"), ObjectActive(0))
                    .let {
                        println("new tags: " + it)

                        it.shouldContainExactly(
                                lbGroupTag("aaa"),
                                "state=active")
                    }
        }
    }
})
