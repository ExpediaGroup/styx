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

public class Buffer {
    private final ByteBuf delegate;

    public Buffer(ByteBuf byteBuf) {
        this.delegate = requireNonNull(byteBuf);
    }

    public Buffer(String content, Charset charset) {
        this(copiedBuffer(content, charset));
    }

    public int size() {
        return delegate.readableBytes();
    }

    public byte[] content() {
        byte[] bytes = new byte[delegate.readableBytes()];
        delegate.getBytes(delegate.readerIndex(), bytes);
        return bytes;
    }

    public ByteBuf delegate() {
        return delegate;
    }
}
