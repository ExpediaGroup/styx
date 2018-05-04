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
package com.hotels.styx.support.dns;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import sun.net.spi.nameservice.NameService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MockNameServiceTest {

    InetAddress[] nsLookupResponse;

    @BeforeMethod
    public void setUp() throws Exception {
        nsLookupResponse =  new InetAddress[] {InetAddress.getByName("localhost")};
    }

    @Test
    public void configuresIndependentlyForSeparateThreads() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        MockNameService nameService = new MockNameService();

        CompletableFuture<Boolean> mainTask = CompletableFuture.supplyAsync(() -> {
            NameService delegate = mockNameService();
            nameService.setDelegate(delegate);

            try {
                nameService.lookupAllHostAddr("localhost");
                waitAt(barrier);

                // Wait for the other thread to overwrite the delegate!

                waitAt(barrier);
                nameService.lookupAllHostAddr("localhost");

                verify(delegate, times(2)).lookupAllHostAddr(eq("localhost"));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            nameService.unset();
            return true;
        });

        CompletableFuture.runAsync(() -> {
            waitAt(barrier);
            NameService delegate = mockNameService();
            nameService.setDelegate(delegate);
            waitAt(barrier);
        });

        assertThat(mainTask.get(), is(true));
    }

    private void waitAt(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }

    private NameService mockNameService() {
        try {
            NameService ns = mock(NameService.class);
            when(ns.lookupAllHostAddr(eq("localhost")))
                    .thenReturn(nsLookupResponse)
                    .thenReturn(nsLookupResponse);
            return ns;
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}