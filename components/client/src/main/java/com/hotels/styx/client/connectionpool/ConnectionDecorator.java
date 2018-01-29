/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client.connectionpool;

import com.hotels.styx.api.client.Connection;

/**
 * Provides method used for a connection decorating.
 */
interface ConnectionDecorator {

    /**
     * Used for a connection decoration to provide extended logic.
     * @param connection instance to be decorated.
     * @return decorated connection
     */
    Connection decorate(Connection connection);

    /**
     * Creates a {@link ConnectionDecorator} that returns the same connections without alterations.
     * @return same connection passed as an argument.
     */
    static ConnectionDecorator identityDecorator() {
        return connection -> connection;
    }
}
