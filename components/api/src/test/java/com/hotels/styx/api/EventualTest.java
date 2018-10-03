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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import rx.subjects.PublishSubject;

import static rx.RxReactiveStreams.toPublisher;

public class EventualTest {

    @Test
    public void testFromRxObservable() {
        PublishSubject<String> subject = PublishSubject.create();
        Eventual<String> eventual = new Eventual<>(Mono.from(toPublisher(subject)));

        eventual.subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                System.out.println("onSubscribe");
                subscription.request(10);
            }

            @Override
            public void onNext(String s) {
                System.out.println("onNext " + s);
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("onError");
            }

            @Override
            public void onComplete() {
                System.out.println("onComplete");
            }
        });

        subject.onNext("hey");
//        subject.onCompleted();
    }
}