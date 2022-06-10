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

/**
 * A listener interface for progression of HTTP requests.
 */
interface RequestProgressListener {
    /**
     * To be called when a request begins.
     *
     * @param requestId a unique ID to identify the request
     */
    fun onRequest(requestId: Any)

    /**
     * To be called when a request completes successfully.
     * If onComplete or onTerminate has already been called. This method should do nothing.
     *
     * @param requestId a unique ID to identify the request
     * @param responseStatus the status code of the response
     */
    fun onComplete(requestId: Any, responseStatus: Int)

    /**
     * To be called when a request is terminated after being unable to complete successfully.
     * If onComplete or onTerminate has already been called. This method should do nothing.
     *
     * @param requestId a unique ID to identify the request
     */
    fun onTerminate(requestId: Any)

    companion object {
        @JvmField
        val IGNORE_REQUEST_PROGRESS: RequestProgressListener = object : RequestProgressListener {
            override fun onRequest(requestId: Any) {}
            override fun onComplete(requestId: Any, responseStatus: Int) {}
            override fun onTerminate(requestId: Any) {}
        }
    }
}