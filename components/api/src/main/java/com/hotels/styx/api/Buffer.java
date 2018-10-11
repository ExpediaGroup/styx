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

import java.nio.charset.Charset;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static java.util.Objects.requireNonNull;

/**
 * A byte buffer.
 *
 */
public final class Buffer {
    private final ByteBuf delegate;

    Buffer(ByteBuf byteBuf) {
        this.delegate = requireNonNull(byteBuf);
    }

    /**
     * Creates a new Buffer from {@link String} content with specified character encoding.
     *
     * @param content content
     * @param charset desired character encoding
     */
    public Buffer(String content, Charset charset) {
        this(copiedBuffer(content, charset));
    }

    /**
     * Returns a size of the Buffer in bytes.
     * @return a size in bytes
     */
    public int size() {
        return delegate.readableBytes();
    }

    /**
     * Returns buffer content as array of bytes.
     *
     * @return a byte array
     */
    public byte[] content() {
        byte[] bytes = new byte[delegate.readableBytes()];
        delegate.getBytes(delegate.readerIndex(), bytes);
        return bytes;
    }

    /**
     * The underlying Netty ByteBuf.
     *
     * @return a Netty ByteBuf object
     */
    ByteBuf delegate() {
        return delegate;
    }
}
