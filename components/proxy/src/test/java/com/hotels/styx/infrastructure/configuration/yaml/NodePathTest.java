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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Throwables.propagate;
import static com.hotels.styx.support.matchers.IsOptional.isPresent;
import static com.hotels.styx.support.matchers.IsOptional.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NodePathTest {
    String yaml = "" +
            "text: \"abc\"\n" +
            "array:\n" +
            "- \"alpha\"\n" +
            "- beta\n" +
            "- \"gamma\"\n" +
            "object:\n" +
            "  field1: foo\n" +
            "  field2: bar\n" +
            "array2:\n" +
            "- arrayObjectField1: abc\n" +
            "  arrayObjectField2: def\n";

    JsonNode root = parseYaml(yaml);

    @Test
    public void canGetTopLevelProperty() {
        assertThat(new NodePath("text").findMatchingDescendant(root), matches(isTextNode("abc")));
    }

    @Test
    public void canGetArray() {
        Optional<JsonNode> node = new NodePath("array").findMatchingDescendant(root);
        assertThat(node, isPresent());

        List<JsonNode> elements = elements(node.get());
        assertThat(elements.size(), is(3));
        assertThat(elements.get(0), isTextNode("alpha"));
        assertThat(elements.get(1), isTextNode("beta"));
        assertThat(elements.get(2), isTextNode("gamma"));
    }

    @Test
    public void canGetArrayElements() {
        assertThat(new NodePath("array[0]").findMatchingDescendant(root), matches(isTextNode("alpha")));
        assertThat(new NodePath("array[1]").findMatchingDescendant(root), matches(isTextNode("beta")));
        assertThat(new NodePath("array[2]").findMatchingDescendant(root), matches(isTextNode("gamma")));
    }

    @Test
    public void canGetObjectField() {
        assertThat(new NodePath("object.field1").findMatchingDescendant(root), matches(isTextNode("foo")));
        assertThat(new NodePath("object.field2").findMatchingDescendant(root), matches(isTextNode("bar")));
    }

    @Test
    public void canGetFieldsWithinArrayElementObjects() {
        assertThat(new NodePath("array2[0].arrayObjectField1").findMatchingDescendant(root), matches(isTextNode("abc")));
        assertThat(new NodePath("array2[0].arrayObjectField2").findMatchingDescendant(root), matches(isTextNode("def")));
    }

    private static List<JsonNode> elements(JsonNode node) {
        return ImmutableList.copyOf(node::elements);
    }

    private static JsonNode parseYaml(String yaml) {
        try {
            return new ObjectMapper(new YAMLFactory()).readTree(yaml);
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    public static TextNodeMatcher isTextNode(String text) {
        return new TextNodeMatcher(text);
    }

    private static final class TextNodeMatcher extends TypeSafeMatcher<JsonNode> {
        private final String expected;

        public TextNodeMatcher(String expected) {
            this.expected = expected;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("TextNode of(" + expected + ")");
        }

        @Override
        protected void describeMismatchSafely(JsonNode node, Description mismatchDescription) {
            if (!node.isTextual()) {
                mismatchDescription.appendText("Non-textual node (" + node.getNodeType() + ":" + node + ")");
            } else {
                mismatchDescription.appendText("TextNode of(" + node.textValue() + ")");
            }
        }

        @Override
        protected boolean matchesSafely(JsonNode node) {
            return node.isTextual() && Objects.equals(node.textValue(), expected);
        }
    }
}