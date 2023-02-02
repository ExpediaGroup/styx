/*
  Copyright (C) 2013-2023 Expedia Inc.

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
package com.hotels.styx.server

import com.fasterxml.uuid.EthernetAddress
import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.NoArgGenerator

/**
 * Useful unique id suppliers.
 */
object UniqueIdSuppliers {
    private val TIME_BASED_GENERATOR: NoArgGenerator = Generators.timeBasedGenerator(EthernetAddress.fromInterface())

    /**
     * A unique ID supplier which uses a UUID Version One implementation.
     */
    @JvmField
    val UUID_VERSION_ONE_SUPPLIER = UniqueIdSupplier { TIME_BASED_GENERATOR.generate().toString() }

    /**
     * Returns a supplier whose `get()` method returns the `uniqueId` passed in.
     *
     * @return a new ID supplier
     */
    @JvmStatic
    fun fixedUniqueIdSupplier(uniqueId: String): UniqueIdSupplier = UniqueIdSupplier { uniqueId }
}
