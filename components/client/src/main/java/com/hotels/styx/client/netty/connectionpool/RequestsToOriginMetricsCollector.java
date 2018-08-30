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
package com.hotels.styx.client.netty.connectionpool;

import com.hotels.styx.client.applications.AggregateTimer;
import com.hotels.styx.client.applications.OriginStats;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;


/**
 * A netty channel handler that collects statistics on outbound requests.
 */
class RequestsToOriginMetricsCollector extends ChannelDuplexHandler {
    public static final String NAME = "outbound-request-stats-handler";

    private static final Logger LOG = LoggerFactory.getLogger(RequestsToOriginMetricsCollector.class);

    private final OriginStats originStats;
    private volatile AggregateTimer.Stopper requestLatencyTiming;

    /**
     * Constructs a new instance.
     *
     * @param originStats object to record statistics in
     */
    public RequestsToOriginMetricsCollector(OriginStats originStats) {
        this.originStats = requireNonNull(originStats);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //
        // Break out HttpResponse and and LastHttpContent handling in separate
        // blocks. This way it doesn't require an HttpObjectAggregator in the
        // pipeline.
        //
        if (msg instanceof HttpResponse) {
            HttpResponse resp = (HttpResponse) msg;

            int code = resp.getStatus().code();
            updateHttpResponseCounters(code);
        }

        if (msg instanceof LastHttpContent) {
            stopAndRecordLatency();
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
            requestLatencyTiming = originStats.requestLatencyTimer().time();
        }
        super.write(ctx, msg, promise);
    }

    private static boolean statusIsServerError(int status) {
        return status / 100 == 5;
    }

    private void updateHttpResponseCounters(int statusCode) {
        if (statusIsServerError(statusCode)) {
            originStats.requestError();
        } else {
            originStats.requestSuccess();
        }
        originStats.responseWithStatusCode(statusCode);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        stopAndRecordLatency();
        super.exceptionCaught(ctx, cause);
    }

    private void stopAndRecordLatency() {
        // Should only be null in unit tests,
        // but this check is also here just in case there is some weird bug, we should not interfere with the proxying
        // just because it doesn't record metrics
        if (requestLatencyTiming != null) {
            requestLatencyTiming.stopAndRecord();
        } else {
            LOG.warn("Attempted to stop timer and record latency when no timing had begun");
        }
    }
}
