/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.api.messages;

import io.netty.buffer.ByteBuf;
import rx.Observable;

import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static rx.Observable.empty;
import static rx.Observable.just;

/**
 * Common logic for new HTTP message classes.
 */
public final class HttpSupport {
    private HttpSupport() {
    }

    /**
     * Determine the content-length of a full aggregated body.
     * This method only covers some common cases, and others will
     * need the content-length to be set manually.
     *
     * Cases covered:
     * <ul>
     * <li> null - no content, so content-length = 0 </li>
     * <li> byte array - content-length = array length </li>
     * <li> String - content-length = number of bytes, assuming UTF-8 encoding </li>
     * </ul>
     *
     * Any other object will be converted to a String with its toString method, and
     * then treated as UTF-8.
     *
     * @param body body
     * @return content-length
     */
    public static int contentLength(Object body) {
        if (body == null) {
            return 0;
        }

        if (body instanceof byte[]) {
            return ((byte[]) body).length;
        }

        if (body instanceof String) {
            return contentLength(((String) body).getBytes(UTF_8));
        }

        return contentLength(body.toString());
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
    public static <T> Observable<ByteBuf> encodeBody(T body, Function<T, ByteBuf> encoder) {
        return  body == null || "".equals(body)
                ? empty()
                : just(encoder.apply(body));
    }
}
