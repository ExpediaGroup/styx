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
package com.hotels.styx.client.origincommands;

import com.hotels.styx.api.Id;

/**
 * An event that signals to {@link com.hotels.styx.client.OriginsInventory} to enable a previously disabled origin to start accepting traffic.
 */
public final class EnableOrigin extends OriginCommand {
    /**
     * Constructs an instance.
     *
     * @param appId application the origin belongs to
     * @param originId origin ID
     */
    public EnableOrigin(Id appId, Id originId) {
        super(appId, originId);
    }
}
