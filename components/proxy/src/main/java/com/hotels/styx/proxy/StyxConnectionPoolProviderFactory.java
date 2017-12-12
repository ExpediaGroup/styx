package com.hotels.styx.proxy;

import com.hotels.styx.Environment;
import com.hotels.styx.api.client.ConnectionPoolProvider;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import com.hotels.styx.client.BackendServiceConnectionPoolProvider;
import com.hotels.styx.client.OriginStatsFactory;
import com.hotels.styx.client.applications.BackendService;
import com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy;
import com.hotels.styx.client.netty.connectionpool.NettyConnectionFactory;

import static com.hotels.styx.serviceproviders.ServiceProvision.loadService;

public class StyxConnectionPoolProviderFactory implements ConnectionPoolProviderFactory {

    private final Environment environment;
    private final int clientWorkerThreadsCount;

    public StyxConnectionPoolProviderFactory (Environment environment, int clientWorkerThreadsCount) {
        this.environment = environment;
        this.clientWorkerThreadsCount = clientWorkerThreadsCount;
    }

    @Override
    public ConnectionPoolProvider createProvider(BackendService backendService) {
        LoadBalancingStrategy loadBalancingStrategy = loadService(environment.configuration(), environment, "loadBalancing.strategy.factory", LoadBalancingStrategy.class)
                .orElseGet(RoundRobinStrategy::new);

        OriginStatsFactory originStatsFactory = new OriginStatsFactory(environment.metricRegistry());

        return new BackendServiceConnectionPoolProvider.Builder(backendService)
                .version(environment.buildInfo().releaseVersion())
                .eventBus(environment.eventBus())
                .loadBalancingStrategy(loadBalancingStrategy)
                .originRestrictionCookie(environment.configuration().get("originRestrictionCookie").orElse(null))
                .connectionFactory(new NettyConnectionFactory.Builder()
                        .name("Styx")
                        .clientWorkerThreadsCount(clientWorkerThreadsCount)
                        .tlsSettings(backendService.tlsSettings().orElse(null)).build())
                .originStatsFactory(originStatsFactory)
                .build();
    }
}
