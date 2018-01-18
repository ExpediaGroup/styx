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
package com.hotels.styx.proxy.backends;

import com.hotels.styx.Environment;
import com.hotels.styx.api.client.Origin;
import com.hotels.styx.client.OriginsInventory;
import com.hotels.styx.client.applications.BackendService;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class OriginInventoryFactoryTest {

    private BackendService backendService = new BackendService.Builder()
            .origins(Origin.newOriginBuilder ("localhost", 8080).build())
            .build();

    @Test
    public void createsOriginInventory() {
        Environment environment = new Environment.Builder().build();

        OriginInventoryFactory factory = new OriginInventoryFactory(environment, 10);

        OriginsInventory inventory = factory.newInventory(backendService);

        assertThat(inventory, Matchers.instanceOf(OriginsInventory.class));
    }
}