/*
  Copyright (C) 2013-2024 Expedia Inc.

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

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * An interceptor can interact with a request before it is passed to the handler and/or the response from the handler
 * before it is returned.
 * <p>
 * The interceptor can simply log/record data, or it could modify the request/response.
 */
public interface HttpInterceptor {
    /**
     * Context. Provides application-wide data to the interceptor.
     */
    interface Context {
        /**
         * Add key-value pair to the context.
         *
         * @param key   key
         * @param value value
         */
        void add(String key, Object value);

        /**
         * Retrieve a value from the context, converted to a given class.
         *
         * @param key   key
         * @param clazz class of value
         * @param <T>   class of value
         * @return value
         * @deprecated  use getIfAvailable instead
         */
        @Deprecated
        <T> T get(String key, Class<T> clazz);

        default <T> Optional<T> getIfAvailable(String key, Class<T> clazz) {
            return Optional.ofNullable(get(key, clazz));
        }

        /**
         * Returns true if this request was received over a secure connection.
         *
         * @return returns true if this request was received over a secure connection
         */
        boolean isSecure();

        /**
         * Provides address of the client that sent the request to Styx.
         *
         * @return address of the client that sent the request to Styx
         */
        Optional<InetSocketAddress> clientAddress();

        /**
         * Returns the netty executor which started handling the current request.
         *
         * @return returns the netty executor which started handling the current request
         */
        Executor executor();

        /**
         * Removes all the stored items from this context.
         */
        void clear();
    }

    /**
     * Coordinates the propagation of request and response through a chain of interceptors and back.
     */
    interface Chain {
        /**
         * Returns the {@link Context}. Default implementation returns null.
         *
         * @return the context
         */
        default Context context() {
            return null;
        }

        /**
         * Propagate to the next interceptor, or to the handler if there are no more interceptors.
         *
         * @param request request to propagate
         * @return observable that will provide the response
         */
        Eventual<LiveHttpResponse> proceed(LiveHttpRequest request);

    }

    /**
     * Executes the interceptor's action. The interceptor is responsible for continuing execution of the interceptor
     * stack with the {@link HttpInterceptor.Chain}.
     *
     * @param request HTTP request
     * @param chain   chain
     * @return observable that will provide the response
     */
    Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain);

}
