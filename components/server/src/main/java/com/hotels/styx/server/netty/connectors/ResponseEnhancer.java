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
package com.hotels.styx.server.netty.connectors;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;

/**
 * Enhances responses. For example, adding headers.
 */
public interface ResponseEnhancer {
    ResponseEnhancer DO_NOT_MODIFY_RESPONSE = (responseBuilder, request) -> responseBuilder;

    /**
     * Enhance response while it is being built.
     *
     * @param responseBuilder builder of response
     * @param request request to which Styx is responding
     * @return enhanced response builder
     */
    HttpResponse.Builder enhance(HttpResponse.Builder responseBuilder, HttpRequest request);

    /**
     * Create a new enhanced response, based on an existing one. This is less efficient than
     * {@link #enhance(HttpResponse.Builder, HttpRequest)} as it has to create a new builder
     * and build, but it suitable for cases where that would have to happen anyway.
     *
     * @param response response
     * @param request request
     * @return enhanced response
     */
    default HttpResponse enhance(HttpResponse response, HttpRequest request) {
        return enhance(response.newBuilder(), request).build();
    }
}
