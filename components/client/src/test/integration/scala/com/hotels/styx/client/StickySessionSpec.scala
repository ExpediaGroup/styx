/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client

import com.hotels.styx.support.api.BlockingObservables._
import com.hotels.styx.api.HttpRequest.Builder
import com.hotels.styx.api.Id.id
import com.hotels.styx.api.{HttpRequest}
import com.hotels.styx.client.StyxHttpClient.newHttpClientBuilder
import com.hotels.styx.client.applications.BackendService
import com.hotels.styx.client.stickysession.StickySessionConfig
import io.netty.handler.codec.http.HttpResponseStatus._
import org.scalatest.{BeforeAndAfter, FunSuite, ShouldMatchers}

import scala.collection.JavaConverters._

class StickySessionSpec extends FunSuite with BeforeAndAfter with ShouldMatchers with OriginSupport {

  val (appOriginOne, server1) = originAndServer("app", "app-01")
  val (appOriginTwo, server2) = originAndServer("app", "app-02")

  val StickySessionEnabled = new StickySessionConfig.Builder()
    .enabled(true)
    .build()

  val StickySessionDisabled = new StickySessionConfig.Builder()
    .enabled(false)
    .build()


  before {
    server1.start
    server2.start
  }

  after {
    server1.stop
    server2.stop
  }


  test("Responds with sticky session cookie when STICKY_SESSION_ENABLED=true") {
    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .id(id("app"))
        .origins(appOriginOne, appOriginTwo)
        .stickySessionConfig(StickySessionEnabled)
        .build())
      .build

    val request: HttpRequest = Builder.get("/")
      .build

    val response = waitForResponse(client.sendRequest(request))
    response.status() should be(OK)
    response.cookie("styx_origin_app").get().toString should fullyMatch regex "styx_origin_app=app-0[12]; Max-Age=.*; Path=/; HttpOnly"
  }

  test("Responds without sticky session cookie when STICKY_SESSION_ENABLED=false") {
    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .id(id("app"))
      .origins(appOriginOne, appOriginTwo)
      .stickySessionConfig(StickySessionDisabled)
      .build()
    )
      .build

    val request: HttpRequest = Builder.get("/")
      .build

    val response = waitForResponse(client.sendRequest(request))
    response.status() should be(OK)
    response.cookies().asScala should have size (0)
  }

  test("Routes to origins indicated by sticky session cookie.") {
    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .id(id("app"))
      .origins(appOriginOne, appOriginTwo)
      .stickySessionConfig(StickySessionEnabled)
      .build()
    )
      .build

    val request: HttpRequest = Builder.get("/")
      .addCookie("styx_origin_app", "app-02")
      .build


    val response1 = waitForResponse(client.sendRequest(request))
    val response2 = waitForResponse(client.sendRequest(request))
    val response3 = waitForResponse(client.sendRequest(request))

    response1.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response2.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response3.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
  }

  test("Routes to origins indicated by sticky session cookie when other cookies are provided.") {
    val client: StyxHttpClient = newHttpClientBuilder(new BackendService.Builder()
      .id(id("app"))
      .origins(appOriginOne, appOriginTwo)
      .stickySessionConfig(StickySessionEnabled)
      .build()
    )
      .build

    val request: HttpRequest = Builder.get("/")
      .addCookie("other_cookie1", "foo")
      .addCookie("styx_origin_app", "app-02")
      .addCookie("other_cookie2", "bar")
      .build()


    val response1 = waitForResponse(client.sendRequest(request))
    val response2 = waitForResponse(client.sendRequest(request))
    val response3 = waitForResponse(client.sendRequest(request))

    response1.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response2.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
    response3.header("Stub-Origin-Info").get() should be(s"APP-localhost:${server2.port}")
  }

  test("Routes to new origin when the origin indicated by sticky session cookie does not exist.") {
    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .id(id("app"))
        .origins(appOriginOne, appOriginTwo)
        .stickySessionConfig(StickySessionEnabled)
        .build())
      .build

    val request: HttpRequest = Builder.get("/")
      .addCookie("styx_origin_app", "h3")
      .build

    val response = waitForResponse(client.sendRequest(request))

    response.status() should be(OK)
    response.cookies().asScala should have size (1)
    response.cookie("styx_origin_app").get().toString should fullyMatch regex "styx_origin_app=app-0[12]; Max-Age=.*; Path=/; HttpOnly"
  }

  test("Routes to new origin when the origin indicated by sticky session cookie is no longer available.") {
    server1.stop()
    server2.stop()

    server2.start()

    val client: StyxHttpClient = newHttpClientBuilder(
      new BackendService.Builder()
        .id(id("app"))
        .origins(appOriginOne, appOriginTwo)
        .stickySessionConfig(StickySessionEnabled)
        .build())
      .build

    val request: HttpRequest = Builder.get("/")
      .addCookie("styx_origin_app", "app-02")
      .build

    val response = waitForResponse(client.sendRequest(request))

    response.status() should be(OK)
    response.cookies() should have size (1)
    response.cookie("styx_origin_app").get().toString should fullyMatch regex "styx_origin_app=app-02; Max-Age=.*; Path=/; HttpOnly"
  }

  private def healthCheckIntervalFor(appId: String) = 1000

}
