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
import com.hotels.styx.common.QueueDrainingExecutor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.Executor;

/**
 * A publisher to wrap the FlowControllingHttpContentProducer FSM and perform subscription operations via a QueueDrainingExecutor.
 */
public final class QueueDrainingPublisher implements Publisher<Buffer> {

    private final Executor queueDrainingExecutor = new QueueDrainingExecutor();
    private final FlowControllingHttpContentProducer contentProducer;

    public QueueDrainingPublisher(FlowControllingHttpContentProducer contentProducer) {
        this.contentProducer = contentProducer;
    }

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {
        ByteBufToBufferSubscriber byteBufToBufferSubscriber = new ByteBufToBufferSubscriber(subscriber);
        queueDrainingExecutor.execute(() -> contentProducer.onSubscribed(byteBufToBufferSubscriber));
        byteBufToBufferSubscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                queueDrainingExecutor.execute(() -> contentProducer.request(n));
            }

            @Override
            public void cancel() {
                queueDrainingExecutor.execute(contentProducer::unsubscribe);
            }
        });
    }

}
