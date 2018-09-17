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
package com.hotels.styx.server;

import com.hotels.styx.api.HttpInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap backed implementation of HttpInterceptor.Context.
 */
public final class HttpInterceptorContext implements HttpInterceptor.Context {
    private final Map<String, Object> context = new ConcurrentHashMap<>();

    private final boolean secure;
    private final InetSocketAddress clientAddress;

    /**
     * Construct a new instance.
     *
     * @param secure true if the request was received via SSL
     * @param clientAddress address that request came from, or null if not-applicable
     */
    public HttpInterceptorContext(boolean secure, InetSocketAddress clientAddress) {
        this.secure = secure;
        this.clientAddress = clientAddress; // intentionally nullable
    }

    /**
     * Construct a new instance.
     *
     * @param clientAddress address that request came from, or null if not-applicable
     */
    public HttpInterceptorContext(InetSocketAddress clientAddress) {
        this(false, clientAddress);
    }

    /**
     * Construct a new instance.
     *
     * @param secure true if the request was received via SSL
     */
    public HttpInterceptorContext(boolean secure) {
        this(secure, null);
    }

    /**
     * Construct a new instance.
     */
    public HttpInterceptorContext() {
        this(false, null);
    }

    // TODO deprecate
    public static HttpInterceptor.Context create() {
        return new HttpInterceptorContext();
    }

    @Override
    public void add(String key, Object value) {
        context.put(key, value);
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return (T) context.get(key);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public Optional<InetSocketAddress> clientAddress() {
        return Optional.ofNullable(clientAddress);
    }
}
