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
package com.hotels.styx.support.api;

import com.hotels.styx.api.HttpMessage;
import com.hotels.styx.api.HttpMessageBody;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Provides a support method for dealing with {@link HttpMessageBody}.
 */
public final class HttpMessageBodies {
    /**
     * Return the body of {@code message} as string. Note that this will buffer all the message body in memory.
     *
     * @param message the message to read the body from
     * @return the body of the message as string
     */
    public static String bodyAsString(HttpMessage message) {
        return bodyAsString(message.body());
    }

    static String bodyAsString(HttpMessageBody body) {
        return body.decode(bytes -> bytes.toString(UTF_8), 0x100000)
                .toBlocking()
                .single();
    }

    private HttpMessageBodies() {
    }
}
