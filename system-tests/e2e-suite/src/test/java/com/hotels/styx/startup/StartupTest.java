/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.startup;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.testapi.StyxServer;
import org.slf4j.Logger;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.hotels.styx.api.HttpRequest.get;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.slf4j.LoggerFactory.getLogger;
import static org.testng.Assert.fail;

public class StartupTest {
    private static final Logger LOGGER = getLogger(StartupTest.class);

    private StyxServer server;

    @Test
    public void adminServerIsAvailableBeforeProxyServerCompletes() throws InterruptedException, TimeoutException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(1);

        server = new StyxServer.Builder()
                .addPluginFactory("plug", new SlowToCreatePlugin.Factory(latch), null)
                .startAsync();

        int adminPort = waitForAdminPort();

        StyxHttpClient client = new StyxHttpClient.Builder()
                .build();

        HttpResponse livenessResponse = client.send(get("http://localhost:" + adminPort + "/admin/ping").build()).get(1, SECONDS);
        assertThat(livenessResponse.status(), is(OK));
        assertThat(livenessResponse.bodyAs(UTF_8), is("pong"));

        HttpResponse readinessResponse = client.send(get("http://localhost:" + adminPort + "/admin/startup").build()).get(1, SECONDS);
        assertThat(readinessResponse.status(), is(OK));
        assertThat(readinessResponse.bodyAs(UTF_8), containsString("\"http\" : \"incomplete\""));

        latch.countDown();

        eventually(3, SECONDS, () -> {
            HttpResponse readinessResponse2 = client.send(get("http://localhost:" + adminPort + "/admin/startup").build()).get(1, SECONDS);
            assertThat(readinessResponse2.status(), is(OK));
            assertThat(readinessResponse2.bodyAs(UTF_8), containsString("\"http\" : \"complete\""));
        });
    }

    private int waitForAdminPort() throws InterruptedException {
        while (throwsException(() -> server.adminPort())) {
            sleep(100);
        }

        return server.adminPort();
    }

    private static boolean throwsException(Runnable2 runnable2) {
        try {
            runnable2.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            boolean completed = latch.await(10, SECONDS);

            if (!completed) {
                fail("Latch timeout");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void eventually(long timeout, TimeUnit timeUnit, Runnable2 block) {
        long timeoutMillis = timeUnit.toMillis(timeout);
        long startTime = currentTimeMillis();
        Throwable err = null;
        while (currentTimeMillis() - startTime < timeoutMillis) {
            try {
                block.run();
                return;
            } catch (Throwable e) {
                err = e;
            }
        }
        fail("Eventually block did not complete in " + timeout + " " + timeUnit, err);
    }

    private interface Runnable2 {
        void run() throws Exception;
    }

    private static final class SlowToCreatePlugin implements Plugin {
        @Override
        public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
            return chain.proceed(request);
        }

        public static class Factory implements PluginFactory {
            private final CountDownLatch latch;

            Factory(CountDownLatch latch) {
                this.latch = latch;
            }

            @Override
            public Plugin create(Environment environment) {
                LOGGER.info("Waiting for latch to release...");
                await(latch);
                LOGGER.info("Latch released");
                return new SlowToCreatePlugin();
            }
        }
    }
}
