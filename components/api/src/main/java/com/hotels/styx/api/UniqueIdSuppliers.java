/**
 * Copyright (C) 2013-2017 Expedia Inc.
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
package com.hotels.styx.api;

import com.eaio.uuid.UUID;

/**
 * Useful unique id suppliers.
 */
public final class UniqueIdSuppliers {
    /**
     * A unique ID supplier which uses a UUID Version One implementation.
     */
    public static final UniqueIdSupplier UUID_VERSION_ONE_SUPPLIER = () -> new UUID().toString();

    /**
     * Returns a supplier whose {@code get()} method returns the {@code uniqueId} passed in.
     *
     * @return a new ID supplier
     */
    public static UniqueIdSupplier fixedUniqueIdSupplier(String uniqueId) {
        return () -> uniqueId;
    }

    private UniqueIdSuppliers() {
    }
}
