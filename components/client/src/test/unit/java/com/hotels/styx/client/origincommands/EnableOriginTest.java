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
import com.hotels.styx.support.EqualsTester;
import org.testng.annotations.Test;

import static com.hotels.styx.api.Id.GENERIC_APP;
import static com.hotels.styx.api.Id.id;

public class EnableOriginTest {
    @Test
    public void checkEquality() {
        new EqualsTester()
                .addEqualityGroup(new EnableOrigin(id("same"), id("same")), new EnableOrigin(id("same"), id("same")))
                .addEqualityGroup(enableOrigin(id("same")), enableOrigin(id("same")))
                .testEquals();
    }

    private static EnableOrigin enableOrigin(Id originId) {
        return new EnableOrigin(GENERIC_APP, originId);
    }
}
