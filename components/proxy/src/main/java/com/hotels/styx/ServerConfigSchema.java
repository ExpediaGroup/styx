/*
  Copyright (C) 2013-2018 Expedia Inc.

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
import static com.hotels.styx.config.schema.SchemaDsl.schema;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.config.validator.DocumentFormat.newDocument;

final class ServerConfigSchema {
    private static final String HTTP_CONNECTOR = "HttpConnectorConfig";
    private static final String HTTPS_CONNECTOR = "HttpsConnectorConfig";
    private static final String SERVER_CONNECTORS = "ServerConnectors";
    private static final String PROXY_CONNECTOR_CONFIG = "ProxyConnectorConfig";
    private static final String ADMIN_CONNECTOR_CONFIG = "AdminConnectorConfig";
    private static final String LOG_FORMAT = "LogFormatConfig";
    private static final String REQUEST_LOGGING_CONFIG = "RequestLoggingConfig";
    private static final String STYX_HEADERS_CONFIG = "StyxHeadersConfig";

    private static final String URL_ENCODING_CONFIG = "UrlEncodingConfig";
    private static final DocumentFormat STYX_SERVER_CONFIGURATION_SCHEMA = newDocument()
            .subSchema(HTTP_CONNECTOR, schema(
                    field("port", integer())
            ))
            .subSchema(HTTPS_CONNECTOR, schema(
                    field("port", integer()),
                    optional("sslProvider", string()),
                    optional("certificateFile", string()),
                    optional("certificateKeyFile", string()),
                    optional("sessionTimeoutMillis", integer()),
                    optional("sessionCacheSize", integer()),
                    optional("cipherSuites", list(string())),
                    optional("protocols", list(string()))
            ))
            .subSchema(SERVER_CONNECTORS, schema(
                    optional("http", object(HTTP_CONNECTOR)),
                    optional("https", object(HTTPS_CONNECTOR)),
                    atLeastOne("http", "https")
            ))
            .subSchema(PROXY_CONNECTOR_CONFIG, schema(
                    field("connectors", object(SERVER_CONNECTORS)),
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
            ))
            .subSchema(ADMIN_CONNECTOR_CONFIG, schema(
                    field("connectors", object(SERVER_CONNECTORS)),
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
            ))
            .subSchema(URL_ENCODING_CONFIG, schema(
                    field("encoding", object(
                            field("unwiseCharactersToEncode", string())
                    ))
            ))
            .subSchema(LOG_FORMAT, schema(
                    optional("enabled", bool()),
                    optional("longFormat", bool()),
                    atLeastOne("enabled", "longFormat")
            ))
            .subSchema(REQUEST_LOGGING_CONFIG, schema(
                    optional("inbound", object(LOG_FORMAT)),
                    optional("outbound", object(LOG_FORMAT)),
                    atLeastOne("inbound", "outbound")
            ))
            .subSchema(STYX_HEADERS_CONFIG, schema(
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
            ))
            .rootSchema(schema(
                    field("proxy", object(PROXY_CONNECTOR_CONFIG)),
                    field("admin", object(ADMIN_CONNECTOR_CONFIG)),
                    field("services", object(
                            field("factories", map(object(opaque())))
                    )),
                    optional("url", object(URL_ENCODING_CONFIG)),
                    optional("request-logging", object(REQUEST_LOGGING_CONFIG)),
                    optional("styxHeaders", object(STYX_HEADERS_CONFIG)),
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
                    optional("userDefined", object(opaque()))
            ))
            .build();

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
