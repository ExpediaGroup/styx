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
package com.hotels.styx.spi;

/**
 * Exception that is thrown if something goes wrong while attempting to load and instantiate an extension object.
 */
public class ExtensionLoadingException extends RuntimeException {
    public ExtensionLoadingException() {
    }

    public ExtensionLoadingException(String message) {
        super(message);
    }

    public ExtensionLoadingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExtensionLoadingException(Throwable cause) {
        super(cause);
    }
}
