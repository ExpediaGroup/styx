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

import com.hotels.styx.api.HttpHandler;

import java.util.List;

/**
 * A factory for constructing HTTP handler objects from a RouteHandlerDefinition yaml config block.
 */
public interface HttpHandlerFactory {
    /**
     * Constructs a terminal action handler according to routing configuration block.
     * <p>
     * Constructs a terminal action handler for the HTTP request. The handler is constructed
     * according to the definition codified in the RouteHandlerDefinition instance.
     * The RouteHandlerFactory is a factory object for constructing any dependant routing
     * objects. The objectVariables is a map of already instantiated routing objects
     * that can be referred from the handler being built.
     * <p>
     *
     * @param parents
     * @param builder
     * @param configBlock
     * @return
     */
    HttpHandler build(List<String> parents, RouteHandlerFactory builder, RouteHandlerDefinition configBlock);
}
