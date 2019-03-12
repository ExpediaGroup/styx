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
package com.hotels.styx.api.configuration;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.extension.ActiveOrigins;

/**
 * A generic factory that can be implemented in order to facilitate creating objects whose type is not known
 * until read from configuration.
 *
 * @param <E> factory product type
 */
public interface ServiceFactory<E> {
    /**
     * Create an instance of the product.
     *
     * @param environment                 environment
     * @param serviceConfiguration configuration specific to the factory product
     * @return product instance
     */
    E create(Environment environment, Configuration serviceConfiguration);

    /**
     * Create an instance of the product.
     *
     * @param environment                 environment
     * @param serviceConfiguration configuration specific to the factory product
     * @param objects objects that service might depend on
     * @return product instance
     */
    default E create(Environment environment, Configuration serviceConfiguration, ActiveOrigins objects) {
        return create(environment, serviceConfiguration);
    }
}
