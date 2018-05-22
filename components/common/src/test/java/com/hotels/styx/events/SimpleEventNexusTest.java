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
package com.hotels.styx.events;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.observers.TestSubscriber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleEventNexusTest {
    private TestSubscriber<Event> subscriber;
    private SimpleEventNexus eventNexus;

    @BeforeMethod
    public void setUp() {
        subscriber = new TestSubscriber<>();
        eventNexus = new SimpleEventNexus();
    }

    @Test
    public void canListenToExactEvents() {
        eventNexus.events("foo.bar").subscribe(subscriber);

        eventNexus.publish("foo.bar", "myEvent");

        subscriber.assertValue(new Event("foo.bar", "myEvent"));
    }

    @Test
    public void canListenToEventsStartingWithPrefix() {
        eventNexus.events("foo").subscribe(subscriber);

        eventNexus.publish("foo.bar", "myEvent");

        subscriber.assertValue(new Event("foo.bar", "myEvent"));
    }

    @Test
    public void doesNotConsiderNamesParentsUnlessSeparatedByDot() {
        eventNexus.events("foo").subscribe(subscriber);

        eventNexus.publish("foobar", "myEvent");

        subscriber.assertNoValues();
    }

    @Test
    public void canTellIfStringIsChildOfParent() {
        assertThat(SimpleEventNexus.equalOrChild("foo", "foo"), is(true));
        assertThat(SimpleEventNexus.equalOrChild("foo.bar", "foo"), is(true));
        assertThat(SimpleEventNexus.equalOrChild("foo.bar", "foo.bar"), is(true));
        assertThat(SimpleEventNexus.equalOrChild("foo.bar.baz", "foo.bar"), is(true));

        assertThat(SimpleEventNexus.equalOrChild("foobar", "foo"), is(false));
    }

    @Test
    public void eventNamesAreCaseInsensitive() {
        eventNexus.events("foo.bar").subscribe(subscriber);

        eventNexus.publish("Foo.BAR", "myEvent");

        subscriber.assertValue(new Event("foo.bar", "myEvent"));
    }

    @Test
    public void canPublishMultipleEventsWithSameName() {
        eventNexus.events("foo").subscribe(subscriber);

        eventNexus.publish("foo.bar", "myEvent1");
        eventNexus.publish("foo.bar", "myEvent2");

        subscriber.assertValues(
                new Event("foo.bar", "myEvent1"),
                new Event("foo.bar", "myEvent2"));
    }

    @Test
    public void multipleListenersCanReceiveEvents() {
        TestSubscriber<Event> subscriber1 = new TestSubscriber<>();
        TestSubscriber<Event> subscriber2 = new TestSubscriber<>();

        eventNexus.events("foo").subscribe(subscriber1);
        eventNexus.events("foo").subscribe(subscriber2);

        eventNexus.publish("foo.bar", "myEvent1");
        eventNexus.publish("foo.bar", "myEvent2");

        subscriber1.assertValues(
                new Event("foo.bar", "myEvent1"),
                new Event("foo.bar", "myEvent2"));

        subscriber2.assertValues(
                new Event("foo.bar", "myEvent1"),
                new Event("foo.bar", "myEvent2"));
    }

    @Test
    public void eventsCanTriggerOtherEvents() {
        eventNexus.events("bar").subscribe(subscriber);

        eventNexus.events("foo").subscribe(event ->
                eventNexus.publish("bar", "Received!"));

        eventNexus.publish("foo", "SomeEvent");

        subscriber.assertValue(new Event("bar", "Received!"));
    }
}
