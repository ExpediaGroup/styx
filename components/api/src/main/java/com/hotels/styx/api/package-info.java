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


/**
 * A programming interface for implementing Styx extensions. This includes the
 * {@link com.hotels.styx.api.HttpInterceptor} interface for intercepting HTTP traffic, and classes
 * to represent HTTP Request and Response messages.
 * <p></p>
 *
 * Styx exposes intercepted traffic to custom extensions by {@link com.hotels.styx.api.HttpInterceptor}
 * interface. A Styx plugin extends this interface to provide additional features
 * for shaping or modifying proxied HTTP messages, including requests and responses.
 * <p></p>
 * Styx represents proxied live traffic as instances of {@link com.hotels.styx.api.HttpRequest} and
 * {@link com.hotels.styx.api.HttpResponse} classes. They offer an interface for processing
 * a HTTP message content as a stream of network events. These classes are used
 * (1) from HttpInterceptors to process live traffic or (2) to deal with arbitrarily
 * large HTTP content.
 * <p></p>
 * {@link com.hotels.styx.api.FullHttpRequest} and {@link com.hotels.styx.api.FullHttpResponse} classes provide an immutable
 * aggregate view of a HTTP messages with full headers and content.
 * They are useful for dealing with HTTP messages with limited content sizes,
 * such as most RESTful API endpoints, or when "real-time" content processing
 * is not relevant.
 *
 * The API provides methods to convert between the streaming and full representations.
 *
 */
package com.hotels.styx.api;
