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
import static com.hotels.styx.config.schema.SchemaDsl.routingObject;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.schema.SchemaDsl.union;
import static com.hotels.styx.config.validator.DocumentFormat.newDocument;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_HANDLER_SCHEMAS;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_SERVER_SCHEMAS;
import static com.hotels.styx.routing.config.Builtins.BUILTIN_SERVICE_PROVIDER_SCHEMAS;
import static com.hotels.styx.routing.config.Builtins.INTERCEPTOR_SCHEMAS;

final class ServerConfigSchema {

    private static final DocumentFormat.Builder STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER;

    static {

        Schema.FieldType serverConnectorsSchema = object(
                optional("http", object(
                        field("port", integer())
                )),
                optional("https", object(
                        field("port", integer()),
                        optional("sslProvider", string()),
                        optional("certificateFile", string()),
                        optional("certificateKeyFile", string()),
                        optional("sessionTimeoutMillis", integer()),
                        optional("sessionCacheSize", integer()),
                        optional("cipherSuites", list(string())),
                        optional("protocols", list(string()))
                )),
                atLeastOne("http", "https")
        );

        Schema.FieldType logFormatSchema = object(
                optional("enabled", bool()),
                optional("longFormat", bool()),
                atLeastOne("enabled", "longFormat")
        );

        STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER = newDocument()
                    .rootSchema(object(
                            field("proxy", object(
                                    optional("compressResponses", bool()),
                                    field("connectors", serverConnectorsSchema),
                                    optional("bossThreadsCount", integer()),
                                    optional("clientWorkerThreadsCount", integer()),
                                    optional("workerThreadsCount", integer()),
                                    // tcpNoDelay is deprecated by PR #464
                                    optional("tcpNoDelay", bool()),
                                    // nioReuseAddress is deprecated by PR #464
                                    optional("nioReuseAddress", bool()),
                                    // nioKeepAlive is deprecated by PR #464
                                    optional("nioKeepAlive", bool()),
                                    optional("maxInitialLength", integer()),
                                    optional("maxHeaderSize", integer()),
                                    optional("maxChunkSize", integer()),
                                    // maxContentLength is deprecated by PR #464
                                    optional("maxContentLength", integer()),
                                    optional("requestTimeoutMillis", integer()),
                                    optional("keepAliveTimeoutMillis", integer()),
                                    optional("maxConnectionsCount", integer())
                            )),
                            field("admin", object(
                                    field("connectors", serverConnectorsSchema),
                                    optional("bossThreadsCount", integer()),
                                    optional("workerThreadsCount", integer()),
                                    // tcpNoDelay is deprecated by PR #464
                                    optional("tcpNoDelay", bool()),
                                    // nioReuseAddress is deprecated by PR #464
                                    optional("nioReuseAddress", bool()),
                                    // nioKeepAlive is deprecated by PR #464
                                    optional("nioKeepAlive", bool()),
                                    optional("maxInitialLength", integer()),
                                    optional("maxHeaderSize", integer()),
                                    optional("maxChunkSize", integer()),
                                    // maxContentLength is deprecated by PR #464
                                    optional("maxContentLength", integer()),
                                    optional("metricsCache", object(
                                            field("enabled", bool()),
                                            field("expirationMillis", integer())
                                    ))
                            )),
                            optional("services", object(
                                    field("factories", map(object(opaque())))
                            )),
                            optional("providers", map(routingObject())),
                            optional("servers", map(routingObject())),
                            optional("url", object(
                                    field("encoding", object(
                                            field("unwiseCharactersToEncode", string())
                                    ))
                            )),
                            optional("request-logging", object(
                                    optional("inbound", logFormatSchema),
                                    optional("outbound", logFormatSchema),
                                    atLeastOne("inbound", "outbound"),
                                    optional("hideHeaders", list(string())),
                                    optional("hideCookies", list(string()))
                            )),
                            optional("styxHeaders", object(
                                    optional("styxInfo", object(
                                            field("name", string()),
                                            optional("valueFormat", string())
                                    )),
                                    optional("originId", object(
                                            field("name", string())
                                    )),
                                    optional("requestId", object(
                                            field("name", string())
                                    )),
                                    atLeastOne("styxInfo", "originId", "requestId")
                            )),
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
                            optional("httpPipeline", routingObject()),
                            optional("logFormat", string()),
                            optional("userDefined", object(opaque())),
                            optional("requestTracking", bool()),
                            optional("routingObjects", map(object(
                                    optional("name", string()),
                                    field("type", string()),
                                    optional("tags", list(string())),
                                    optional("config", union("type")))))
                    ));

        BUILTIN_HANDLER_SCHEMAS.forEach(STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER::typeExtension);
        BUILTIN_SERVICE_PROVIDER_SCHEMAS.forEach(STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER::typeExtension);
        BUILTIN_SERVER_SCHEMAS.forEach(STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER::typeExtension);
        INTERCEPTOR_SCHEMAS.forEach(STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER::typeExtension);
    }


    private ServerConfigSchema() {
    }

    static Optional<String> validateServerConfiguration(YamlConfiguration yamlConfiguration) {
        try {
            STYX_SERVER_CONFIGURATION_SCHEMA_BUILDER.build().validateObject(yamlConfiguration.root());
            return Optional.empty();
        } catch (SchemaValidationException e) {
            return Optional.of(e.getMessage());
        }
    }

}
