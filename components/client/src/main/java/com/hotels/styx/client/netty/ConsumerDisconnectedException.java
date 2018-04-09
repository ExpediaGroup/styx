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
package com.hotels.styx.client.netty;

import static java.lang.String.format;

/**
 * Indicates that consumer is no longer observing the HTTP content observable.
 *
 * Typically this occurs due to Styx server-side cancelling due to some
 * reason.
 */
public class ConsumerDisconnectedException extends RuntimeException {

    public ConsumerDisconnectedException(String message, String state) {
        super(format("%s producerState=%s", message, state));
    }
}
