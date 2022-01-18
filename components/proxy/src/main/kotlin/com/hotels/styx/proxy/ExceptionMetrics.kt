/*
  Copyright (C) 2013-2022 Expedia Inc.

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
package com.hotels.styx.proxy

import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.exceptions.OriginUnreachableException
import com.hotels.styx.api.exceptions.ResponseTimeoutException
import com.hotels.styx.api.exceptions.StyxException
import com.hotels.styx.client.BadHttpResponseException
import com.hotels.styx.client.connectionpool.MaxPendingConnectionTimeoutException
import com.hotels.styx.client.connectionpool.MaxPendingConnectionsExceededException
import com.hotels.styx.metrics.CentralisedMetrics
import io.micrometer.core.instrument.Counter

/**
 * Records metrics for back-end faults.
 * These are errors that describe an ability to send traffic to a bank-end origin, not problems with Styx itself.
 */
fun countBackendFault(metrics: CentralisedMetrics, error: Throwable) {
    val counter : Counter? = metrics.proxy.client.run {
        when (error) {
            is NoAvailableHostsException ->
                backendFaults(error.appId(), " ", "noHostsLiveForApplication")
            is OriginUnreachableException ->
                backendFaults(error.appId(), error.originId(), "cannotConnect")
            is BadHttpResponseException ->
                backendFaults(error.appId(), error.originId(), "badHttpResponse")
            is MaxPendingConnectionTimeoutException ->
                backendFaults(error.appId(), error.originId(), "connectionsHeldTooLong")
            is MaxPendingConnectionsExceededException ->
                backendFaults(error.appId(), error.originId(), "tooManyConnections")
            is ResponseTimeoutException ->
                backendFaults(error.appId(), error.originId(), "responseTooSlow")
            else -> null
        }
    }

    counter?.increment()
}

private fun StyxException.appId(): String = application().toString()
private fun StyxException.originId(): String? = origin().orElse(null)?.toString()
