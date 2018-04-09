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
package com.hotels.styx.api.client;

import rx.Observable;

import java.util.function.Function;

/**
 * Provides connections to a function and handles close/return mechanisms.
 */
public interface ConnectionDestination {
    /**
     * Allows tasks to be performed using connections.
     *
     * The parameter of the task function will be a connection that is not in use by any other task.
     *
     * The return value of the task function is an observable that must publish onComplete or onError when the connection
     * is no longer needed, so that it may be closed or reused.
     *
     * @param task task function
     * @return result of task
     */
    <T> Observable<T> withConnection(Function<Connection, Observable<T>> task);

    /**
     * The origin that connections will be connected to.
     *
     * @return an origin
     */
    Origin getOrigin();

    /**
     * The settings for creating connections.
     *
     * @return connection
     */
    Connection.Settings settings();

    /**
     * Factory to build instances for origins.
     */
    interface Factory {
        /**
         * Create a new instance.
         *
         * @param origin origin connections will go to
         * @return new instance
         */
        ConnectionDestination create(Origin origin);
    }
}
