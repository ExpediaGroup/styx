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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;
import com.hotels.styx.server.track.CurrentRequestTracker;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.State.BLOCKED;
import static java.lang.management.ManagementFactory.getThreadMXBean;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Admin handler that will help in tracking only the current HTTP requests to Styx.
 */
public class CurrentRequestsHandler extends BaseHttpHandler {
    private final ThreadMXBean threadMXBean;
    private final CurrentRequestTracker tracker;

    public CurrentRequestsHandler(CurrentRequestTracker tracker) {
        this.threadMXBean = getThreadMXBean();
        this.tracker = tracker;
    }

    @Override
    public LiveHttpResponse doHandle(LiveHttpRequest request) {
        boolean withStackTrace = request.queryParam("withStackTrace")
                .map("true"::equals)
                .orElse(false);

        return HttpResponse
                .response(OK)
                .disableCaching()
                .header(CONTENT_TYPE, PLAIN_TEXT_UTF_8)
                .body(getCurrentRequestContent(withStackTrace), UTF_8, true)
                .build()
                .stream();
    }

    private String getCurrentRequestContent(boolean withStackTrace) {
        StringBuilder sb = new StringBuilder();

        tracker.currentRequests().forEach(req -> {
            sb.append("[\n");
            sb.append(req.request().replaceAll(",", "\n"));
            sb.append("\n\n");
            sb.append("running for: ");
            sb.append(currentTimeMillis() - req.startingTimeMillies());
            sb.append("ms\n");
            if (req.isRequestSent()) {
                sb.append("\nRequest state: Waiting response from origin.\n");
            } else {
                sb.append("\nRequest state: Plugins pipeline.\n");
                sb.append("\n\nThread Info:\n");
                if (withStackTrace) {
                    sb.append(getThreadInfo(req.currentThread().getId()));
                } else {
                    sb.append("Name: ");
                    sb.append(req.currentThread().getName());
                    sb.append("\n");
                }
            }
            sb.append("]\n\n");
        });

        return sb.toString();
    }

    private String getThreadInfo(long threadId) {
        StringBuilder sb = new StringBuilder();

        ThreadInfo t = threadMXBean.getThreadInfo(threadId, Integer.MAX_VALUE);
        sb.append(format("\"%s\" id=%d state=%s", t.getThreadName(), t.getThreadId(), t.getThreadState()));
        sb.append(getThreadState(t));

        if (t.isSuspended()) {
            sb.append(" (suspended)");
        }

        if (t.isInNative()) {
            sb.append(" (running in native)");
        }

        sb.append("\n");
        if (t.getLockOwnerName() != null) {
            sb.append(format("     owned by %s id=%d%n", t.getLockOwnerName(), t.getLockOwnerId()));
        }

        sb.append(getThreadElements(t));
        sb.append("\n");

        sb.append(getThreadLockedSynchronizer(t));

        return sb.toString();
    }

    private String getThreadState(ThreadInfo t) {
        StringBuilder sb = new StringBuilder();

        LockInfo lock = t.getLockInfo();
        if (lock != null && t.getThreadState() != BLOCKED) {
            sb.append(format("%n    - waiting on <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName()));
            sb.append(format("%n    - locked <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName()));
        } else if (lock != null && t.getThreadState() == BLOCKED) {
            sb.append(format("%n    - waiting to lock <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName()));
        }

        return sb.toString();
    }

    private String getThreadLockedSynchronizer(ThreadInfo t) {
        StringBuilder sb = new StringBuilder();

        LockInfo[] locks = t.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append(format("    Locked synchronizers: count = %d%n", locks.length));
            for (LockInfo l : locks) {
                sb.append(format("      - %s%n", l));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String getThreadElements(ThreadInfo t) {
        StackTraceElement[] elements = t.getStackTrace();
        MonitorInfo[] monitors = t.getLockedMonitors();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < elements.length; i++) {
            StackTraceElement element = elements[i];
            sb.append(format("    at %s%n", element));
            for (int j = 1; j < monitors.length; j++) {
                MonitorInfo monitor = monitors[j];
                if (monitor.getLockedStackDepth() == i) {
                    sb.append(format("      - locked %s%n", monitor));
                }
            }
        }

        return sb.toString();
    }
}
