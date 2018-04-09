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
package com.hotels.styx.api.configuration;

/**
 * An exception thrown when an attempt at converting a value from one type to another failed.
 */
public class ConversionException extends RuntimeException {
    /**
     * Constructs a ConversionException with detailed message.
     *
     * @param message exception message
     */
    public ConversionException(String message) {
        super(message);
    }

    /**
     * Constructs a ConversionException with detailed message and cause.
     *
     * @param message exception message
     * @param cause   exception that caused this exception
     */
    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a ConversionException with the cause.
     *
     * @param cause exception that caused this exception
     */
    public ConversionException(Throwable cause) {
        super(cause);
    }
}
