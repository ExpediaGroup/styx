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

import com.hotels.styx.config.validator.ObjectValidator;
import com.hotels.styx.config.validator.SchemaValidationException;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;

import java.util.Optional;

import static com.hotels.styx.config.validator.ObjectValidator.newDocument;
import static com.hotels.styx.config.validator.ObjectValidator.pass;
import static com.hotels.styx.config.validator.ObjectValidator.schema;
import static com.hotels.styx.config.validator.Schema.Field.bool;
import static com.hotels.styx.config.validator.Schema.Field.field;
import static com.hotels.styx.config.validator.Schema.Field.integer;
import static com.hotels.styx.config.validator.Schema.Field.list;
import static com.hotels.styx.config.validator.Schema.Field.object;
import static com.hotels.styx.config.validator.Schema.Field.string;

class ServerConfigSchema {
    private static final String HTTP_CONNECTOR = "HttpConnectorConfig";
    private static final String HTTPS_CONNECTOR = "HttpsConnectorConfig";
    private static final String SERVER_CONNECTORS = "ServerConnectors";
    private static final String PROXY_CONNECTOR_CONFIG = "ProxyConnectorConfig";
    private static final String ADMIN_CONNECTOR_CONFIG = "AdminConnectorConfig";
    private static final String LOG_FORMAT = "LogFormatConfig";
    private static final String REQUEST_LOGGING_CONFIG = "RequestLoggingConfig";
    private static final String STYX_HEADERS_CONFIG = "StyxHeadersConfig";

    private static final String URL_ENCODING_CONFIG = "UrlEncodingConfig";
    private static final ObjectValidator validator = newDocument()
            .subSchema(HTTP_CONNECTOR, schema()
                    .field("port", integer())
            )
            .subSchema(HTTPS_CONNECTOR, schema()
                    .field("port", integer())
                    .optional("sslProvider", string())
                    .optional("certificateFile", string())
                    .optional("certificateKeyFile", string())
                    .optional("sessionTimeoutMillis", integer())
                    .optional("sessionCacheSize", integer())
                    .optional("cipherSuites", list(string()))
            )
            .subSchema(SERVER_CONNECTORS, schema()
                    .atLeastOne(
                            field("http", object(HTTP_CONNECTOR)),
                            field("https", object(HTTPS_CONNECTOR))
                    )
            )
            .subSchema(PROXY_CONNECTOR_CONFIG, schema()
                    .field("connectors", object(SERVER_CONNECTORS))
                    .optional("bossThreadsCount", integer())
                    .optional("clientWorkerThreadsCount", integer())
                    .optional("workerThreadsCount", integer())
                    .optional("tcpNoDelay", bool())
                    .optional("nioReuseAddress", bool())
                    .optional("nioKeepAlive", bool())
                    .optional("maxInitialLength", integer())
                    .optional("maxHeaderSize", integer())
                    .optional("maxChunkSize", integer())
                    .optional("maxContentLength", integer())
                    .optional("requestTimeoutMillis", integer())
                    .optional("keepAliveTimeoutMillis", integer())
                    .optional("maxConnectionsCount", integer())
            )
            .subSchema(ADMIN_CONNECTOR_CONFIG, schema()
                    .field("connectors", object(SERVER_CONNECTORS))
                    .optional("bossThreadsCount", integer())
                    .optional("workerThreadsCount", integer())
                    .optional("tcpNoDelay", bool())
                    .optional("nioReuseAddress", bool())
                    .optional("nioKeepAlive", bool())
                    .optional("maxInitialLength", integer())
                    .optional("maxHeaderSize", integer())
                    .optional("maxChunkSize", integer())
                    .optional("maxContentLength", integer())
                    .optional("metricsCache", object(schema()
                            .field("enabled", bool())
                            .field("expirationMillis", integer())
                    ))
            )
            .subSchema(URL_ENCODING_CONFIG, schema()
                    .field("encoding", object(schema()
                            .field("unwiseCharactersToEncode", string())
                    )))
            .subSchema(LOG_FORMAT, schema()
                    .atLeastOne(
                            field("enabled", bool()),
                            field("longFormat", bool()))
            )
            .subSchema(REQUEST_LOGGING_CONFIG, schema()
                    .atLeastOne(
                            field("inbound", object(LOG_FORMAT)),
                            field("outbound", object(LOG_FORMAT))
                    )
            )
            .subSchema(STYX_HEADERS_CONFIG, schema()
                    .atLeastOne(
                            field("styxInfo", object(schema()
                                    .field("name", string())
                                    .optional("format", string())
                            )),
                            field("originId", object(schema()
                                .field("name", string())
                            )),
                            field("requestId", object(schema()
                                .field("name", string())
                            ))
                    )
            )
            .rootSchema(schema("")
                    .field("proxy", object(PROXY_CONNECTOR_CONFIG))
                    .field("admin", object(ADMIN_CONNECTOR_CONFIG))
                    .field("services", object(pass()))
                    .optional("url", object(URL_ENCODING_CONFIG))
                    .optional("request-logging", object(REQUEST_LOGGING_CONFIG))
                    .optional("styxHeaders", object(STYX_HEADERS_CONFIG))
                    .optional("include", string())
                    .optional("retrypolicy", object(pass()))
                    .optional("loadBalancing", object(pass()))
                    .optional("plugins", object(pass()))
                    .optional("jvmRouteName", string())
                    .optional("originRestrictionCookie", string())
                    .optional("responseInfoHeaderFormat", string())
                    .optional("httpPipeline", object(pass()))
                    .optional("logFormat", string())
                    .optional("userDefined", object(pass()))
            )
            .build();

    static Optional<String> validateServerConfiguration(YamlConfiguration yamlConfiguration) {
        try {
            validator.validateObject(yamlConfiguration.root());
            return Optional.empty();
        } catch (SchemaValidationException e) {
            return Optional.of(e.getMessage());
        }
    }

}
