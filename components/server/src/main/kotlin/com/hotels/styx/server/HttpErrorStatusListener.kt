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
package com.hotels.styx.server

import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import java.net.InetSocketAddress

/**
 * Listens to errors during requests that result in a 4xx or 5xx status code, so that metrics can be recorded.
 */
interface HttpErrorStatusListener {
    /**
     * To be called when an exception was thrown in styx while proxying.
     *
     * @param cause the throwable class associated with this error
     */
    fun proxyErrorOccurred(cause: Throwable)

    /**
     * To be called when an exception was thrown in styx while writing response.
     *
     * @param cause the throwable class associated with this error
     */
    fun proxyWriteFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable)

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param cause the throwable class associated with this error
     */
    fun proxyingFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable)

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param status HTTP response status
     * @param cause  the throwable class associated with this error
     */
    fun proxyErrorOccurred(status: HttpResponseStatus, cause: Throwable)

    /**
     * To be called when an exception was thrown in styx while proxying.
     * If the status is not an error code, the listener should ignore this method call.
     *
     * @param request Proxied request
     * @param status  HTTP response status
     * @param cause   the throwable class associated with this error
     */
    fun proxyErrorOccurred(request: LiveHttpRequest, clientAddress: InetSocketAddress, status: HttpResponseStatus, cause: Throwable)

    companion object {
        @JvmStatic
        fun compose(vararg listeners: HttpErrorStatusListener): HttpErrorStatusListener = CompositeHttpErrorStatusListener(listOf(*listeners))

        @JvmField
        val IGNORE_ERROR_STATUS: HttpErrorStatusListener = object : HttpErrorStatusListener {
            override fun proxyErrorOccurred(cause: Throwable) {}
            override fun proxyWriteFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable) {}
            override fun proxyingFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable) {}
            override fun proxyErrorOccurred(status: HttpResponseStatus, cause: Throwable) {}
            override fun proxyErrorOccurred(
                request: LiveHttpRequest,
                clientAddress: InetSocketAddress,
                status: HttpResponseStatus,
                cause: Throwable
            ) {
            }
        }
    }
}