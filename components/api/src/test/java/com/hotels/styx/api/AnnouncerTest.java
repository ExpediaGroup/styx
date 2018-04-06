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
package com.hotels.styx.api;

import org.testng.annotations.Test;

import java.util.EventListener;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class AnnouncerTest {
    @Test
    public void createsAnnouncerForType() {
        Announcer<AnnouncerTestListener> announcer = Announcer.to(AnnouncerTestListener.class);
        assertThat(announcer, is(notNullValue()));
    }

    @Test
    public void announcesToRegisteredListener() {
        // Given announcer:
        Announcer<AnnouncerTestListener> announcer = Announcer.to(AnnouncerTestListener.class);
        assertThat(announcer, is(notNullValue()));

        // Register listeners:
        TestObserver observer = new TestObserver();
        announcer.addListener(observer);

        // Announce:
        announcer.announce().announceTest("Hello Observers");

        // Then:
        assertThat(observer.message(), is("Hello Observers"));
    }

    @Test
    public void announcesToMultipleRegisteredListeners() {
        // Given announcer:
        Announcer<AnnouncerTestListener> announcer = Announcer.to(AnnouncerTestListener.class);
        assertThat(announcer, is(notNullValue()));

        // Register listeners:
        TestObserver observer1 = new TestObserver();
        TestObserver observer2 = new TestObserver();
        TestObserver observer3 = new TestObserver();
        announcer.addListener(observer1);
        announcer.addListener(observer2);
        announcer.addListener(observer3);

        // Announce:
        announcer.announce().announceTest("Hello Observers");

        // Then:
        assertThat(observer1.message(), is("Hello Observers"));
        assertThat(observer2.message(), is("Hello Observers"));
        assertThat(observer3.message(), is("Hello Observers"));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void throwsExceptionWhenAttemptingToRegisterNullListener() {
        // Given announcer:
        Announcer<AnnouncerTestListener> announcer = Announcer.to(AnnouncerTestListener.class);
        assertThat(announcer, is(notNullValue()));

        // Register listeners:
        announcer.addListener(null);
    }

    @Test
    public void doesNotThrowExceptionIfNoListenersAreRegisteredWhenAnnouncing() {
        // Given announcer:
        Announcer<AnnouncerTestListener> announcer = Announcer.to(AnnouncerTestListener.class);
        assertThat(announcer, is(notNullValue()));

        // Don't register listeners

        // Announce:
        announcer.announce().announceTest("Hello Observers");

        // Announcer should not throw exceptions.
    }

    interface AnnouncerTestListener extends EventListener {
        void announceTest(String message);
    }

    class TestObserver implements AnnouncerTestListener {
        private String message = null;

        @Override
        public void announceTest(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }
}
