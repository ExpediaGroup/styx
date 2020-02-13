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
import com.hotels.styx.ProxyConnectorFactory
import com.hotels.styx.ResponseInfoFormat
import com.hotels.styx.StyxObjectRecord
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.bool
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.list
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS
import com.hotels.styx.routing.config.RoutingObjectFactory
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.db.StyxObjectStore
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.server.netty.NettyServerBuilder
import com.hotels.styx.server.netty.connectors.ResponseEnhancer
import com.hotels.styx.serviceproviders.StyxServerFactory
import org.slf4j.LoggerFactory

object StyxHttpServer {
    @JvmField
    val SCHEMA = `object`(
            field("port", integer()),
            field("handler", string()),
            optional("compressResponses", bool()),
            optional("tlsSettings", `object`(
                    optional("sslProvider", string()),
                    optional("certificateFile", string()),
                    optional("certificateKeyFile", string()),
                    optional("sessionTimeoutMillis", integer()),
                    optional("sessionCacheSize", integer()),
                    optional("cipherSuites", list(string())),
                    optional("protocols", list(string()))
            )),

            optional("maxInitialLength", integer()),
            optional("maxHeaderSize", integer()),
            optional("maxChunkSize", integer()),

            optional("requestTimeoutMillis", integer()),
            optional("keepAliveTimeoutMillis", integer()),
            optional("maxConnectionsCount", integer()),

            optional("bossExecutor", string()),
            optional("workerExecutor", string())
    )

    internal val LOGGER = LoggerFactory.getLogger(StyxHttpServer::class.java)
}

private data class StyxHttpServerTlsSettings(
        val certificateFile: String,
        val certificateKeyFile: String,
        val sslProvider: String = "JDK",
        val sessionTimeoutMillis: Int = 300000,
        val sessionCacheSize: Int = 0,
        val cipherSuites: List<String> = listOf(),
        val protocols: List<String> = listOf()
)


private data class StyxHttpServerConfiguration(
        val port: Int,
        val handler: String,
        val compressResponses: Boolean = false,
        val tlsSettings: StyxHttpServerTlsSettings?,

        val maxInitialLength: Int = 4096,
        val maxHeaderSize: Int = 8192,
        val maxChunkSize: Int = 8192,

        val requestTimeoutMillis: Int = 60000,
        val keepAliveTimeoutMillis: Int = 120000,
        val maxConnectionsCount: Int = 512,

        val bossExecutor: String = "StyxHttpServer-Global-Boss",
        val workerExecutor: String = "StyxHttpServer-Global-Worker"
)

internal class StyxHttpServerFactory : StyxServerFactory {
    private fun serverConfig(configuration: JsonNode) = JsonNodeConfig(configuration).`as`(StyxHttpServerConfiguration::class.java)

    override fun create(name: String, context: RoutingObjectFactory.Context, configuration: JsonNode, serverDb: StyxObjectStore<StyxObjectRecord<InetServer>>): InetServer {
        val config = serverConfig(configuration)
        val environment = context.environment()

        val bossExecutor = context.executors()[config.bossExecutor]
                .orElseThrow { IllegalArgumentException("StyxHttpServer($name) configuration error: bossExecutor='${config.bossExecutor}' not declared.") }
                .styxService

        val workerExecutor = context.executors()[config.workerExecutor]
                .orElseThrow { IllegalArgumentException("StyxHttpServer($name) configuration error: workerExecutor='${config.workerExecutor}' not declared.") }
                .styxService

        return NettyServerBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .setProtocolConnector(
                        ProxyConnectorFactory(
                                ProxyServerConfig.Builder()
                                        .setCompressResponses(config.compressResponses)
                                        .setMaxInitialLength(config.maxInitialLength)
                                        .setMaxHeaderSize(config.maxHeaderSize)
                                        .setMaxChunkSize(config.maxChunkSize)
                                        .setRequestTimeoutMillis(config.requestTimeoutMillis)
                                        .setKeepAliveTimeoutMillis(config.keepAliveTimeoutMillis)
                                        .setMaxConnectionsCount(config.maxConnectionsCount)
                                        .build(),
                                environment.metricRegistry(),
                                environment.errorListener(),
                                environment.configuration().get(ENCODE_UNWISECHARS).orElse(""),
                                ResponseEnhancer { builder, request ->
                                    builder.header(
                                            environment.configuration().styxHeaderConfig().styxInfoHeaderName(),
                                            ResponseInfoFormat(environment).format(request))
                                },
                                false,
                                environment.httpMessageFormatter())
                                .create(
                                        if (config.tlsSettings == null) {
                                            HttpConnectorConfig(config.port)
                                        } else {
                                            HttpsConnectorConfig.Builder()
                                                    .port(config.port)
                                                    .sslProvider(config.tlsSettings.sslProvider)
                                                    .certificateFile(config.tlsSettings.certificateFile)
                                                    .certificateKeyFile(config.tlsSettings.certificateKeyFile)
                                                    .cipherSuites(config.tlsSettings.cipherSuites)
                                                    .protocols(*config.tlsSettings.protocols.toTypedArray())
                                                    .build()
                                        }))
                .bossExecutor(bossExecutor)
                .workerExecutor(workerExecutor)
                .handler({ request, ctx ->
                    context.refLookup()
                            .apply(StyxObjectReference(config.handler))
                            .handle(request, ctx)
                })
                .build();
    }
}
