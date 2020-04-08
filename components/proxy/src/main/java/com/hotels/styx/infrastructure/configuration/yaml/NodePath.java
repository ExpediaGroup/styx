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
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Splitter;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Integer.parseInt;
import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Node path.
 */
public class NodePath {
    private final List<PathElement> elements;

    public NodePath(String path) {
        this.elements = splitPath(path);
        checkArgument(!this.elements.isEmpty(), "No elements in %s", path);
    }

    NodePath(List<PathElement> elements) {
        this.elements = elements;
    }

    public List<PathElement> elements() {
        return elements;
    }

    public PathElement lastElement() {
        return elements.get(elements.size() - 1);
    }

    public Optional<JsonNode> findMatchingDescendant(JsonNode rootNode) {
        JsonNode current = rootNode;

        for (PathElement element : elements) {
            current = element.child(current);

            if (current == null) {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(current);
    }

    // Note that if no node exists at this path relative to the root, this method will do nothing.
    public boolean override(JsonNode rootNode, JsonNode leaf) {
        JsonNode current = rootNode;
        JsonNode parent = null;

        for (PathElement element : elements) {
            parent = current;
            current = element.child(parent);

            if (current == null) {
                return false;
            }
        }

        lastElement().setChild(parent, leaf);
        return true;
    }

    public boolean override(JsonNode rootNode, String value) {
        return override(rootNode, TextNode.valueOf(value));
    }

    private static List<PathElement> splitPath(String path) {
        Splitter leftBracketSplitter = Splitter.on('[');
        Splitter dotSplitter = Splitter.on('.');

        Iterable<String> splitOnDot = dotSplitter.split(path);

        return stream(splitOnDot.spliterator(), false)
                .map(leftBracketSplitter::split)
                .flatMap(splitByLeftBracket -> stream(splitByLeftBracket.spliterator(), false))
                .map(input -> {
                    if (input.endsWith("]")) {
                        return new ArrayIndex(parseInt(input.substring(0, input.length() - 1)));
                    }

                    return new ObjectField(input);
                }).collect(toList());
    }

    @Override
    public String toString() {
        String path = join("", elements.stream()
                .map(input -> input.isArrayIndex() ? "[" + input + "]" : "." + input)
                .collect(toList()));

        if (path.startsWith(".")) {
            path = path.substring(1);
        }

        return path;
    }
}
