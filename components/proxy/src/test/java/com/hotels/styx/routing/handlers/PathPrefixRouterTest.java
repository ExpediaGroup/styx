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
package com.hotels.styx.routing.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.config.Builtins;
import com.hotels.styx.routing.config.RoutingObjectFactory;
import com.hotels.styx.routing.config.StyxObjectDefinition;
import com.hotels.styx.routing.config.StyxObjectReference;
import com.hotels.styx.server.NoServiceConfiguredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathPrefixRouterTest {
    private static final ObjectMapper mapper = new ObjectMapper();

    public Map<StyxObjectReference, RoutingObject> routingObjects = new HashMap<>();
    public RoutingObjectFactory.Context Routingcontext;
    public HttpInterceptor.Context context = mock(HttpInterceptor.Context.class);

    @BeforeEach
    public void beforeEach() throws Exception {
        Routingcontext = mock(RoutingObjectFactory.Context.class);
        when(Routingcontext.refLookup()).thenReturn(ref -> routingObjects.get(ref));
    }

    @Test
    public void no_routes_always_throws_NoServiceConfiguredException() throws Exception {
        PathPrefixRouter router = buildRouter(emptyMap());

        assertThrows(NoServiceConfiguredException.class, () ->
                Mono.from(router.handle(LiveHttpRequest.get("/").build(), null)).block()
        );
    }

    @Test
    public void root_handler_handles_sub_paths() throws Exception {
        RoutingObject rootHandler = mock(RoutingObject.class);

        routingObjects.put(new StyxObjectReference("rootHandler"), rootHandler);

        PathPrefixRouter router = buildRouter(singletonMap("/", "rootHandler"));

        testRequestRoute(router, "/", rootHandler);
        testRequestRoute(router, "/foo/bar", rootHandler);
    }

    @Test
    public void most_specific_path_is_chosen() throws Exception {
        RoutingObject rootHandler = mock(RoutingObject.class);
        RoutingObject fooFileHandler = mock(RoutingObject.class);
        RoutingObject fooPathHandler = mock(RoutingObject.class);
        RoutingObject fooBarFileHandler = mock(RoutingObject.class);
        RoutingObject fooBarPathHandler = mock(RoutingObject.class);
        RoutingObject fooBazFileHandler = mock(RoutingObject.class);

        routingObjects.put(new StyxObjectReference("rootHandler"), rootHandler);
        routingObjects.put(new StyxObjectReference("fooFileHandler"), fooFileHandler);
        routingObjects.put(new StyxObjectReference("fooPathHandler"), fooPathHandler);
        routingObjects.put(new StyxObjectReference("fooBarFileHandler"), fooBarFileHandler);
        routingObjects.put(new StyxObjectReference("fooBarPathHandler"), fooBarPathHandler);
        routingObjects.put(new StyxObjectReference("fooBazFileHandler"), fooBazFileHandler);

        PathPrefixRouter router = buildRouter(ImmutableMap.<String, String>builder()
                .put("/", "rootHandler")
                .put("/foo", "fooFileHandler")
                .put("/foo/", "fooPathHandler")
                .put("/foo/bar", "fooBarFileHandler")
                .put("/foo/bar/", "fooBarPathHandler")
                .put("/foo/baz", "fooBazFileHandler")
                .build());

        testRequestRoute(router, "/", rootHandler);
        testRequestRoute(router, "/foo", fooFileHandler);
        testRequestRoute(router, "/foo/x", fooPathHandler);
        testRequestRoute(router, "/foo/", fooPathHandler);
        testRequestRoute(router, "/foo/bar", fooBarFileHandler);
        testRequestRoute(router, "/foo/bar/x", fooBarPathHandler);
        testRequestRoute(router, "/foo/bar/", fooBarPathHandler);
        testRequestRoute(router, "/foo/baz/", fooBazFileHandler);
        testRequestRoute(router, "/foo/baz/y", fooBazFileHandler);
    }

    private PathPrefixRouter buildRouter(Map<String, String> prefixRoutes) {
        ArrayNode routes = mapper.createArrayNode();
        prefixRoutes.forEach((path, route) -> routes.add(mapper.createObjectNode()
                .put("prefix", path)
                .put("destination", route)));
        ObjectNode config = mapper.createObjectNode().set("routes", routes);

        StyxObjectDefinition configBlock = new StyxObjectDefinition("test", Builtins.PATH_PREFIX_ROUTER, config);

        PathPrefixRouter router = (PathPrefixRouter) new PathPrefixRouter.Factory().build(singletonList("test"), Routingcontext, configBlock);

        return router;
    }

    private void testRequestRoute(PathPrefixRouter router, String path, RoutingObject handler) {
        LiveHttpRequest request = LiveHttpRequest.get(path).build();
        router.handle(request, context);
        verify(handler).handle(request, context);
    }
}
