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

import io.netty.buffer.ByteBuf;
import rx.Observable;

import java.util.function.Function;

import static rx.Observable.empty;
import static rx.Observable.just;

/**
 * Common logic for new HTTP message classes.
 */
final class HttpSupport {
    private HttpSupport() {
    }

    /**
     * Encodes a body of arbitrary type, returning an empty observable if the body is null or an empty string
     * and otherwise creating an observable of buffers using the provided encoder.
     *
     * @param body body
     * @param encoder encoder
     * @param <T> body type
     * @return an observable of buffers, may be empty
     */
    static <T> Observable<ByteBuf> encodeBody(T body, Function<T, ByteBuf> encoder) {
        return  body == null || "".equals(body)
                ? empty()
                : just(encoder.apply(body));
    }
}
