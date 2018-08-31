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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Objects;
import com.hotels.styx.support.matchers.IsOptional;
import org.hamcrest.Description;
import org.hamcrest.MatcherAssert;
import org.hamcrest.TypeSafeMatcher;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.hotels.styx.infrastructure.configuration.yaml.JsonTreeTraversal.traverseJsonTree;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class JsonTreeTraversalTest {
    @Test
    public void canTraverseValueNodesInOrder() throws IOException {
        String yaml = "" +
                "object:\n" +
                "  field1: 234\n" +
                "  field2: value2\n" +
                "  arrayField:\n" +
                "  - arrayValue1\n" +
                "  - 757\n" +
                "  - arrayObjectValue: 1234\n";

        JsonNode rootNode = new ObjectMapper(new YAMLFactory()).readTree(yaml);

        List<Call> calls = new ArrayList<>();

        traverseJsonTree(rootNode, (node, parent, path) -> calls.add(new Call(node, parent, path)));

        MatcherAssert.assertThat(calls.get(0).path, contains(pathElements("object", "field1")));
        assertThat(calls.get(0).node, is(instanceOf(IntNode.class)));
        assertThat(calls.get(0).node.intValue(), is(234));
        MatcherAssert.assertThat(calls.get(0).parent, IsOptional.matches(instanceOf(ObjectNode.class)));

        MatcherAssert.assertThat(calls.get(1).path, contains(pathElements("object", "field2")));
        assertThat(calls.get(1).node, is(instanceOf(TextNode.class)));
        assertThat(calls.get(1).node.textValue(), is("value2"));
        MatcherAssert.assertThat(calls.get(1).parent, IsOptional.matches(instanceOf(ObjectNode.class)));

        MatcherAssert.assertThat(calls.get(2).path, contains(pathElements("object", "arrayField", 0)));
        assertThat(calls.get(2).node, is(instanceOf(TextNode.class)));
        assertThat(calls.get(2).node.textValue(), is("arrayValue1"));
        MatcherAssert.assertThat(calls.get(2).parent, IsOptional.matches(instanceOf(ArrayNode.class)));

        MatcherAssert.assertThat(calls.get(3).path, contains(pathElements("object", "arrayField", 1)));
        assertThat(calls.get(3).node, is(instanceOf(IntNode.class)));
        assertThat(calls.get(3).node.intValue(), is(757));
        MatcherAssert.assertThat(calls.get(3).parent, IsOptional.matches(instanceOf(ArrayNode.class)));

        MatcherAssert.assertThat(calls.get(4).path, contains(pathElements("object", "arrayField", 2, "arrayObjectValue")));
        assertThat(calls.get(4).node, is(instanceOf(IntNode.class)));
        assertThat(calls.get(4).node.intValue(), is(1234));
        MatcherAssert.assertThat(calls.get(4).parent, IsOptional.matches(instanceOf(ObjectNode.class)));
    }

    private static PathElementMatcher[] pathElements(Object... value) {
        PathElementMatcher[] matchers = new PathElementMatcher[value.length];

        for (int i = 0; i < value.length; i++) {
            matchers[i] = new PathElementMatcher(value[i]);
        }

        return matchers;
    }

    private static class PathElementMatcher extends TypeSafeMatcher<PathElement> {
        private final Object expected;

        public PathElementMatcher(Object expected) {
            checkArgument(expected.getClass() == Integer.class || expected.getClass() == String.class, String.valueOf(expected.getClass()));

            this.expected = expected;
        }

        @Override
        protected boolean matchesSafely(PathElement item) {
            if (item instanceof ArrayIndex) {
                ArrayIndex arrayIndex = (ArrayIndex) item;
                return Objects.equal(arrayIndex.index(), expected);
            }

            if (item instanceof ObjectField) {
                ObjectField objectField = (ObjectField) item;

                return Objects.equal(objectField.name(), expected);
            }

            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(expected);
        }
    }

    private static final class Call {
        private final ValueNode node;
        private final Optional<ContainerNode<?>> parent;
        private final List<PathElement> path;

        private Call(ValueNode node, Optional<ContainerNode<?>> parent, List<PathElement> path) {
            this.node = node;
            this.parent = parent;
            this.path = path;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                    .add("node", node)
                    .add("parent", parent)
                    .add("path", path)
                    .toString();
        }
    }
}