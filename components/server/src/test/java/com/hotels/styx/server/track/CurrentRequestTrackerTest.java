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

    CurrentRequestTracker tracker = new CurrentRequestTracker();

    @BeforeMethod
    public void setUp() {
        tracker = new CurrentRequestTracker();
    }

    @Test
    public void testTrackRequest() {
        tracker.trackRequest(req1);
        assertThat(tracker.currentRequests().iterator().next().request(), is(req1.toString()));
    }

    @Test
    public void testChangeWorkingThread() {
        Thread.currentThread().setName("thread-1");
        tracker.trackRequest(req1);
        assertThat("thread-1", is(tracker.currentRequests().iterator().next().currentThread().getName()));
        Thread.currentThread().setName("thread-2");
        tracker.trackRequest(req1);
        assertThat("thread-2", is(tracker.currentRequests().iterator().next().currentThread().getName()));
    }

    @Test
    public void testTrackingSameReqMultipleTimesWillNotGenerateMultipleEntries() {
        assertThat(tracker.currentRequests().size(), is(0));
        tracker.trackRequest(req1);
        tracker.trackRequest(req1);
        tracker.trackRequest(req1);
        tracker.trackRequest(req1);
        assertThat(tracker.currentRequests().size(), is(1));
        assertThat(tracker.currentRequests().iterator().next().request(), is(req1.toString()));
    }

    @Test
    public void testEndTrack() {
        tracker.trackRequest(req1);
        assertThat(tracker.currentRequests().size(), is(1));
        assertThat(tracker.currentRequests().iterator().next().request(), is(req1.toString()));
        tracker.endTrack(req1);
        assertThat(tracker.currentRequests().size(), is(0));
    }

    @Test
    public void testEndTrackWillEffectOneRequest() {
        tracker.trackRequest(req1);
        tracker.trackRequest(req2);
        assertThat(tracker.currentRequests().size(), is(2));
        tracker.endTrack(req1);
        assertThat(tracker.currentRequests().size(), is(1));
    }

    @Test
    public void testEndTrackWillEffectTheCorrectRequest() {
        tracker.trackRequest(req1);
        tracker.trackRequest(req2);
        tracker.endTrack(req1);
        assertThat(tracker.currentRequests().iterator().next().request(), is(req2.toString()));
    }
}
