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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An additional requirement, or constraint, for the JSON document.
 *
 * A constraint could describe a particular relationship between object's
 * fields.
 */
public interface Constraint extends SchemaDirective {
    /**
     * Evaluates a constraint associated with `schema` against the JSON `node`
     *
     * A constraint specifies a rule which the parsed Json/Yaml object must
     * satisfy in order to be considered conformant. An example rule might be
     * "At least one of (a, b, c) fields must be present."
     *
     * @param schema Json object schema
     * @param node   Parsed JSON object
     * @return       true if `node` conforms to the constraint, false otherwise
     */
    boolean evaluate(Schema schema, JsonNode node);

    /**
     * An informational description of the constraint. This can be used
     * for example in the error messages to provide more information about
     * validation failures, or perhaps to generate end-user documentation.
     *
     * @return  An contraint description.
     */
    String describe();
}
