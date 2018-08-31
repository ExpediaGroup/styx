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

import java.util.Objects;

import static com.google.common.base.Objects.toStringHelper;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

/**
 * Abstract parent class for origin-related commands. Takes two parameters: application ID and origin ID.
 */
abstract class OriginCommand {
    private final Id appId;
    private final Id originId;

    public OriginCommand(Id appId, Id originId) {
        this.appId = requireNonNull(appId);
        this.originId = requireNonNull(originId);
    }

    public Id appId() {
        return appId;
    }

    public Id originId() {
        return this.originId;
    }

    public boolean forApp(Id appId) {
        return this.appId.equals(appId);
    }

    @Override
    public int hashCode() {
        return hash(appId, originId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        OriginCommand other = (OriginCommand) obj;
        return Objects.equals(this.appId, other.appId) && Objects.equals(this.originId, other.originId);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("appId", appId)
                .add("originId", originId)
                .toString();
    }
}
