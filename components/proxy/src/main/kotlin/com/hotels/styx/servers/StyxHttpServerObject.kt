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

import com.fasterxml.jackson.annotation.JsonProperty
import com.hotels.styx.InetServer
import com.hotels.styx.NettyExecutor
import com.hotels.styx.ProxyConnectorFactory
import com.hotels.styx.ResponseInfoFormat
import com.hotels.styx.config.schema.SchemaDsl.`object`
import com.hotels.styx.config.schema.SchemaDsl.bool
import com.hotels.styx.config.schema.SchemaDsl.field
import com.hotels.styx.config.schema.SchemaDsl.integer
import com.hotels.styx.config.schema.SchemaDsl.list
import com.hotels.styx.config.schema.SchemaDsl.optional
import com.hotels.styx.config.schema.SchemaDsl.string
import com.hotels.styx.proxy.ProxyServerConfig
import com.hotels.styx.proxy.encoders.ConfigurableUnwiseCharsEncoder.ENCODE_UNWISECHARS
import com.hotels.styx.routing.config.Builtins
import com.hotels.styx.routing.config.StyxObjectReference
import com.hotels.styx.routing.config2.StyxObject
import com.hotels.styx.server.HttpConnectorConfig
import com.hotels.styx.server.HttpsConnectorConfig
import com.hotels.styx.server.netty.NettyServerBuilder
import com.hotels.styx.server.netty.connectors.ResponseEnhancer

val HttpServerDescriptor = Builtins.StyxObjectDescriptor<StyxObject<InetServer>>(
        "HttpServer",
        `object`(
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

                optional("bossThreadsCount", integer()),
                optional("workerThreadsCount", integer())
        )
        ,
        StyxHttpServer::class.java
)

data class StyxHttpServer(
        @JsonProperty val port: Int,
        @JsonProperty val handler: String,
        @JsonProperty val compressResponses: Boolean = false,
        @JsonProperty val tlsSettings: StyxHttpServerTlsSettings? = null,

        @JsonProperty val maxInitialLength: Int = 4096,
        @JsonProperty val maxHeaderSize: Int = 8192,
        @JsonProperty val maxChunkSize: Int = 8192,

        @JsonProperty val requestTimeoutMillis: Int = 60000,
        @JsonProperty val keepAliveTimeoutMillis: Int = 120000,
        @JsonProperty val maxConnectionsCount: Int = 512,

        @JsonProperty val bossThreadsCount: Int = 0,
        @JsonProperty val workerThreadsCount: Int = 0
) : StyxObject<InetServer> {

    override fun type() = HttpServerDescriptor.type()

    override fun build(context: StyxObject.Context): InetServer {
        val environment = context.environment()
        val proxyServerConfig = ProxyServerConfig.Builder()
                .setCompressResponses(compressResponses)
                .setMaxInitialLength(maxInitialLength)
                .setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize)
                .setRequestTimeoutMillis(requestTimeoutMillis)
                .setKeepAliveTimeoutMillis(keepAliveTimeoutMillis)
                .setMaxConnectionsCount(maxConnectionsCount)
                .setBossThreadsCount(bossThreadsCount)
                .setWorkerThreadsCount(workerThreadsCount)
                .build()

        return NettyServerBuilder()
                .setMetricsRegistry(environment.metricRegistry())
                .setProtocolConnector(
                        ProxyConnectorFactory(
                                proxyServerConfig,
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
                                        if (tlsSettings == null) {
                                            HttpConnectorConfig(port)
                                        } else {
                                            HttpsConnectorConfig.Builder()
                                                    .port(port)
                                                    .sslProvider(tlsSettings.sslProvider)
                                                    .certificateFile(tlsSettings.certificateFile)
                                                    .certificateKeyFile(tlsSettings.certificateKeyFile)
                                                    .cipherSuites(tlsSettings.cipherSuites)
                                                    .protocols(*tlsSettings.protocols.toTypedArray())
                                                    .build()
                                        }))
                .workerExecutor(NettyExecutor.create("Http-Server(localhost-${port})", workerThreadsCount))
                .bossExecutor(NettyExecutor.create("Http-Server(localhost-${port})", bossThreadsCount))
                .handler({ request, ctx ->
                    context.refLookup()
                            .apply(StyxObjectReference(handler))
                            .handle(request, ctx)
                })
                .build();
    }
}

data class StyxHttpServerTlsSettings(
        @JsonProperty val certificateFile: String,
        @JsonProperty val certificateKeyFile: String,
        @JsonProperty val sslProvider: String = "JDK",
        @JsonProperty val sessionTimeoutMillis: Int = 300000,
        @JsonProperty val sessionCacheSize: Int = 0,
        @JsonProperty val cipherSuites: List<String> = listOf(),
        @JsonProperty val protocols: List<String> = listOf()
)
