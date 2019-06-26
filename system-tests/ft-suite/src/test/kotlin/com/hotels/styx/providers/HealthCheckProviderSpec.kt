package com.hotels.styx.providers

import com.hotels.styx.StyxServer
import com.hotels.styx.api.HttpHeaderNames.HOST
import com.hotels.styx.api.HttpRequest.get
import com.hotels.styx.api.HttpResponseStatus.CREATED
import com.hotels.styx.api.HttpResponseStatus.OK
import com.hotels.styx.client.StyxHttpClient
import com.hotels.styx.routing.ConditionRoutingSpec
import com.hotels.styx.support.ResourcePaths
import com.hotels.styx.support.StyxServerProvider
import com.hotels.styx.support.newRoutingObject
import com.hotels.styx.support.proxyHttpHostHeader
import com.hotels.styx.support.routingObject
import com.hotels.styx.support.wait
import io.kotlintest.Description
import io.kotlintest.Spec
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.nio.charset.StandardCharsets.UTF_8


class HealthCheckProviderSpec : StringSpec() {

    val originsOk = ResourcePaths.fixturesHome(ConditionRoutingSpec::class.java, "/conf/origins/origins-correct.yml")


    val styxServer = StyxServerProvider("""
            proxy:
              connectors:
                http:
                  port: 0

            admin:
              connectors:
                http:
                  port: 0

            services:
              factories:
                backendServiceRegistry:
                  class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                  config: {originsFile: "$originsOk"}

            providers:
              myMonitor:
                type: HealthCheckMonitor
                config:
                  objects: aaa
                  path: /healthCheck
                  timeoutMillis: 250
                  intervalMillis: 500
                  healthyThreshold: 3
                  unhealthyThreshold: 2

            httpPipeline: 
                type: LoadBalancingGroup
                config:
                  origins: aaa
            """.trimIndent())

    val httpClient = StyxHttpClient.Builder().build()

    fun hostProxy(tag: String, remote: StyxServerProvider) = """
        type: HostProxy
        tags:
          - $tag
        config:
          host: ${remote().proxyHttpHostHeader()}
        """.trimIndent()

    init {
        "Allows traffic to live origins" {
            styxServer.restart()

            styxServer().newRoutingObject("aaa-01", hostProxy("aaa", testServer01)).shouldBe(CREATED)
            styxServer().newRoutingObject("aaa-02", hostProxy("aaa", testServer02)).shouldBe(CREATED)
            styxServer().newRoutingObject("aaa-03", hostProxy("aaa", testServer03)).shouldBe(CREATED)

            List(100) { 1 }
                    .groupBy {
                        httpClient
                                .send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
                                .wait(debug = false)!!
                                .run {
                                    bodyAs(UTF_8) shouldMatch "origin-[123]"
                                    status() shouldBe(OK)
                                    bodyAs(UTF_8)
                                }
                    }.mapValues { (_, value) -> value.sum() }
                    .let {
                        println("results before: " + it)
                    }

            testServer02.stop()

            Thread.sleep(2000)

            styxServer().routingObject("aaa-02").let {
                println("object-02: " + it.get())
                it.get().shouldContain("state:")
            }


//            List(100) { 1 }
//                    .groupBy {
//                        httpClient
//                                .send(get("/").header(HOST, styxServer().proxyHttpHostHeader()).build())
//                                .wait(debug = false)!!
//                                .run {
//                                    bodyAs(UTF_8) shouldMatch "origin-[123]"
//                                    status() shouldBe(OK)
//                                    bodyAs(UTF_8)
//                                }
//                    }.mapValues { (_, value) -> value.sum() }
//                    .let {
//                        println("results after: " + it)
//                    }
        }
    }


    val testServerConfig = """
            proxy:
              connectors:
                http:
                  port: 0

            admin:
              connectors:
                http:
                  port: 0

            services:
              factories:
                backendServiceRegistry:
                  class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry${'$'}Factory"
                  config: {originsFile: "$originsOk"}

            httpPipeline: 
              type: PathPrefixRouter
              config:
                routes:
                    - prefix: /
                      destination:
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "origin-%d"
                    - prefix: /healthCheck
                      destination:
                        type: StaticResponseHandler
                        config:
                          status: 200
                          content: "health check endpoint" 
            """.trimIndent()

    val testServer01 = StyxServerProvider(testServerConfig.format(1))
    val testServer02 = StyxServerProvider(testServerConfig.format(2))
    val testServer03 = StyxServerProvider(testServerConfig.format(3))

    override fun beforeSpec(spec: Spec) {
        testServer01.restart()
        testServer02.restart()
        testServer03.restart()
        styxServer.restart()
    }

    override fun afterDiscovery(descriptions: List<Description>) {
        styxServer.stop()
        testServer01.stop()
        testServer02.stop()
        testServer03.stop()
    }


}
