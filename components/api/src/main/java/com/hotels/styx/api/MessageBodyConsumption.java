/*
  Copyright (C) 2013-2019 Expedia Inc.

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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

final class MessageBodyConsumption {
    private MessageBodyConsumption() {
    }

    static <T extends LiveHttpMessage> Eventual<T> consume(T message, Class<T> type) {
        // Not strictly necessary, but we're playing with generics and unchecked casting here,
        // so this will probably help us if any bugs appear due to misuse of this method.
        if (!type.isInstance(message)) {
            throw new IllegalArgumentException("Incorrect type: " + type);
        }

        // Note: since the ByteStream will have zero elements, we can ignore the original element type of body().drop().
        Publisher typeErased = message.body().drop();
        Mono<T> replaceWithOriginalMessage = Mono.from(typeErased).concatWith(Mono.just(message)).single();

        return new Eventual<>(replaceWithOriginalMessage);
    }
}
