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
package com.hotels.styx.api.extension.service.spi;

import com.hotels.styx.api.FullHttpResponse;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.extension.service.spi.AbstractStyxService;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.hotels.styx.api.MockContext.MOCK_CONTEXT;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.FAILED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.RUNNING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STARTING;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPED;
import static com.hotels.styx.api.extension.service.spi.StyxServiceStatus.STOPPING;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AbstractStyxServiceTest {

    private final HttpRequest get = HttpRequest.get("/").build();

    @Test
    public void exposesNameAndStatusViaAdminInterface() throws ExecutionException, InterruptedException {
        DerivedStyxService service = new DerivedStyxService("derived-service", new CompletableFuture<>());

        FullHttpResponse response =
                service.adminInterfaceHandlers().get("status").handle(get, MOCK_CONTEXT)
                        .flatMap(r -> r.toFullResponse(1024))
                .asCompletableFuture()
                .get();

        assertThat(response.bodyAs(UTF_8), is("{ name: \"derived-service\" status: \"CREATED\" }"));
    }

    @Test
    public void inStartingStateWhenStartIsCalled() {
        DerivedStyxService service = new DerivedStyxService("derived-service", new CompletableFuture<>());

        CompletableFuture<Void> started = service.start();

        assertThat(service.status(), is(STARTING));
        assertThat(started.isDone(), is(false));
    }

    @Test
    public void inStartedStateWhenStartupCompletes() {
        CompletableFuture<Void> subclassStarted = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", subclassStarted);

        CompletableFuture<Void> started = service.start();

        assertThat(service.status(), is(STARTING));
        assertThat(started.isDone(), is(false));

        subclassStarted.complete(null);

        assertThat(service.status(), is(RUNNING));
        assertThat(started.isDone(), is(true));
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Start called in STARTING state")
    public void throwsExceptionFor2ndCallToStart() {
        CompletableFuture<Void> subclassStarted = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", subclassStarted);

        CompletableFuture<Void> started1st = service.start();

        assertThat(service.status(), is(STARTING));
        assertThat(started1st.isDone(), is(false));

        try {
            service.start();
        } catch (Exception e) {
            assertThat(service.status(), is(STARTING));
            assertThat(started1st.isDone(), is(false));
            throw e;
        }
    }

    @Test
    public void inFailedStateAfterSubclassStartupFailure() {
        CompletableFuture<Void> subclassStarted = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", subclassStarted);

        CompletableFuture<Void> started = service.start();

        assertThat(service.status(), is(STARTING));
        assertThat(started.isDone(), is(false));

        subclassStarted.completeExceptionally(new RuntimeException("Derived failed to start"));

        assertThat(service.status(), is(FAILED));
        assertThat(started.isCompletedExceptionally(), is(true));
    }

    @Test
    public void inStoppingStateAfterStopIsCalled() {
        DerivedStyxService service = new DerivedStyxService("derived-service", completedFuture(null), new CompletableFuture<>());

        CompletableFuture<Void> started = service.start();
        assertThat(service.status(), is(RUNNING));
        assertThat(started.isDone(), is(true));

        CompletableFuture<Void> stopped = service.stop();
        assertThat(service.status(), is(STOPPING));
        assertThat(stopped.isDone(), is(false));
    }

    @Test
    public void inStoppedStateAfterSubClassHasStopped() {
        CompletableFuture<Void> subclassStopped = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", completedFuture(null), subclassStopped);

        CompletableFuture<Void> started = service.start();
        assertThat(service.status(), is(RUNNING));
        assertThat(started.isDone(), is(true));

        CompletableFuture<Void> stopped = service.stop();
        assertThat(service.status(), is(STOPPING));
        assertThat(stopped.isDone(), is(false));

        subclassStopped.complete(null);
        assertThat(service.status(), is(STOPPED));
        assertThat(stopped.isDone(), is(true));
    }

    @Test
    public void inFailedStateWhenSubclassFailsToStop() {
        CompletableFuture<Void> subclassStopped = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", completedFuture(null), subclassStopped);

        CompletableFuture<Void> started = service.start();
        assertThat(service.status(), is(RUNNING));
        assertThat(started.isDone(), is(true));

        CompletableFuture<Void> stopped = service.stop();
        assertThat(service.status(), is(STOPPING));
        assertThat(stopped.isDone(), is(false));

        subclassStopped.completeExceptionally(new RuntimeException("derived service failed to stop"));
        assertThat(service.status(), is(FAILED));
        assertThat(stopped.isCompletedExceptionally(), is(true));
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Stop called in FAILED state")
    public void throwsExceptionWhenStopIsCalledInFailedState() {
        CompletableFuture<Void> subclassStopped = new CompletableFuture<>();
        DerivedStyxService service = new DerivedStyxService("derived-service", completedFuture(null), subclassStopped);

        CompletableFuture<Void> started = service.start();
        assertThat(service.status(), is(RUNNING));
        assertThat(started.isDone(), is(true));

        CompletableFuture<Void> stopped = service.stop();
        assertThat(service.status(), is(STOPPING));
        assertThat(stopped.isDone(), is(false));

        subclassStopped.completeExceptionally(new RuntimeException("derived service failed to stop"));
        assertThat(service.status(), is(FAILED));
        assertThat(stopped.isCompletedExceptionally(), is(true));

        service.stop();
    }


    static class DerivedStyxService extends AbstractStyxService {
        private final CompletableFuture<Void> startFuture;
        private final CompletableFuture<Void> stopFuture;

        DerivedStyxService(String name, CompletableFuture startFuture) {
            this(name, startFuture, completedFuture(null));
        }

        DerivedStyxService(String name, CompletableFuture<Void> startFuture, CompletableFuture<Void> stopFuture) {
            super(name);
            this.startFuture = startFuture;
            this.stopFuture = stopFuture;
        }

        @Override
        protected CompletableFuture<Void> startService() {
            return startFuture;
        }

        @Override
        protected CompletableFuture<Void> stopService() {
            return stopFuture;
        }
    }

}