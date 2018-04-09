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
package com.hotels.styx.admin.handlers.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.padStart;
import static com.google.common.base.Throwables.propagate;
import static java.util.stream.Collectors.joining;

/**
 * Reformats JSON.
 */
public final class JsonReformatter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Always uses unix-style line separators regardless of platform
    private static final String LINE_SEPARATOR = "\n";

    private JsonReformatter() {
    }

    public static String reformat(String json) {
        try {
            return reformat0(json);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private static String reformat0(String json) throws IOException {
        Tree tree = new Tree();

        MAPPER.readValue(json, Map.class).forEach((key, value) ->
                tree.add(String.valueOf(key), value));

        return tree.pretty();
    }

    private static class Tree {
        private static final Pattern IS_NUMBER = Pattern.compile("-?([0-9]+.)?[0-9]+");

        private final Map<String, Tree> children;
        private final Object value;

        Tree() {
            this(null);
        }

        Tree(Object value) {
            children = new TreeMap<>();
            this.value = value;
        }

        public void add(String name, Object value) {
            int firstDot = name.indexOf('.');

            if (firstDot == -1) {
                Tree tree;

                if (value instanceof Map) {
                    tree = new Tree();

                    ((Map<?, ?>) value).forEach((k, v) -> tree.add(String.valueOf(k), v));
                } else {
                    tree = new Tree(value);
                }

                children.put(name, tree);
            } else {
                String prefix = name.substring(0, firstDot);
                String suffix = name.substring(firstDot + 1, name.length());

                Tree tree = children.computeIfAbsent(prefix, k -> new Tree());
                tree.add(suffix, value);
            }
        }

        public String pretty() {
            return pretty(0);
        }

        private String pretty(int indentation) {
            String indent = indent(indentation);
            String indent1 = indent(indentation + 1);

            StringBuilder json = new StringBuilder();

            if (value != null) {
                if (indentation > 0) {
                    json.append("\":");
                }

                if (!isNumber(String.valueOf(value))) {
                    json.append('"').append(value).append('"');
                } else {
                    json.append(value);
                }
            } else {
                if (children.size() == 1) {
                    Map.Entry<String, Tree> entry = children.entrySet().stream().findFirst().orElseThrow(IllegalStateException::new);

                    if (indentation == 0) {
                        json.append("{\"");
                    } else {
                        json.append(".");
                    }

                    json
                            .append(entry.getKey())
                            .append(entry.getValue().pretty(indentation + 1));

                    if (indentation == 0) {
                        json.append("}");
                    }
                } else {
                    if (indentation > 0) {
                        json.append("\":");
                    }

                    json
                            .append("{")
                            .append(LINE_SEPARATOR)
                            .append(children.entrySet().stream()
                                    .map(entry -> indent1 + '"'
                                            + entry.getKey()
                                            + entry.getValue().pretty(indentation + 1)
                                    ).collect(joining("," + LINE_SEPARATOR)))
                            .append(LINE_SEPARATOR);

                    if (indentation > 0) {
                        json.append(indent);
                    }

                    json.append("}");
                }
            }

            return json.toString();
        }

        private static boolean isNumber(String s) {
            return IS_NUMBER.matcher(s).matches();
        }

        private static String indent(int indentation) {
            return padStart("", indentation * 2, ' ');
        }
    }
}
