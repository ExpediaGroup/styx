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
package com.hotels.styx.support.api;

import com.hotels.styx.api.StyxObservable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class HttpMessageBodiesTest {

    private static StyxObservable<ByteBuf> byteBufObservable(String... strings) {
        return StyxObservable.from(stream(strings)
                .map(HttpMessageBodiesTest::buf)
                .collect(toList()));
    }

    private static ByteBuf buf(CharSequence charSequence) {
        return Unpooled.copiedBuffer(charSequence, UTF_8);
    }
}