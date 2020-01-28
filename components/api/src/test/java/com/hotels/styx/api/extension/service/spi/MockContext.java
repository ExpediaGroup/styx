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
package com.hotels.styx.api.extension.service.spi;

import com.hotels.styx.api.HttpInterceptor;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.Executor;

class MockContext implements HttpInterceptor.Context {
    static final HttpInterceptor.Context MOCK_CONTEXT = new MockContext();

    @Override
    public void add(String key, Object value) {

    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public Optional<InetSocketAddress> clientAddress() {
        return Optional.empty();
    }

    @Override
    public Executor executor() {
        return Runnable::run;
    }
}
