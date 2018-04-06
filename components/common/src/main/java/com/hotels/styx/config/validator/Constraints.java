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
package com.hotels.styx.config.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.Set;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An utility class providing schema validation constraints.
 */
public final class Constraints {

    private Constraints() {
    }

    /**
     * A constraint to ensure at least one of many specified must be present in the validated object.
     */
    public static class AtLeastOneFieldPresenceConstraint implements Constraint {
        private Set<String> fieldNames;

        public AtLeastOneFieldPresenceConstraint(Schema.Field... fields) {
            this.fieldNames = ImmutableList.copyOf(fields)
                    .stream()
                    .map(Schema.Field::name)
                    .collect(toSet());
        }

        @Override
        public boolean evaluate(Schema schema, JsonNode node) {
            long fieldsPresent = ImmutableList.copyOf(node.fieldNames())
                    .stream()
                    .filter(name -> fieldNames.contains(name))
                    .count();

            return fieldsPresent >= 1;
        }

        @Override
        public String message() {
            String displayNames = Joiner.on(", ").join(
                    fieldNames.stream()
                            .map(name -> format("'%s'", name))
                            .collect(toList()));
            return format("At least one of (%s) must be present.", displayNames);
        }
    }
}
