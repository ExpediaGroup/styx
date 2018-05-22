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
package com.hotels.styx.configstore;

import com.hotels.styx.support.Latch;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.concurrent.atomic.AtomicReference;

import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ConfigStoreTest {
    private ConfigStore configStore;

    @BeforeMethod
    public void setUp() {
        configStore = new ConfigStore();
    }

    @Test
    public void getsWhatWasSet() {
        configStore.set("foo", "bar");
        assertThat(configStore.get("foo", String.class), isValue("bar"));
    }

    @Test
    public void listenersReceiveUpdatesWhenValuesChange() {
        Latch sync = new Latch(1);
        AtomicReference<String> update = new AtomicReference<>();

        configStore.watch("foo", String.class)
                .subscribe(value -> {
                    update.set(value);
                    sync.countDown();
                });

        configStore.set("foo", "bar");

        sync.await(1, SECONDS);

        assertThat(update.get(), is("bar"));
    }

    @Test
    public void valueInConfigStoreIsConsistentWithListenersUpdate() {
        AtomicReference<String> valueInStore = new AtomicReference<>();
        AtomicReference<String> valueInUpdate = new AtomicReference<>();

        Latch sync = new Latch(1);

        configStore.watch("foo", String.class)
                .subscribe(value -> {
                    valueInStore.set(configStore.get("foo", String.class).orElse(null));
                    valueInUpdate.set(value);
                    sync.countDown();
                });

        configStore.set("foo", "bar");

        sync.await(1, SECONDS);

        assertThat(valueInStore.get(), is(valueInUpdate.get()));
    }

    // If this test fails it will cause a deadlock, resulting in a latch timeout
    @Test
    public void listensOnSeparateThread() {
        Latch unlockedByTestThread = new Latch(1);
        Latch unlockedBySubscribeThread = new Latch(1);

        configStore.watch("foo", String.class)
                .subscribe(value -> {
                    unlockedByTestThread.await(1, SECONDS);
                    unlockedBySubscribeThread.countDown();
                });

        configStore.set("foo", "bar");

        unlockedByTestThread.countDown();
        unlockedBySubscribeThread.await(2, SECONDS);
    }

    // If this test fails it will cause a deadlock, resulting in a latch timeout
    @Test
    public void multipleListenersCanSubscribeSimultaneously() {
        Latch unlockedByListener1 = new Latch(1);
        Latch unlockedByListener2 = new Latch(1);

        Latch unlockedWhenBothFinish = new Latch(2);

        Observable<String> watch = configStore.watch("foo", String.class);

        // Listener 1
        watch.subscribe(value -> {
            unlockedByListener1.countDown();
            unlockedByListener2.await(1, SECONDS);
            unlockedWhenBothFinish.countDown();
        });

        // Listener 2
        watch.subscribe(value -> {
            unlockedByListener1.await(1, SECONDS);
            unlockedByListener2.countDown();
            unlockedWhenBothFinish.countDown();
        });

        configStore.set("foo", "bar");

        unlockedWhenBothFinish.await(5, SECONDS);
    }
}