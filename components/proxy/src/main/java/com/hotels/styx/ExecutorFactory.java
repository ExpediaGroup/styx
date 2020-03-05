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
package com.hotels.styx;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A generic factory that can be implemented to create executor objects whose type is not known
 * until read from configuration.
 *
 */
public interface ExecutorFactory {
    /**
     * Create an executor instance.
     *
     * @param name                 Executor name
     * @param configuration        Styx executor configuration
     *
     * @return Styx service instance
     */
    NettyExecutor create(String name, JsonNode configuration);
}
