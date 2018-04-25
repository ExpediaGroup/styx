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
package com.hotels.styx.infrastructure

import com.hotels.styx.api.FullHttpResponse


trait HttpResponseImplicits {

  class RichHttpResponse(val response: FullHttpResponse) {

    def isNotCacheAble(): Boolean = {
      response.header("Pragma").get().equals("no-cache") &&
        response.header("Expires").get().equals("Mon, 1 Jan 2007 08:00:00 GMT") &&
        response.header("Cache-Control").get().equals("no-cache,must-revalidate,no-store")
    }

  }

  implicit def toRichHttpResponse(response: FullHttpResponse): RichHttpResponse = {
    new RichHttpResponse(response)
  }
}
