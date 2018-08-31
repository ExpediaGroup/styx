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
package com.hotels.styx.client.applications;

import com.hotels.styx.api.extension.Origin;
import org.testng.annotations.Test;

import static com.google.common.collect.Lists.newArrayList;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.checkThatOriginsAreDistinct;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class OriginTest {
    final Origin origin = newOriginBuilder("localhost", 9090).applicationId(id("webapp")).build();

    @Test
    public void buildsApplicationInfo() {
        assertThat(origin.applicationInfo(), is("WEBAPP-localhost:9090"));
    }

    @Test
    public void acceptsIfAllIdsAreDistinct() {
        Origin origin1 = newOriginBuilder("localhost", 8080).applicationId("webapp").id("origin-01").build();
        Origin origin2 = newOriginBuilder("localhost", 8081).applicationId("webapp").id("origin-02").build();

        checkThatOriginsAreDistinct(newArrayList(origin1, origin2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsIfThereAreDuplicateIds() {
        Origin origin1 = newOriginBuilder("localhost", 8080).applicationId("webapp").id("origin-01").build();
        Origin origin2 = newOriginBuilder("localhost", 8081).applicationId("webapp").id("origin-01").build();
        Origin origin3 = newOriginBuilder("localhost", 8082).applicationId("webapp").id("origin-03").build();

        checkThatOriginsAreDistinct(newArrayList(origin1, origin2, origin3));
    }

    @Test
    public void acceptsIfAllHostsAreDistinct() {
        Origin origin1 = newOriginBuilder("localhost", 8080).applicationId("webapp").id("origin-01").build();
        Origin origin2 = newOriginBuilder("localhost", 8081).applicationId("webapp").id("origin-02").build();

        checkThatOriginsAreDistinct(newArrayList(origin1, origin2));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsIfThereAreDuplicateHosts() {
        Origin origin1 = newOriginBuilder("localhost", 8080).applicationId("webapp").id("origin-01").build();
        Origin origin2 = newOriginBuilder("localhost", 8081).applicationId("webapp").id("origin-02").build();
        Origin origin3 = newOriginBuilder("localhost", 8081).applicationId("webapp").id("origin-03").build();

        checkThatOriginsAreDistinct(newArrayList(origin1, origin2, origin3));
    }
}