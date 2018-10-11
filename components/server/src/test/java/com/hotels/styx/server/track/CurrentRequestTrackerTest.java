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
package com.hotels.styx.server.track;

import static com.hotels.styx.api.LiveHttpRequest.get;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.hotels.styx.api.LiveHttpRequest;

public class CurrentRequestTrackerTest {

    LiveHttpRequest req1 = get("/requestId1").build();
    LiveHttpRequest req2 = get("/requestId2").build();

    @BeforeMethod
    public void setUp() {
        CurrentRequestTracker.INSTANCE.clear();
    }

    @Test
    public void testTrackRequest() {
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getRequest(), is(req1.toString()));
    }

    @Test
    public void testChangeWorkingThread() {
        Thread.currentThread().setName("thread-1");
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        assertThat("thread-1", is(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getCurrentThread().getName()));
        Thread.currentThread().setName("thread-2");
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        assertThat("thread-2", is(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getCurrentThread().getName()));
    }

    @Test
    public void testTrackingSameReqMultipleTimesWillNotGenerateMultipleEntries() {
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(0));
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(1));
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getRequest(), is(req1.toString()));
    }

    @Test
    public void testEndTrack() {
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(1));
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getRequest(), is(req1.toString()));
        CurrentRequestTracker.INSTANCE.endTrack(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(0));
    }

    @Test
    public void testEndTrackWillEffectOneRequest() {
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        CurrentRequestTracker.INSTANCE.trackRequest(req2);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(2));
        CurrentRequestTracker.INSTANCE.endTrack(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().size(), is(1));
    }

    @Test
    public void testEndTrackWillEffectTheCorrectRequest() {
        CurrentRequestTracker.INSTANCE.trackRequest(req1);
        CurrentRequestTracker.INSTANCE.trackRequest(req2);
        CurrentRequestTracker.INSTANCE.endTrack(req1);
        assertThat(CurrentRequestTracker.INSTANCE.getCurrentRequests().iterator().next().getRequest(), is(req2.toString()));
    }
}
