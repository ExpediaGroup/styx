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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;

/**
 * All behaviour common to both full requests and full responses.
 */
public interface FullHttpMessage {
    /**
     * Returns the protocol version of this.
     *
     * @return the protocol version
     */
    HttpVersion version();

    /**
     * Return all headers in this request.
     *
     * @return all headers
     */
    HttpHeaders headers();

    /**
     * Returns the body of this message in its unencoded form.
     *
     * @return the body
     */
    byte[] body();

    /**
     * Returns the message body as a String decoded with provided character set.
     *
     * Decodes the message body into a Java String object with a provided charset.
     * The caller must ensure the provided charset is compatible with message content
     * type and encoding.
     *
     * @param charset     Charset used to decode message body.
     * @return            Message body as a String.
     */
    String bodyAs(Charset charset);

    /**
     * Returns the value of the header with the specified {@code name}.
     * If there is more than one header value for the specified header name, the first value is returned.
     *
     * @return the value of the header with the specified {@code name} if present
     */
    default Optional<String> header(CharSequence name) {
        return headers().get(name);
    }

    /**
     * Returns the values of the headers with the specified {@code name}.
     *
     * @param name the name of the headers
     * @return A {@link List} of header values which will be empty if no values are found
     */
    default List<String> headers(CharSequence name) {
        return headers().getAll(name);
    }

    /**
     * Return the {@code 'Content-Length'} header value.
     *
     * @return the content-length if present
     */
    default Optional<Integer> contentLength() {
        return header(CONTENT_LENGTH).map(Integer::valueOf);
    }

    /**
     * Returns the value of the {@code 'Content-Type'} header.
     *
     * @return content-type if present
     */
    default Optional<String> contentType() {
        return header(CONTENT_TYPE);
    }

    /**
     * Return {@code true} if the response is chunked.
     *
     * @return {@code true} if the response is chunked
     */
    default boolean chunked() {
        return HttpMessageSupport.chunked(headers());
    }
}
