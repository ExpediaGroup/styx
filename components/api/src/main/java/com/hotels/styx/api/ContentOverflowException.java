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
package com.hotels.styx.api;

/**
 * Indicates a content aggregation failure because an aggregator function received
 * more data than expected.
 * <p>
 * A streaming HTTP message can be aggregated to a full message.
 * The aggregator methods {@link HttpRequest#toFullRequest}, and
 * {@link HttpResponse#toFullResponse} consume data from a network socket or
 * some other unpredictable source and emit this exception when more data is
 * received than expected.
 */
public class ContentOverflowException extends RuntimeException {
    public ContentOverflowException(String message) {
        super(message);
    }

    public ContentOverflowException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContentOverflowException(Throwable cause) {
        super(cause);
    }
}
