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
package com.hotels.styx.client;

import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;

/**
 * An object that can receive a notification when an origin is enabled or disabled from accepting traffic.
 */
public interface OriginsCommandsListener {
    /**
     * A signal that a disabled origin should start accepting traffic.
     *
     * @param enableOrigin command event
     */
    void onCommand(EnableOrigin enableOrigin);

    /**
     * A signal that an enabled origin should stop accepting traffic.
     *
     * @param disableOrigin command event
     */
    void onCommand(DisableOrigin disableOrigin);

    /**
     * A signal that the origins inventory should publish a new snapshot.
     *
     * @param getOriginsInventorySnapshot command event
     */
    void onCommand(GetOriginsInventorySnapshot getOriginsInventorySnapshot);
}
