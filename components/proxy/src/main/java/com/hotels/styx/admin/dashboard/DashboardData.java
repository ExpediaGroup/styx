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
package com.hotels.styx.admin.dashboard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.hotels.styx.Version;
import com.hotels.styx.api.MetricRegistry;
import com.hotels.styx.api.extension.OriginsChangeListener;
import com.hotels.styx.api.extension.OriginsSnapshot;
import com.hotels.styx.api.extension.service.BackendService;
import com.hotels.styx.api.extension.service.spi.Registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Supplier;

import static com.google.common.collect.Iterables.transform;
import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.COUNTER;
import static com.hotels.styx.admin.dashboard.ResponseCodeSupplier.StatusMetricType.METER;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

/**
 * Data to be converted to JSON for the dashboard.
 */
public class DashboardData {
    private final MetricRegistry metrics;
    private final Server server;
    private final Downstream downstream;
    private final String serverId;
    private final String version;
    private final EventBus eventBus;
    private final Registry<BackendService> backendServicesRegistry;

    public DashboardData(MetricRegistry metrics, Registry<BackendService> backendServicesRegistry, String serverId, Version version, EventBus eventBus) {
        this.backendServicesRegistry = requireNonNull(backendServicesRegistry);

        this.serverId = requireNonNull(serverId);
        this.metrics = requireNonNull(metrics);
        this.version = version.releaseVersion();
        this.eventBus = requireNonNull(eventBus);

        this.server = new Server();
        this.downstream = new Downstream();
    }

    @JsonProperty("server")
    public Server server() {
        return server;
    }

    @JsonProperty("downstream")
    public Downstream downstream() {
        return downstream;
    }

    /*
     * This will give us the time at which this object was converted into JSON.
     */
    @JsonProperty("publishTime")
    public long publishTime() {
        return System.currentTimeMillis();
    }

    void unregister() {
        this.downstream.unregister();
    }

    /**
     * Styx-related data.
     */
    public final class Server {
        private final Gauge<String> uptimeGauge;
        private final Supplier<Map<String, Integer>> responsesSupplier;

        private Server() {
            this.uptimeGauge = metrics.getGauges().get("jvm.uptime.formatted");
            this.responsesSupplier = new ResponseCodeSupplier(metrics, COUNTER, "styx.response.status", false);
        }

        @JsonProperty("id")
        public String id() {
            return serverId;
        }

        @JsonProperty("version")
        public String version() {
            return version;
        }

        @JsonProperty("uptime")
        String uptime() {
            return uptimeGauge == null ? null : uptimeGauge.getValue();
        }

        @JsonProperty("responses")
        public Map<String, Integer> responses() {
            return responsesSupplier.get();
        }
    }

    /**
     * Data related to all origins.
     */
    public final class Downstream implements Registry.ChangeListener<BackendService> {
        private Collection<Backend> backends;
        private final Supplier<Map<String, Integer>> responsesSupplier;

        private Downstream() {
            this.backends = updateBackendsFromRegistry();

            this.responsesSupplier = new ResponseCodeSupplier(metrics, COUNTER, "origins.response.status", false);
            backendServicesRegistry.addListener(this);
        }

        @JsonProperty("responses")
        public Map<String, Integer> responses() {
            return responsesSupplier.get();
        }

        @JsonProperty("backends")
        public Collection<Backend> backends() {
            return backends;
        }

        @VisibleForTesting
        Backend firstBackend() {
            return backends().stream().findFirst().get();
        }

        @VisibleForTesting
        List<String> backendIds() {
            return backends().stream().map(Backend::id).collect(toList());
        }

        @VisibleForTesting
        Backend backend(String backendId) {
            return backends().stream()
                    .filter(backend -> backend.id().equals(backendId))
                    .findFirst().orElseThrow(() ->
                            new IllegalStateException(format("No origin with id %s in %s", backendId, backendIds())));
        }

        @Override
        public void onChange(Registry.Changes<BackendService> changes) {
            this.backends = updateBackendsFromRegistry();
        }

        private List<Backend> updateBackendsFromRegistry() {
            unregister();

            return stream(backendServicesRegistry.get().spliterator(), false)
                    .map(Backend::new)
                    .collect(toList());
        }

        void unregister() {
            if (backends != null) {
                backends.forEach(Backend::unregister);
            }
        }
    }

    /**
     * Application-related data.
     */
    public final class Backend {
        private final String id;
        private final String name;
        private final List<Origin> origin;
        private List<Origin> registeredOrigins;

        private final Supplier<Map<String, Integer>> responsesSupplier;
        private final Requests requests;
        private final List<String> status;
        private final ConnectionsPoolsAggregate connectionsPoolsAggregate;

        private Backend(BackendService application) {
            this.name = application.id().toString();
            this.id = serverId + "-" + name;
            this.requests = new Requests("origins." + application.id());

            this.origin = application.origins().stream().map(Origin::new).collect(toList());
            this.registeredOrigins = new ArrayList<>();

            this.origin.forEach(origin -> {
                eventBus.register(origin);
                registeredOrigins.add(origin);
            });

            /* IMPORTANT NOTE: We are using guava transforms here instead of java 8 stream-map-collect because
              the guava transforms are backed by the original objects and reflect changes in them. */
            this.status = Lists.transform(origin, Origin::status);
            this.connectionsPoolsAggregate = new ConnectionsPoolsAggregate(transform(origin, Origin::connectionsPool));

            String prefix = format("origins.%s.requests.response.status", name);
            this.responsesSupplier = new ResponseCodeSupplier(metrics, METER, prefix, true);
        }

        void unregister() {
            registeredOrigins.forEach(origin -> {
                eventBus.unregister(origin);
            });
            registeredOrigins = new ArrayList<>();
        }

        @JsonProperty("id")
        public String id() {
            return id;
        }

        @JsonProperty("name")
        public String name() {
            return name;
        }

        @JsonProperty("responses")
        public Map<String, Integer> responses() {
            return responsesSupplier.get();
        }

        @JsonProperty("requests")
        public Requests requests() {
            return requests;
        }

        @JsonProperty("origin")
        public Collection<Origin> origins() {
            return origin;
        }

        @JsonProperty("statuses")
        public Collection<String> statuses() {
            return status;
        }

        @JsonProperty("totalConnections")
        public ConnectionsPoolsAggregate totalConnections() {
            return connectionsPoolsAggregate;
        }

        @VisibleForTesting
        List<String> originsStatuses() {
            return origins()
                    .stream()
                    .map(origin -> origin.id() + "=" + origin.status())
                    .collect(toList());
        }

        @VisibleForTesting
        Map<String, String> statusesByOriginId() {
            return origins().stream().collect(toMap(Origin::id, Origin::status));
        }

        @VisibleForTesting
        Origin origin(String originId) {
            return origins().stream()
                    .filter(origin -> origin.id().equals(originId))
                    .findFirst().get();
        }

        @VisibleForTesting
        Origin firstOrigin() {
            return origins().stream()
                    .findFirst().get();
        }
    }

    /**
     * Requests-related data.
     */
    public final class Requests {
        private final SuccessRate successRate;
        private final ErrorRate errorRate;
        private final Latency latency;

        private Requests(String prefix) {
            successRate = new SuccessRate(metrics.meter(prefix + ".requests.success-rate"));
            errorRate = new ErrorRate(metrics.meter(prefix + ".requests.error-rate"));
            latency = new Latency(metrics.timer(prefix + ".requests.latency"));
        }

        @JsonProperty("successRate")
        public SuccessRate successRate() {
            return successRate;
        }

        @JsonProperty("errorRate")
        public ErrorRate errorRate() {
            return errorRate;
        }

        @JsonProperty("latency")
        public Latency latency() {
            latency.updateSnapshot();
            return latency;
        }

        @JsonProperty("errorPercentage")
        public double errorPercentage() {
            double errorRate = errorRate().count();

            return 100.0 * (errorRate / (successRate.count() + errorRate));
        }
    }

    /**
     * Success data.
     */
    public static final class SuccessRate {
        private final Meter meter;

        private SuccessRate(Meter meter) {
            this.meter = meter;
        }

        @JsonProperty("count")
        public long count() {
            return meter.getCount();
        }

        @JsonProperty("m1")
        public double m1Rate() {
            return meter.getOneMinuteRate();
        }

        @JsonProperty("m15")
        public double m15Rate() {
            return meter.getFifteenMinuteRate();
        }

        @JsonProperty("mean")
        public double meanRate() {
            return meter.getMeanRate();
        }
    }

    /**
     * Error data.
     */
    public static final class ErrorRate {
        private final Meter meter;

        private ErrorRate(Meter meter) {
            this.meter = meter;
        }

        @JsonProperty("count")
        public long count() {
            return meter.getCount();
        }

        @JsonProperty("m1")
        public double m1Rate() {
            return meter.getOneMinuteRate();
        }

        @JsonProperty("m15")
        public double m15Rate() {
            return meter.getFifteenMinuteRate();
        }

        @JsonProperty("mean")
        public double meanRate() {
            return meter.getMeanRate();
        }
    }

    /**
     * Latency.
     */
    public static final class Latency {
        private static final double DURATION_FACTOR = 1.0 / MILLISECONDS.toNanos(1);

        private final Timer timer;
        private volatile Snapshot snapshot;

        private Latency(Timer timer) {
            this.timer = timer;
        }

        @JsonProperty("p50")
        public double p50() {
            return millis(snapshot.getMedian());
        }

        @JsonProperty("p75")
        public double p75() {
            return millis(snapshot.get75thPercentile());
        }

        @JsonProperty("p95")
        public double p95() {
            return millis(snapshot.get95thPercentile());
        }

        @JsonProperty("p98")
        public double p98() {
            return millis(snapshot.get98thPercentile());
        }

        @JsonProperty("p99")
        public double p99() {
            return millis(snapshot.get99thPercentile());
        }

        @JsonProperty("p999")
        public double p999() {
            return millis(snapshot.get999thPercentile());
        }

        @JsonProperty("mean")
        public double mean() {
            return millis(snapshot.getMean());
        }

        private static double millis(double nanos) {
            return nanos * DURATION_FACTOR;
        }

        private void updateSnapshot() {
            snapshot = timer.getSnapshot();
        }
    }

    /**
     * Aggregation of connection pools data.
     */
    public static final class ConnectionsPoolsAggregate {
        private final Iterable<Origin.ConnectionsPool> pools;

        private ConnectionsPoolsAggregate(Iterable<Origin.ConnectionsPool> pools) {
            this.pools = pools;
        }

        @JsonProperty("available")
        public int available() {
            int available = 0;
            for (Origin.ConnectionsPool pool : pools) {
                available += pool.available();
            }
            return available;
        }

        @JsonProperty("busy")
        public int busy() {
            int busy = 0;
            for (Origin.ConnectionsPool pool : pools) {
                busy += pool.busy();
            }
            return busy;
        }

        @JsonProperty("pending")
        public int pending() {
            int pending = 0;
            for (Origin.ConnectionsPool pool : pools) {
                pending += pool.pending();
            }
            return pending;
        }
    }

    /**
     * Origin-related data.
     */
    public final class Origin implements OriginsChangeListener {
        private final com.hotels.styx.api.extension.Origin origin;
        private final Supplier<Map<String, Integer>> responsesSupplier;
        private final Requests requests;
        private final ConnectionsPool connectionsPool;
        private String status = "unknown";

        private Origin(com.hotels.styx.api.extension.Origin origin) {
            this.origin = origin;
            connectionsPool = new ConnectionsPool();

            String prefix = format("origins.%s.%s.requests.response.status", origin.applicationId(), origin.id());
            this.responsesSupplier = new ResponseCodeSupplier(metrics, METER, prefix, true);

            this.requests = new Requests(format("origins.%s.%s", origin.applicationId(), origin.id()));
        }

        @Subscribe
        @Override
        public void originsChanged(OriginsSnapshot snapshot) {
            if (snapshot.activeOrigins().contains(origin)) {
                status = "active";
            } else if (snapshot.inactiveOrigins().contains(origin)) {
                status = "inactive";
            } else if (snapshot.disabledOrigins().contains(origin)) {
                status = "disabled";
            }
        }

        @JsonProperty("id")
        public String id() {
            return origin.id().toString();
        }

        @JsonProperty("name")
        public String name() {
            return origin.id().toString();
        }

        @JsonProperty("responses")
        public Map<String, Integer> responses() {
            return responsesSupplier.get();
        }

        @JsonProperty("requests")
        public Requests requests() {
            return requests;
        }

        @JsonProperty("status")
        public String status() {
            return status;
        }

        @JsonProperty("connectionsPool")
        public ConnectionsPool connectionsPool() {
            return connectionsPool;
        }

        /**
         * Connection-pool-related data.
         */
        public final class ConnectionsPool {
            private final Gauge<Integer> availableGauge;
            private final Gauge<Integer> busyGauge;
            private final Gauge<Integer> pendingGauge;

            private ConnectionsPool() {
                String prefix = format("origins.%s.%s.connectionspool", origin.applicationId(), origin.id());

                SortedMap<String, Gauge> gauges = metrics.getGauges();

                availableGauge = gauges.get(prefix + ".available-connections");
                busyGauge = gauges.get(prefix + ".busy-connections");
                pendingGauge = gauges.get(prefix + ".pending-connections");
            }

            @JsonProperty("available")
            public int available() {
                return availableGauge == null ? 0 : availableGauge.getValue();
            }

            @JsonProperty("busy")
            public int busy() {
                return busyGauge == null ? 0 : busyGauge.getValue();
            }

            @JsonProperty("pending")
            public int pending() {
                return pendingGauge == null ? 0 : pendingGauge.getValue();
            }
        }
    }
}
