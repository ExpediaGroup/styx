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
 * A programming interface for Styx extensions. This includes a Styx
 * {@code HttpInterceptor} interface for intercepting HTTP traffic, and classes
 * to represent HTTP Request and Response messages.
 * <p></p>
 *
 * Styx exposes intercepted traffic to custom extensions via {@code HttpInterceptor}
 * class.
 * <p></p>
 * The intercepted live traffic is represented as {@code HttpRequest} and
 * {@code HttpResponse} classes.
 * <p></p>
 * The API also has aggregated {@code FullHttpRequest} and {@code FullHttpResponse}
 * classes. They are useful for most situations apart from intercepting live traffic.
 *
 */
package com.hotels.styx.api;
