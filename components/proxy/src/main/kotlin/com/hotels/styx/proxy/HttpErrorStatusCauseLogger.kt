/*
  Copyright (C) 2013-2021 Expedia Inc.

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

import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.LiveHttpRequest
import com.hotels.styx.api.LiveHttpResponse
import com.hotels.styx.common.format.HttpMessageFormatter
import com.hotels.styx.server.HttpErrorStatusListener
import org.slf4j.LoggerFactory.getLogger
import java.net.InetSocketAddress

private val LOG = getLogger(HttpErrorStatusCauseLogger::class.java)

/**
 * Wrapper for [HttpErrorStatusListener] that also logs [Throwable]s.
 */
class HttpErrorStatusCauseLogger(private val formatter: HttpMessageFormatter) : HttpErrorStatusListener {
    override fun proxyErrorOccurred(status: HttpResponseStatus, cause: Throwable) {
        if (status.code() > 500) {
            // we remove the stack trace so that logs are not flooded with high volumes of data when origins are unreachable/timing out.
            LOG.error("Failure status=\"{}\", exception=\"{}\"", status, withoutStackTrace(cause))
        } else {
            LOG.error("Failure status=\"{}\"", status, cause)
        }
    }

    override fun proxyErrorOccurred(request: LiveHttpRequest, clientAddress: InetSocketAddress, status: HttpResponseStatus, cause: Throwable) {
        if (status.code() == 500) {
            LOG.error("Failure status=\"{}\" during request={}, clientAddress={}", status, formatter.formatRequest(request), clientAddress, cause)
        } else {
            proxyErrorOccurred(status, cause)
        }
    }

    override fun proxyErrorOccurred(cause: Throwable) {
        LOG.error("Error occurred during proxying", cause)
    }

    override fun proxyWriteFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable) {
        LOG.error(
            "Error writing response. request={}, response={}, cause={}",
            formatter.formatRequest(request),
            formatter.formatResponse(response),
            cause
        )
    }

    override fun proxyingFailure(request: LiveHttpRequest, response: LiveHttpResponse, cause: Throwable) {
        LOG.error(
            "Error proxying request. request={} response={} cause={}",
            formatter.formatRequest(request),
            formatter.formatResponse(response),
            cause
        )
    }
}

private fun withoutStackTrace(cause: Throwable): String {
    val builder = StringBuilder(cause.toString())
    var head: Throwable? = cause

    while (head != null) {
        builder.append(", cause=").append('"').append(head).append('"')
        head = head.cause
    }

    return builder.toString()
}