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
package com.hotels.styx.routing.handlers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.Id;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.configuration.ObjectStore;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import com.hotels.styx.api.extension.service.StickySessionConfig;
import com.hotels.styx.client.OriginRestrictionLoadBalancingStrategy;
import com.hotels.styx.client.StyxBackendServiceClient;
import com.hotels.styx.client.loadbalancing.strategies.PowerOfTwoStrategy;
import com.hotels.styx.client.stickysession.StickySessionLoadBalancingStrategy;
import com.hotels.styx.config.schema.Schema;
import com.hotels.styx.infrastructure.configuration.yaml.JsonNodeConfig;
import com.hotels.styx.routing.RoutingObject;
import com.hotels.styx.routing.RoutingObjectAdapter;
import com.hotels.styx.routing.RoutingObjectRecord;
import com.hotels.styx.routing.config.HttpHandlerFactory;
import com.hotels.styx.routing.config.RoutingObjectDefinition;
import com.hotels.styx.routing.db.StyxObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.hotels.styx.api.extension.Origin.newOriginBuilder;
import static com.hotels.styx.api.extension.RemoteHost.remoteHost;
import static com.hotels.styx.api.extension.service.StickySessionConfig.stickySessionDisabled;
import static com.hotels.styx.config.schema.SchemaDsl.bool;
import static com.hotels.styx.config.schema.SchemaDsl.field;
import static com.hotels.styx.config.schema.SchemaDsl.integer;
import static com.hotels.styx.config.schema.SchemaDsl.object;
import static com.hotels.styx.config.schema.SchemaDsl.optional;
import static com.hotels.styx.config.schema.SchemaDsl.string;
import static com.hotels.styx.routing.config.RoutingSupport.missingAttributeError;
import static java.lang.String.join;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Add an API for LoadBalancingGroup class.
 */
public class LoadBalancingGroup implements RoutingObject {
    public static final Schema.FieldType SCHEMA = object(
            field("origins", string()),
            optional("originsRestrictionCookie", string()),
            optional("stickySession", object(
                    field("enabled", bool()),
                    field("timeoutSeconds", integer())
            ))
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadBalancingGroup.class);

    private final StyxBackendServiceClient client;
    private final Disposable changeWatcher;


    @VisibleForTesting
    LoadBalancingGroup(StyxBackendServiceClient client, Disposable changeWatcher) {
        this.client = requireNonNull(client);
        this.changeWatcher = requireNonNull(changeWatcher);
    }

    @Override
    public Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context) {
        return new Eventual<>(client.sendRequest(request));
    }

    @Override
    public CompletableFuture<Void> stop() {
        changeWatcher.dispose();
        return completedFuture(null);
    }

    /**
     * Add an API doc for Factory class.
     */
    public static class Factory implements HttpHandlerFactory {

        @Override
        public RoutingObject build(List<String> parents, Context context, RoutingObjectDefinition configBlock) {
            JsonNodeConfig config = new JsonNodeConfig(configBlock.config());
            String name = parents.get(parents.size() - 1);

            String appId = config.get("origins")
                    .orElseThrow(() -> missingAttributeError(configBlock, join(".", parents), "origins"));

            StickySessionConfig stickySessionConfig = config.get("stickySession", StickySessionConfig.class)
                    .orElse(stickySessionDisabled());

            String originsRestrictionCookie = config.get("originsRestrictionCookie")
                    .orElse(null);

            StyxObjectStore<RoutingObjectRecord> routeDb = context.routeDb();
            AtomicReference<Set<RemoteHost>> remoteHosts = new AtomicReference<>(ImmutableSet.of());

            Disposable watch = Flux.from(routeDb.watch()).subscribe(
                    snapshot -> routeDatabaseChanged(appId, snapshot, remoteHosts),
                    cause -> watchFailed(name, cause),
                    () -> watchCompleted(name)
            );

            LoadBalancer loadBalancer = loadBalancer(stickySessionConfig, originsRestrictionCookie, remoteHosts::get);
            StyxBackendServiceClient client = new StyxBackendServiceClient.Builder(Id.id(name))
                    .loadBalancer(loadBalancer)
                    .metricsRegistry(context.environment().metricRegistry())
                    .originIdHeader(context.environment().configuration().styxHeaderConfig().originIdHeaderName())
                    .stickySessionConfig(stickySessionConfig)
                    .originsRestrictionCookieName(originsRestrictionCookie)
                    .build();
            return new LoadBalancingGroup(client, watch);
        }

        private static LoadBalancer loadBalancer(StickySessionConfig stickySessionConfig, String originsRestrictionCookie, ActiveOrigins activeOrigins) {
            LoadBalancer loadBalancer = new PowerOfTwoStrategy(activeOrigins);
            if (stickySessionConfig.stickySessionEnabled()) {
                return new StickySessionLoadBalancingStrategy(activeOrigins, loadBalancer);
            } else if (originsRestrictionCookie == null){
                return loadBalancer;
            } else {
                return new OriginRestrictionLoadBalancingStrategy(activeOrigins, loadBalancer);
            }
        }

        private static void routeDatabaseChanged(String appId, ObjectStore<RoutingObjectRecord> snapshot, AtomicReference<Set<RemoteHost>> remoteHosts) {
            Set<RemoteHost> newSet = snapshot.entrySet()
                    .stream()
                    .filter(it -> isTaggedWith(it, appId))
                    .map(it -> toRemoteHost(appId, it))
                    .collect(Collectors.toSet());

            remoteHosts.set(newSet);
        }

        private static boolean isTaggedWith(Map.Entry<String, RoutingObjectRecord> recordEntry, String appId) {
            return recordEntry.getValue().getTags().contains(appId);
        }

        private static RemoteHost toRemoteHost(String appId, Map.Entry<String, RoutingObjectRecord> record) {
            RoutingObjectAdapter routingObject = record.getValue().getRoutingObject();
            String originName = record.getKey();

            return remoteHost(
                    // The origin is used to determine remote host hostname or port
                    // therefore we'll just pass NA:0
                    newOriginBuilder("na", 0)
                            .applicationId(appId)
                            .id(originName)
                            .build(),
                    routingObject,
                    routingObject::metric);
        }

        private static void watchFailed(String name, Throwable cause) {
            LOGGER.error("{} watch error - cause={}", name, cause);
        }

        private static void watchCompleted(String name) {
            LOGGER.error("{} watch complete", name);
        }

    }
}
