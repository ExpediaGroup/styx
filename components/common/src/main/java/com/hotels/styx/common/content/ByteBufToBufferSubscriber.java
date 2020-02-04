/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.common.content;

import com.hotels.styx.api.Buffer;
import com.hotels.styx.api.Buffers;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A subscriber that takes ByteBufs and converts them to Buffers.
 */
public class ByteBufToBufferSubscriber implements Subscriber<ByteBuf> {

    private final Subscriber<? super Buffer> actual;

    public ByteBufToBufferSubscriber(Subscriber<? super Buffer> actual) {
        this.actual = actual;
    }

    @Override
    public void onSubscribe(Subscription s) {
        actual.onSubscribe(s);
    }

    @Override
    public void onNext(ByteBuf byteBuf) {
        actual.onNext(Buffers.fromByteBuf(byteBuf));
    }

    @Override
    public void onError(Throwable t) {
        actual.onError(t);
    }

    @Override
    public void onComplete() {
        actual.onComplete();
    }
}
