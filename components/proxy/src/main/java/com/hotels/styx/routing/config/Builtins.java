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
package com.hotels.styx.routing.config;

import com.google.common.collect.ImmutableMap;
import com.hotels.styx.InetServer;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config2.StyxObject;
import com.hotels.styx.routing.handlers.RouteRefLookup;
import com.hotels.styx.routing.interceptors.RewriteInterceptor;

import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.NOT_FOUND;
import static com.hotels.styx.routing.handlers2.PathPrefixRouterKt.getPathPrefixRouterDescriptor;
import static com.hotels.styx.routing.handlers2.RefLookupObjectKt.getRefLookupDescriptor;
import static com.hotels.styx.routing.handlers2.StaticResponseKt.getStaticResponseDescriptor;
import static com.hotels.styx.servers.StyxHttpServerObjectKt.getHttpServerDescriptor;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Contains mappings of builtin routing object and interceptor names to their factory methods.
 */
public final class Builtins {

    public static class StyxObjectDescriptor<T> {
        private final String typeName;
        private final Schema.FieldType schema;
        private final Class<?> klass;

        public StyxObjectDescriptor(String typeName, Schema.FieldType schema, Class<?> klass) {
            this.typeName = typeName;
            this.schema = schema;
            this.klass = klass;
        }

        public String type() {
            return typeName;
        }

        public Schema.FieldType schema() {
            return schema;
        }

        public Class<?> klass() {
            return this.klass;
        }
    }

    public static final String REF_LOOKUP = "RefLookup";
    public static final String STATIC_RESPONSE = "StaticResponse";
    public static final String PATH_PREFIX_ROUTER = "PathPrefixRouter";

    public static final String REWRITE = "Rewrite";

    public static final ImmutableMap<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>> ROUTING_OBJECT_DESCRIPTORS;
    public static final ImmutableMap<String, Builtins.StyxObjectDescriptor<StyxObject<InetServer>>> SERVER_DESCRIPTORS;

    public static final ImmutableMap<String, HttpInterceptorFactory> INTERCEPTOR_FACTORIES =
            ImmutableMap.of(REWRITE, new RewriteInterceptor.Factory());

    public static final ImmutableMap<String, Schema.FieldType> INTERCEPTOR_SCHEMAS =
            ImmutableMap.of(REWRITE, RewriteInterceptor.SCHEMA);

    public static final RouteRefLookup DEFAULT_REFERENCE_LOOKUP = reference -> (request, ctx) ->
            Eventual.of(response(NOT_FOUND)
                    .body(format("Handler not found for '%s'.", reference), UTF_8)
                    .build()
                    .stream());

    static {
        ROUTING_OBJECT_DESCRIPTORS = ImmutableMap.<String, Builtins.StyxObjectDescriptor<StyxObject<RoutingObject>>>builder()
                .put(PATH_PREFIX_ROUTER,  getPathPrefixRouterDescriptor())
                .put(REF_LOOKUP, getRefLookupDescriptor())
                .put(STATIC_RESPONSE, getStaticResponseDescriptor())
                .build();

        SERVER_DESCRIPTORS = ImmutableMap.<String, Builtins.StyxObjectDescriptor<StyxObject<InetServer>>>builder()
                .put("HttpServer", getHttpServerDescriptor())
                .build();
    }

    private Builtins() {
    }


}
