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
package com.hotels.styx.api;

class ContentEventInternal implements ContentStream.ContentEvent {
    private Buffer buffer;

    public ContentEventInternal(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int size() {
        return buffer.size();
    }

    @Override
    public Buffer content() {
        return buffer;
    }
}
