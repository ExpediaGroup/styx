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

import rx.Observable;

/**
 * Conversions between StyxObservable and rx.Observable types.
 */
public final class StyxInternalObservables {
    private StyxInternalObservables() {
    }

    public static <T> Observable<T> toRxObservable(StyxObservable<T> observable) {
        return ((StyxCoreObservable<T>) observable).delegate();
    }

    public static <T> StyxObservable<T> fromRxObservable(Observable<T> observable) {
        return new StyxCoreObservable<>(observable);
    }
}
