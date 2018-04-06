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
package com.hotels.styx.infrastructure.configuration.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Array index - a type of path element.
 */
final class ArrayIndex implements PathElement {
    private final int index;

    public ArrayIndex(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    @Override
    public void setChild(JsonNode parent, JsonNode child) {
        ((ArrayNode) parent).set(index, child);
    }

    @Override
    public JsonNode child(JsonNode parent) {
        return parent.get(index);
    }

    @Override
    public boolean isArrayIndex() {
        return true;
    }

    @Override
    public boolean isObjectField() {
        return false;
    }

    @Override
    public String toString() {
        return Integer.toString(index);
    }
}
