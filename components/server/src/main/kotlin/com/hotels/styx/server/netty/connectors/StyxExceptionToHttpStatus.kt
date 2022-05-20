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
package com.hotels.styx.server.netty.connectors

import com.hotels.styx.api.ContentOverflowException
import com.hotels.styx.api.HttpResponseStatus
import com.hotels.styx.api.HttpResponseStatus.BAD_GATEWAY
import com.hotels.styx.api.HttpResponseStatus.BAD_REQUEST
import com.hotels.styx.api.HttpResponseStatus.GATEWAY_TIMEOUT
import com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR
import com.hotels.styx.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE
import com.hotels.styx.api.HttpResponseStatus.REQUEST_TIMEOUT
import com.hotels.styx.api.HttpResponseStatus.SERVICE_UNAVAILABLE
import com.hotels.styx.api.exceptions.NoAvailableHostsException
import com.hotels.styx.api.exceptions.OriginUnreachableException
import com.hotels.styx.api.exceptions.ResponseTimeoutException
import com.hotels.styx.api.exceptions.TransportLostException
import com.hotels.styx.client.BadHttpResponseException
import com.hotels.styx.client.StyxClientException
import com.hotels.styx.client.connectionpool.ResourceExhaustedException
import com.hotels.styx.server.BadRequestException
import com.hotels.styx.server.NoServiceConfiguredException
import com.hotels.styx.server.RequestTimeoutException
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.TooLongFrameException

object StyxExceptionToHttpStatus {
    private val EXCEPTION_STATUSES = buildExceptionStatusMapper {
        add(REQUEST_TIMEOUT, RequestTimeoutException::class.java)
            .add(
                BAD_GATEWAY,
                OriginUnreachableException::class.java,
                NoAvailableHostsException::class.java,
                NoServiceConfiguredException::class.java,
                BadHttpResponseException::class.java,
                ContentOverflowException::class.java,
                TransportLostException::class.java
            )
            .add(SERVICE_UNAVAILABLE, ResourceExhaustedException::class.java)
            .add(GATEWAY_TIMEOUT, ResponseTimeoutException::class.java)
            .add(INTERNAL_SERVER_ERROR, StyxClientException::class.java)
            .add(REQUEST_ENTITY_TOO_LARGE, TooLongFrameException::class.java)
            .add(BAD_REQUEST, BadRequestException::class.java)
    }

    fun status(exception: Throwable): HttpResponseStatus = EXCEPTION_STATUSES.statusFor(exception.mostRelevantCause())
        .orElse(INTERNAL_SERVER_ERROR)

    private fun Throwable.mostRelevantCause() =
        (this as? DecoderException)?.let {
            (cause as? BadRequestException)?.let {
                (it.cause as? TooLongFrameException) ?: it
            }
        } ?: this
}