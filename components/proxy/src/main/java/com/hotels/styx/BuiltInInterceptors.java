/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx;

import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.proxy.interceptors.ConfigurationContextResolverInterceptor;
import com.hotels.styx.proxy.interceptors.HopByHopHeadersRemovingInterceptor;
import com.hotels.styx.proxy.interceptors.HttpMessageLoggingInterceptor;
import com.hotels.styx.proxy.interceptors.RequestEnrichingInterceptor;
import com.hotels.styx.proxy.interceptors.TcpTunnelRequestRejector;
import com.hotels.styx.proxy.interceptors.UnexpectedRequestContentLengthRemover;
import com.hotels.styx.proxy.interceptors.ViaHeaderAppendingInterceptor;

import java.util.ArrayList;
import java.util.List;

import static com.hotels.styx.api.configuration.ConfigurationContextResolver.EMPTY_CONFIGURATION_CONTEXT_RESOLVER;
import static com.hotels.styx.common.Collections.listOf;

/**
 * Provides a list of interceptors that are required by the Styx HTTP pipeline for core functionality.
 */
final class BuiltInInterceptors {
    private BuiltInInterceptors() {
    }

    static List<HttpInterceptor> internalStyxInterceptors(StyxConfig config, HttpMessageFormatter httpMessageFormatter) {
        List<HttpInterceptor> tempList = new ArrayList<>();

        boolean loggingEnabled = config.get("request-logging.inbound.enabled", Boolean.class)
                .orElse(false);

        boolean longFormatEnabled = config.get("request-logging.inbound.longFormat", Boolean.class)
                .orElse(false);

        if (loggingEnabled) {
            tempList.add(new HttpMessageLoggingInterceptor(longFormatEnabled, httpMessageFormatter));
        }

        tempList.add(new TcpTunnelRequestRejector());
        tempList.add(new ConfigurationContextResolverInterceptor(EMPTY_CONFIGURATION_CONTEXT_RESOLVER));
        tempList.add(new UnexpectedRequestContentLengthRemover());
        tempList.add(new ViaHeaderAppendingInterceptor());
        tempList.add(new HopByHopHeadersRemovingInterceptor());
        tempList.add(new RequestEnrichingInterceptor(config.styxHeaderConfig()));

        return listOf(tempList);
    }
}
