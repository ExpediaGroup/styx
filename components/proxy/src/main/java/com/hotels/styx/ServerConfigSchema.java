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
package com.hotels.styx;

import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.config.schema.SchemaValidationException;
import com.hotels.styx.config.validator.DocumentFormat;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;

import java.util.Optional;

import static com.hotels.styx.config.schema.SchemaDsl.atLeastOne;
import static com.hotels.styx.config.schema.SchemaDsl.bool;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.list;
import static com.hotels.styx.config.schema.SchemaDsl.map;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.opaque;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static com.hotels.styx.config.validator.DocumentFormat.newDocument;

final class ServerConfigSchema {

    private static final Schema.FieldValue ROUTING_CONFIGURATION = object(
                    optional("name", string()),
                    field("type", string()),
                    optional("tags", list(string())),
                    optional("config", union("type")));

    private static final DocumentFormat STYX_SERVER_CONFIGURATION_SCHEMA;

    static {
        Schema.FieldValue httpConnectorSchema = object(
                field("port", integer())
        );

        Schema.FieldValue httpsConnectorSchema = object(
                field("port", integer()),
                optional("sslProvider", string()),
                optional("certificateFile", string()),
                optional("certificateKeyFile", string()),
                optional("sessionTimeoutMillis", integer()),
                optional("sessionCacheSize", integer()),
                optional("cipherSuites", list(string())),
                optional("protocols", list(string()))
        );

        Schema.FieldValue serverConnectorsSchema = object(
                optional("http", httpConnectorSchema),
                optional("https", httpsConnectorSchema),
                atLeastOne("http", "https")
        );
        Schema.FieldValue urlEncodingConfigSchema = object(
                field("encoding", object(
                        field("unwiseCharactersToEncode", string())
                ))
        );
        Schema.FieldValue logFormatSchema = object(
                optional("enabled", bool()),
                optional("longFormat", bool()),
                atLeastOne("enabled", "longFormat")
        );
        Schema.FieldValue proxyConnectorConfigSchema = object(
                field("connectors", serverConnectorsSchema),
                optional("bossThreadsCount", integer()),
                optional("clientWorkerThreadsCount", integer()),
                optional("workerThreadsCount", integer()),
                optional("tcpNoDelay", bool()),
                optional("nioReuseAddress", bool()),
                optional("nioKeepAlive", bool()),
                optional("maxInitialLength", integer()),
                optional("maxHeaderSize", integer()),
                optional("maxChunkSize", integer()),
                optional("maxContentLength", integer()),
                optional("requestTimeoutMillis", integer()),
                optional("keepAliveTimeoutMillis", integer()),
                optional("maxConnectionsCount", integer())
        );
        Schema.FieldValue adminConnectorConfigSchema = object(
                field("connectors", serverConnectorsSchema),
                optional("bossThreadsCount", integer()),
                optional("workerThreadsCount", integer()),
                optional("tcpNoDelay", bool()),
                optional("nioReuseAddress", bool()),
                optional("nioKeepAlive", bool()),
                optional("maxInitialLength", integer()),
                optional("maxHeaderSize", integer()),
                optional("maxChunkSize", integer()),
                optional("maxContentLength", integer()),
                optional("metricsCache", object(
                        field("enabled", bool()),
                        field("expirationMillis", integer())
                ))
        );
        Schema.FieldValue requestLoggingConfigSchema = object(
                optional("inbound", logFormatSchema),
                optional("outbound", logFormatSchema),
                atLeastOne("inbound", "outbound")
        );
        Schema.FieldValue styxHeadersConfigSchema = object(
                optional("styxInfo", object(
                        field("name", string()),
                        optional("format", string())
                )),
                optional("originId", object(
                        field("name", string())
                )),
                optional("requestId", object(
                        field("name", string())
                )),
                atLeastOne("styxInfo", "originId", "requestId")
        );
        STYX_SERVER_CONFIGURATION_SCHEMA = newDocument()
                    .rootSchema(object(
                            field("proxy", proxyConnectorConfigSchema),
                            field("admin", adminConnectorConfigSchema),
                            field("services", object(
                                    field("factories", map(object(opaque())))
                            )),
                            optional("url", urlEncodingConfigSchema),
                            optional("request-logging", requestLoggingConfigSchema),
                            optional("styxHeaders", styxHeadersConfigSchema),
                            optional("include", string()),
                            optional("retrypolicy", object(opaque())),
                            optional("loadBalancing", object(opaque())),
                            optional("plugins", object(
                                    optional("active", string()),
                                    optional("all", map(object(opaque())))
                            )),
                            optional("jvmRouteName", string()),
                            optional("originRestrictionCookie", string()),
                            optional("responseInfoHeaderFormat", string()),
                            optional("httpPipeline", object(opaque())),
                            optional("logFormat", string()),
                            optional("userDefined", object(opaque())),
                            optional("requestTracking", bool())
                    ))
                    .build();
    }


    private ServerConfigSchema() {
    }

    static Optional<String> validateServerConfiguration(YamlConfiguration yamlConfiguration) {
        try {
            STYX_SERVER_CONFIGURATION_SCHEMA.validateObject(yamlConfiguration.root());
            return Optional.empty();
        } catch (SchemaValidationException e) {
            return Optional.of(e.getMessage());
        }
    }

}
