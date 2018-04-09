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

import io.netty.util.ReferenceCountUtil;
import rx.Observable;
import rx.Subscriber;

/**
 * Requests as many event from upstream as possible (i.e. disables backpressure).
 *
 * @param <E> event type
 */
public final class FlowControlDisableOperator<E> implements Observable.Operator<E, E> {
    public static <T> FlowControlDisableOperator<T> disableFlowControl() {
        return new FlowControlDisableOperator<T>();
    }

    private FlowControlDisableOperator() {
    }

    @Override
    public Subscriber<? super E> call(Subscriber<? super E> downstream) {
        return new Subscriber<E>() {
            @Override
            public void onStart() {
                request(1);
            }

            @Override
            public void onCompleted() {
                downstream.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                downstream.onError(e);
            }

            @Override
            public void onNext(E e) {
                if (downstream.isUnsubscribed()) {
                    ReferenceCountUtil.release(e);
                } else {
                    downstream.onNext(e);
                }

                request(1);
            }
        };
    }
}
