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
package com.hotels.styx.common.io;

import com.hotels.styx.api.Resource;

/**
 * An index for the all types of {@link com.hotels.styx.api.Resource}.
 */
public interface ResourceIndex {

    /**
     * Returns all the resources in the given {@code path} with names ending with {@code suffix}.
     *
     * @param path a resource path
     * @param suffix a suffix
     * @return resources
     */
    Iterable<Resource> list(String path, String suffix);
}
