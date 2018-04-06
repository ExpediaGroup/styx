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
package com.hotels.styx.admin.tasks;

import com.hotels.styx.api.client.Connection;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.Origin;
import rx.Observable;

/**
 * Stub implementation of a ConnectionPool.
 */
public class StubConnectionPool implements ConnectionPool {
    private final Origin origin;

    public StubConnectionPool(Origin origin) {
        this.origin = origin;
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Observable<Connection> borrowConnection() {
        return null;
    }

    @Override
    public boolean returnConnection(Connection connection) {
        return false;
    }

    @Override
    public boolean closeConnection(Connection connection) {
        return false;
    }

    @Override
    public boolean isExhausted() {
        return false;
    }

    @Override
    public Stats stats() {
        return null;
    }

    @Override
    public Settings settings() {
        return null;
    }
}
