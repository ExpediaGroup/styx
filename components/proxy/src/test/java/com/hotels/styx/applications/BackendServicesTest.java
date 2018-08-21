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
package com.hotels.styx.applications;

import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.service.BackendService;
import org.testng.annotations.Test;

import static com.google.common.collect.Iterables.getFirst;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BackendServicesTest {
    @Test
    public void derivesApplicationIdsOnOriginsWhenUsingYamlConstructor() {
        BackendService before = BackendService.newBackendServiceBuilder()
                .id("generic_app")
                .origins(newOriginBuilder("localhost", 8080).id("origin1").build())
                .build();

        BackendServices backendServices = new BackendServices(singletonList(before));
        Origin origin = getFirst(backendServices.first().origins(), null);

        assertThat(origin.applicationId(), is(id("generic_app")));
    }
}
