/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.demo

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.net.HttpURLConnection
import java.net.URL

class DemoOriginTest : FeatureSpec({

    feature("Normal response") {
        val server = launchDemoOrigin(0)
        val port = server.port()

        val (status, body) = call(port, "/")

        status shouldBe 200
        body shouldBe "Demo origin response. Status = 200 OK"
    }

    feature("Custom status") {
        val port = launchDemoOrigin(0).port()

        val (status, body) = call(port, "/?status=201")

        status shouldBe 201
        body shouldBe "Demo origin response. Status = 201 Created"
    }

    feature("Custom body") {
        val port = launchDemoOrigin(0).port()

        val (status, body) = call(port, "/?body=foo")

        status shouldBe 200
        body shouldBe "foo"
    }

    feature("Custom status and body") {
        val port = launchDemoOrigin(0).port()

        val (status, body) = call(port, "/?status=201&body=foo")

        status shouldBe 201
        body shouldBe "foo"
    }
})

private fun call(
    port: Int,
    path: String = "/",
): Pair<Int, String> {
    val path0 = if (path.startsWith("/")) path else "/$path"

    val url = URL("http://localhost:$port$path0")

    val connection = url.openConnection().apply { connect() } as HttpURLConnection

    return Pair(
        connection.responseCode,
        connection.inputStream.use {
            it.bufferedReader().readText()
        },
    )
}
