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
package com.hotels.styx.admin.tasks;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.api.HttpHandler;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancingMetricSupplier;
import com.hotels.styx.client.OriginsCommandsListener;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;
import com.hotels.styx.server.HttpInterceptorContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Set;

import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.support.api.BlockingObservables.getFirst;
import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.support.api.matchers.HttpResponseStatusMatcher.hasStatus;
import static java.lang.String.format;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class OriginsCommandHandlerTest {
    final Origin activeOrigin = newOriginBuilder("localhost", 8081).applicationId("activeAppId").id("activeOriginId").build();
    final Set<RemoteHost> activeOrigins = singleton(remoteHost(activeOrigin, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)));

    final Origin disabledOrigin = newOriginBuilder("localhost", 8082).applicationId("activeAppId").id("disabledOriginId").build();
    final Set<RemoteHost> disabledOrigins = singleton(remoteHost(disabledOrigin, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)));

    final Origin inactiveOrigin = newOriginBuilder("localhost", 8083).applicationId("activeAppId").id("inactiveOriginId").build();
    final Set<RemoteHost> inactiveOrigins = singleton(remoteHost(inactiveOrigin, mock(HttpHandler.class), mock(LoadBalancingMetricSupplier.class)));

    final EventBus eventBus = new EventBus();
    final OriginsCommandHandler originsCommand = new OriginsCommandHandler(eventBus);

    RecordingOriginsCommandsListener recordingOriginsCommandsListener;

    @BeforeMethod
    public void registerListener() {
        originsCommand.originsChanged(new OriginsSnapshot(id("activeAppId"), activeOrigins, inactiveOrigins, disabledOrigins));
        recordingOriginsCommandsListener = new RecordingOriginsCommandsListener();
        eventBus.register(recordingOriginsCommandsListener);
    }

    @Test
    public void propagatesTheOriginsDisableCommand() {
        post("/admin/tasks/origins?cmd=disable_origin&appId=activeAppId&originId=activeOriginId");
        assertThat(recordingOriginsCommandsListener.message(), is(new DisableOrigin(id("activeAppId"), id("activeOriginId"))));
    }

    @Test
    public void propagatesTheOriginsEnableCommand() {
        post("/admin/tasks/origins?cmd=enable_origin&appId=activeAppId&originId=disabledOriginId");
        assertThat(recordingOriginsCommandsListener.message(), is(new EnableOrigin(id("activeAppId"), id("disabledOriginId"))));
    }

    @Test
    public void returnsProperErrorMessageForBadCommand() {
        LiveHttpResponse response = post("/admin/tasks/origins?cmd=foo&appId=foo&originId=bar");
        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody("cmd, appId and originId are all required parameters. cmd can be enable_origin|disable_origin"));
    }

    @Test
    public void returnsProperErrorMessageForMissingAppId() {
        LiveHttpResponse response = post("/admin/tasks/origins?cmd=enable_origin&originId=bar");
        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody("cmd, appId and originId are all required parameters. cmd can be enable_origin|disable_origin"));
    }

    @Test
    public void returnsProperErrorMessageForMissingOriginId() {
        LiveHttpResponse response = post("/admin/tasks/origins?cmd=disable_origin&appId=foo");
        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody("cmd, appId and originId are all required parameters. cmd can be enable_origin|disable_origin"));
    }

    @Test(dataProvider = "nonexistentAppOrOriginId")
    public void failsToIssueTheCommandForNonexistentAppId(String appId) {
        LiveHttpResponse response = post(format("/admin/tasks/origins?cmd=disable_origin&appId=%s&originId=bar", appId));
        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody(format("application with id=%s is not found", appId)));
    }

    @Test(dataProvider = "nonexistentAppOrOriginId")
    public void failsToIssueTheCommandForNonexistentOriginId(String originId) {
        LiveHttpResponse response = post(format("/admin/tasks/origins?cmd=disable_origin&appId=activeAppId&originId=%s", originId));
        assertThat(response, hasStatus(BAD_REQUEST));
        assertThat(response, hasBody(format("origin with id=%s is not found for application=activeAppId", originId)));
    }

    @DataProvider(name = "nonexistentAppOrOriginId")
    protected Object[][] nonexistentAppOrOriginId() {
        return new Object[][]{{"foo"}, {"bar"}};
    }

    private LiveHttpResponse post(String path) {
        LiveHttpRequest request = LiveHttpRequest.post(path).build();
        return getFirst(originsCommand.handle(request, HttpInterceptorContext.create()));
    }

    static class RecordingOriginsCommandsListener implements OriginsCommandsListener {
        private Object message;

        public Object message() {
            return this.message;
        }

        @Subscribe
        @Override
        public void onCommand(EnableOrigin enableOrigin) {
            this.message = enableOrigin;
        }

        @Subscribe
        @Override
        public void onCommand(DisableOrigin disableOrigin) {
            this.message = disableOrigin;
        }

        @Subscribe
        @Override
        public void onCommand(GetOriginsInventorySnapshot getOriginsInventorySnapshot) {
        }
    }
}
