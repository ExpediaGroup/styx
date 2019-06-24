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
package com.hotels.styx.routing

import com.hotels.styx.api.Eventual
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.LiveHttpResponse
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.toMono
import reactor.test.publisher.TestPublisher

class RoutingObjectAdapterTest : FeatureSpec({
    val request = get("/").build()

    feature("Maintains load balancing metrics") {

        val publisher1 = TestPublisher.create<LiveHttpResponse>()
        val publisher2 = TestPublisher.create<LiveHttpResponse>()
        val publidher3 = TestPublisher.create<LiveHttpResponse>()

        val delegate = mockk<RoutingObject> {
            every { handle(any(), any()) }
                    .returnsMany(listOf(Eventual(publisher1), Eventual(publisher2), Eventual(publidher3)))
        }

        RoutingObjectAdapter(delegate)
                .let {

                    it.handle(request)
                            .toMono()
                            .subscribe()

                    it.metric().ongoingConnections().shouldBe(1)

                    it.handle(request)
                            .toMono()
                            .subscribe()

                    it.metric().ongoingConnections().shouldBe(2)

                    publisher1.complete()

                    it.metric().ongoingConnections().shouldBe(1)

                    it.handle(request)
                            .toMono()
                            .subscribe()
                    it.metric().ongoingConnections().shouldBe(2)

                    publisher2.complete()
                    publidher3.complete()
                    it.metric().ongoingConnections().shouldBe(0)
                }
    }

    feature("Calls close on delegate") {
        val delegate = mockk<RoutingObject>(relaxed = true)

        RoutingObjectAdapter(delegate).stop()

        verify { delegate.stop() }
    }

})

