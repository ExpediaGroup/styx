package com.hotels.styx.proxy;

import com.hotels.styx.api.client.ConnectionPoolProvider;
import com.hotels.styx.client.applications.BackendService;

/**
 * Creates a provider object that manages and providers connection pools for specified {@link BackendService}.
 */
public interface ConnectionPoolProviderFactory {
    ConnectionPoolProvider createProvider(BackendService backendService);
}
