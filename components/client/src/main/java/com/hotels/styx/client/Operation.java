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
package com.hotels.styx.client;

import rx.Observable;

/**
 * An operation that can be executed on a connection and that returns an observable to provide the result.
 * <p/>
 * Has an associated application ID.
 *
 * @param <C> connection type
 * @param <R> result type
 */
public interface Operation<C extends Connection, R> {

    /**
     * Executes the operation.
     *
     * @param connection connection to operate on
     * @return an observable to provide the result
     */
    Observable<R> execute(C connection);
}
