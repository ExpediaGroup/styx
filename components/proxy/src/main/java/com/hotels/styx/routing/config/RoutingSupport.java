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
package com.hotels.styx.routing.config;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.lang.String.format;

/**
 * RoutingSupport providers supporting methods for HTTP routing features.
 */
public final class RoutingSupport {

    private RoutingSupport() {
    }

    public static <T> List<T> append(List<T> list, T elem) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();

        builder.addAll(list);
        builder.add(elem);

        return builder.build();
    }

    public static RuntimeException missingAttributeError(RouteHandlerDefinition routingDef, String parent, String expected) {
        return new IllegalArgumentException(
                format("Routing object definition of type '%s', attribute='%s', is missing a mandatory '%s' attribute.",
                        routingDef.type(), parent, expected));
    }

}
