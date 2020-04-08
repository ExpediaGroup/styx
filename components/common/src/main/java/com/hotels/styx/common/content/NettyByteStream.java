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
import io.netty.channel.EventLoop;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A publisher to wrap the FlowControllingHttpContentProducer FSM and perform subscription operations via a Netty EventLoop.
 */
public final class NettyByteStream implements Publisher<Buffer> {

    private final EventLoop eventLoop;
    private final FlowControllingHttpContentProducer contentProducer;

    public NettyByteStream(EventLoop eventLoop, FlowControllingHttpContentProducer contentProducer) {
        this.eventLoop = eventLoop;
        this.contentProducer = contentProducer;
    }

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        ByteBufToBufferSubscriber byteBufToBufferSubscriber = new ByteBufToBufferSubscriber(subscriber);
        eventLoop.submit(() -> contentProducer.onSubscribed(byteBufToBufferSubscriber));
        byteBufToBufferSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                eventLoop.submit(() -> contentProducer.request(n));
            }

            @Override
            public void cancel() {
                eventLoop.submit(contentProducer::unsubscribe);
            }
        });
    }
}
