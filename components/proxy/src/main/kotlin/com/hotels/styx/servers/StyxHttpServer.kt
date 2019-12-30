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
package com.hotels.styx.servers

import com.fasterxml.jackson.databind.JsonNode
import com.hotels.styx.InetServer
import com.hotels.styx.NettyExecutor
import com.hotels.styx.ProxyConnectorFactory
import com.hotels.styx.ResponseInfoFormat
import com.hotels.styx.StyxObjectRecord
import com.hotels.styx.api.HttpHandler
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.netty.NettyServerBuilder
import com.hotels.styx.server.netty.connectors.ResponseEnhancer
import com.hotels.styx.serviceproviders.StyxServerFactory
import org.slf4j.LoggerFactory

object StyxHttpServer {
    @JvmField
    val SCHEMA = `object`(
            field("port", integer()),
            field("handler", string())

//                optional("tlsSettings", `object`(
//                        optional("sslProvider", string()),
//                        optional("certificateFile", string()),
//                        optional("certificateKeyFile", string()),
//                        optional("sessionTimeoutMillis", integer()),
//                        optional("sessionCacheSize", integer()),
//                        optional("cipherSuites", list(string())),
//                        optional("protocols", list(string()))
//                )),

//                optional("maxInitialLength", integer()),
//                optional("maxHeaderSize", integer()),
//                optional("maxChunkSize", integer()),
//
//                optional("requestTimeoutMillis", integer()),
//                optional("keepAliveTimeoutMillis", integer()),
//                optional("maxConnectionsCount", integer()),
//
//                optional("bossThreadsCount", integer()),
//                optional("workerThreadsCount", integer())
    )

    internal val LOGGER = LoggerFactory.getLogger(StyxHttpServer::class.java)
}

/*
 *  TODO: some fields can be optional:
 */
//data class StyxHttpServerTlsSettings(
//        val sslProvider: String,
//        val certificateFile: String,
//        val certificateKeyFile: String,
//        val sessionTimeoutMillis: Int,
//        val sessionCacheSize: Int,
//        val cipherSuites: List<String>,
//        val protocols: List<String>
//)

/*
 *  TODO: What's the best way to deal with optional values?
 */
data class StyxHttpServerConfiguration(
        val port: Int,
        val handler: String

//        val tlsSettings: StyxHttpServerTlsSettings?,

//        val maxInitialLength: Int?,
//        val maxHeaderSize: Int?,
//        val maxChunkSize: Int?,
//
//        val requestTimeoutMillis: Int?,
//        val keepAliveTimeoutMillis: Int?,
//        val maxConnectionsCount: Int?,
//
//        val bossThreadsCount: Int?,
//        val workerThreadsCount: Int?
)

class StyxHttpServerFactory : StyxServerFactory {
    override fun create(name: String, context: RoutingObjectFactory.Context, configuration: JsonNode, serverDb: StyxObjectStore<StyxObjectRecord<InetServer>>): InetServer {

        val config = JsonNodeConfig(configuration).`as`(StyxHttpServerConfiguration::class.java)

        val handlerName = StyxObjectReference(config.handler)

        val handler = HttpHandler { request, ctx ->
            context.refLookup()
                    .apply(handlerName)
                    .handle(request, ctx)
        }
        val environment = context.environment()

        val styxInfoHeaderName = environment.configuration().styxHeaderConfig().styxInfoHeaderName()
        val responseInfoFormat = ResponseInfoFormat(environment)

        val serviceName = "Http-Server(localhost-${config.port})"

        val server = NettyServerBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .setProtocolConnector(
                        ProxyConnectorFactory(
                                environment.configuration().proxyServerConfig(),
                                environment.metricRegistry(),
                                environment.errorListener(),
                                environment.configuration().get(ENCODE_UNWISECHARS).orElse(""),
                                ResponseEnhancer { builder, request -> builder.header(styxInfoHeaderName, responseInfoFormat.format(request)) },
                                false)
                                .create(HttpConnectorConfig(config.port)))
                .workerExecutor(NettyExecutor.create(serviceName, 0))
                .handler(handler)
                .build();

        return server
    }
}