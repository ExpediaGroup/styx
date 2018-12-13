package com.hotels.styx.server.netty.handlers;

import com.codahale.metrics.Counter;
import com.hotels.styx.api.MetricRegistry;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Counts connections
 */
// NON-SHARED HANDLER
public class ConnectionCountHandler extends ChannelDuplexHandler {
    private static final String PREFIX = "connections";
    private static final String TOTAL_CONNECTIONS = "total-connections";

    private static final Logger LOGGER = getLogger(ConnectionCountHandler.class);

    private final Counter totalConnections;
    private final AtomicBoolean active;

    public ConnectionCountHandler(MetricRegistry metricRegistry) {
        MetricRegistry metricRegistry1 = metricRegistry.scope(PREFIX);

        this.totalConnections = metricRegistry1.counter(TOTAL_CONNECTIONS);

        this.active = new AtomicBoolean(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        boolean wasPreviouslyInactive = active.compareAndSet(false, true);

        if (wasPreviouslyInactive) {
            totalConnections.inc();
        } else {
            LOGGER.warn("channelActive on already active channel");
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean wasPreviouslyActive = active.compareAndSet(true, false);

        if (wasPreviouslyActive) {
            totalConnections.dec();
        } else {
            LOGGER.warn("channelInactive on already inactive channel");
        }

        super.channelInactive(ctx);
    }
}
