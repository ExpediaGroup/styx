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
package com.hotels.styx.config.schema;

/**
 * Thrown during schema object construction to indicate an inconsistent schema.
 *
 * May be thrown when the schema object is being built to indicate an internal inconsistency
 * within a schema object. Such inconsistency may be a named reference to a non-existing schema,
 * duplicate field names, and so on.
 */
public class InvalidSchemaException extends RuntimeException {
    public InvalidSchemaException(String message) {
        super(message);
    }
}
