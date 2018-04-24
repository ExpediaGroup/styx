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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

/**
 * A constraint to ensure at least one of many specified fields is mandatory.
 */
class AtLeastOneFieldPresenceConstraint implements Constraint {
    private final Set<String> fieldNames;
    private final String description;

    AtLeastOneFieldPresenceConstraint(String... fieldNames) {
        this.fieldNames = ImmutableSet.copyOf(fieldNames);
        this.description = description(fieldNames);
    }

    private static String description(String[] fieldNames) {
        String displayNames = stream(fieldNames)
                .map(name -> format("'%s'", name))
                .collect(joining(", "));
        return format("At least one of (%s) must be present.", displayNames);
    }

    @Override
    public boolean evaluate(Schema schema, JsonNode node) {
        long fieldsPresent = ImmutableList.copyOf(node.fieldNames())
                .stream()
                .filter(fieldNames::contains)
                .count();

        return fieldsPresent >= 1;
    }

    @Override
    public String describe() {
        return description;
    }
}
