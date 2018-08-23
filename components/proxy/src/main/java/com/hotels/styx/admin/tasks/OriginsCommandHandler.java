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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import com.hotels.styx.client.origincommands.DisableOrigin;
import com.hotels.styx.client.origincommands.EnableOrigin;
import com.hotels.styx.client.origincommands.GetOriginsInventorySnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_LENGTH;
import static com.hotels.styx.api.HttpHeaderNames.LOCATION;
import static com.hotels.styx.api.Id.id;
import static com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST;
import static com.hotels.styx.api.HttpResponseStatus.TEMPORARY_REDIRECT;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import com.hotels.styx.api.HttpRequest;

/**
 * Handles commands for enabling and disabling origins.
 */
public class OriginsCommandHandler extends BaseHttpHandler implements OriginsChangeListener {
    private static final String INVALID_APP_ID_FORMAT = "application with id=%s is not found";
    private static final String INVALID_ORIGIN_ID_FORMAT = "origin with id=%s is not found for application=%s";
    private static final List<String> VALID_COMMANDS = ImmutableList.of("enable_origin", "disable_origin");
    private static final String MISSING_ERROR_MESSAGE = format("cmd, appId and originId are all required parameters. cmd can be %s", Joiner.on('|').join(VALID_COMMANDS));

    private final EventBus eventBus;
    private final Map<Id, OriginsSnapshot> originsInventorySnapshotMap = new ConcurrentHashMap<>();

    /**
     * Constructs an instance with an event bus to pass commands to, and also to listen to for inventory
     * state changes.
     *
     * @param eventBus event bus
     */
    public OriginsCommandHandler(EventBus eventBus) {
        this.eventBus = eventBus;
        this.eventBus.register(this);
        this.eventBus.post(new GetOriginsInventorySnapshot());
    }

    @Override
    public HttpResponse doHandle(HttpRequest request) {
        String cmd = request.queryParam("cmd").orElse("");
        String appId = request.queryParam("appId").orElse("");
        String originId = request.queryParam("originId").orElse("");
        if (!isValidCommand(cmd) || isNullOrEmpty(appId) || isNullOrEmpty(originId)) {
            return response(BAD_REQUEST)
                    .body(MISSING_ERROR_MESSAGE, UTF_8)
                    .build()
                    .toStreamingResponse();
        }

        if (!originsInventorySnapshotMap.containsKey(id(appId))) {
            return response(BAD_REQUEST)
                    .body(format(INVALID_APP_ID_FORMAT, appId), UTF_8)
                    .build()
                    .toStreamingResponse();
        }

        if (!validOriginId(id(appId), id(originId))) {
            return response(BAD_REQUEST)
                    .body(format(INVALID_ORIGIN_ID_FORMAT, originId, appId), UTF_8)
                    .build()
                    .toStreamingResponse();
        }

        Object originCommand = newOriginCommand(cmd, id(appId), id(originId));
        eventBus.post(originCommand);


        return response(TEMPORARY_REDIRECT)
                .header(LOCATION, "/admin/origins/status")
                .header(CONTENT_LENGTH, 0)
                .build()
                .toStreamingResponse();
    }

    private boolean validOriginId(Id appId, Id originId) {
        OriginsSnapshot inventorySnapshot = originsInventorySnapshotMap.get(appId);
        return inventorySnapshot.containsOrigin(originId);
    }

    private static Object newOriginCommand(String cmd, Id appId, Id originId) {
        switch (cmd) {
            case "enable_origin":
                return new EnableOrigin(appId, originId);
            case "disable_origin":
                return new DisableOrigin(appId, originId);
            default:
                // this should never be reached as the validity of the command should already have been checked
                throw new IllegalArgumentException(cmd);
        }
    }

    private static boolean isValidCommand(String cmd) {
        return VALID_COMMANDS.contains(cmd);
    }

    @Subscribe
    @Override
    public void originsChanged(OriginsSnapshot snapshot) {
        originsInventorySnapshotMap.put(snapshot.appId(), snapshot);
    }
}
