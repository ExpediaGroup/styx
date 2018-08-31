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
package com.hotels.styx.server;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Stores URL path string and ID mappings for a longest match retrieval. The
 * mappings are configured with addEntry() method, while the idOf() method
 * performs the best match ID retrieval. A configured path is considered "best"
 * when it has the longest path prefix with the path presented for the idOf()
 * method.
 *
 * An application ID associated with path "/" or "/*" is a default path.
 * If a path presented to idOf() doesn't match with anything else, then an
 * application ID associated with default path is returned.
 *
 * Note about URL mappings:
 *
 * 1. Trailing "/" in a configured path denotes (as you might expect) a
 *    sub-path. Any matching URL must be a "longer" match to be considered.
 *    For example, consider a mapping "/foo/ -> 2". Now, an idOf("foo/bar/blah"),
 *    idOf("/foo/") will return a match==2, but idOf("/foo") would not.
 *
 * 2. Note that trailing "/" and "/*" are equivalent.
 *
 * 3. A configured path that does not end with "/" or "/*" must be matched
 *    exactly. For example, a mapping "/foo/bar -> 2" will only match with
 *    path "/foo/bar", but not with "/foo/bar/" nor "/foo/bar/baz".
 *
 * Implementation notes:
 *
 * - Paths are stored in a tree structure to facilitate longest matching
 *   prefix searches.
 *
 * - Each node represents an individual name component in a path. Each leaf
 *   node in a tree is guaranteed to have an ID configured as a value. This
 *   guarantee does not hold for intermediate nodes. An intermediate node may
 *   or may not contain a configured ID value. Only nodes that have an ID
 *   configured will be considered being part of a successful longest-prefix
 *   match. For example:
 *
 *      Mappings "/foo/bar/A -> 1" and "foo/bar/B -> 2" will result in a
 *      following, where intermediate nodes "foo" and "bar" do not have an
 *      associated application id.
 *
 *              [root]
 *                |
 *               foo
 *                |
 *               bar
 *             /    \
 *            A(1)   B(2)
 *
 * - To differentiate between sub-path matching vs. exact path matching, any
 *   sub-path mappings are stored in a special node "/" under the relevant name
 *   node. For example, consider mappings:
 *
 *       addEntry("/foo/bar", 1);
 *       addEntry("/foo/bar/", 2);
 *
 *   This would result in a following tree:
 *
 *         [root]
 *           |
 *         "foo"
 *           |
 *         "bar" (= 1)  <- matches "/foo/bar" only
 *           |
 *          "/"  (= 2)  <- matches any sub-path of "/foo/bar/..."
 *
 * @param <T> the type of mapped values
 */
public class PathTrie<T> {
    private static final Logger LOGGER = getLogger(PathTrie.class);

    private final MatchTree<T> tree;

    /**
     * Construct a new matcher.
     */
    public PathTrie() {
        this.tree = new MatchTree<>();
    }

    /**
     * Add a new entry.
     *
     * @param path  path as string
     * @param value value to map path to
     */
    public void put(String path, T value) {
        checkArgument(!isNullOrEmpty(path));
        requireNonNull(value);

        List<String> components = pathToComponents(Paths.get(removeAsterisk(path)));

        if (path.endsWith("/") || path.endsWith("/*")) {
            // it is a directory
            components.add("/");
        }

        T existing = tree.getExactMatch(components);
        if (existing != null) {
            String message = format("Path '%s' has already been configured with a ID of [%s]",
                    path, existing.toString());
            throw new DuplicatePathException(message);
        }

        tree.addValueWithParents(components, value);
    }

    /**
     * ID value associated with path.
     *
     * @param path path as string
     * @return associated ID if existent
     */
    public Optional<T> get(String path) {
        List<String> components = getPathComponents(path);

        MatchTreeNode<T> node = tree.getLongestMatchingNode(components);
        if (node == null) {
            return Optional.empty();
        }

        T value;
        if (isExactlyMatchingPath(node, path, components)) {
            // exact match:
            value = node.value();
            if (value == null) {
                value = bestValue(node.parent());
            }
        } else {
            value = bestValue(node);
        }

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    public T remove(String path) {
        List<String> components = getPathComponents(path);

        MatchTreeNode<T> node = tree.getLongestMatchingNode(components);
        T t = bestValue(node);
        if (node != null) {
            node.delete();
        }

        return t;
    }

    private List<String> getPathComponents(String path) {
        if (path.length() == 0) {
            return new ArrayList<>();
        } else if (path.charAt(0) == '/') {
            return asList(path.substring(1).split("/"));
        } else {
            return asList(path.split("/"));
        }
    }

    //
    // Walk up the tree to find a perfect match:
    //
    private T bestValue(MatchTreeNode<T> node) {
        T value = null;
        if (node != null) {
            if (node.child("/") != null) {
                value = node.child("/").value();
            } else {
                value = bestValue(node.parent());
            }
        }
        return value;
    }

    //
    // Returns true if given path component is an exactly matching path with
    // given node. Ie, it is not a sub-path of a given node.
    //
    //                            a       b       /
    //  Node refers to:   [root]-----(A)-----(A)-----(b/)
    //
    // isExactlyMatchingPath(A, "/a/b") -> true
    // isExactlyMatchingPath(B, "/a/b/") -> false
    //
    private boolean isExactlyMatchingPath(MatchTreeNode<T> node, String path, List<String> components) {
        boolean exactlyMatching = false;

        if (node.level() == components.size()
                && lastName(components).equals(node.name())
                && !path.endsWith("/") && !path.endsWith("/*")) {
            exactlyMatching = true;
        }

        return exactlyMatching;
    }

    //
    // Return last path element name, or an empty string if necessary
    //
    private String lastName(List<String> components) {
        int size = components.size();

        return size > 0 ? components.get(size - 1) : "";
    }

    private List<String> pathToComponents(Path path) {
        List<String> components = new LinkedList<>();
        for (int i = 0; i < path.getNameCount(); i++) {
            String name = path.getName(i).toString();
            components.add(name);
        }

        return components;
    }

    private String removeAsterisk(String path) {
        String newPath = path;
        if (path.endsWith("/*")) {
            newPath = path.substring(0, path.length() - 1);
        }
        return newPath;
    }

    public void printContent() {
        String text = tree.printTree();
        LOGGER.debug(text);
    }

    private static class MatchTree<T> {
        private final MatchTreeNode<T> root;

        MatchTree() {
            root = new MatchTreeNode<>("/", null);
        }

        public void addValueWithParents(List<String> path, T value) {
            MatchTreeNode<T> node = root;

            for (String name : path) {
                MatchTreeNode<T> child = node.child(name);
                if (child == null) {
                    child = node.newChild(name);
                }
                node = child;
            }

            node.value(value);
        }

        public T getExactMatch(List<String> path) {
            MatchTreeNode<T> node = root;

            for (String name : path) {
                node = node.child(name);
                if (node == null) {
                    break;
                }
            }

            return node != null ? node.value() : null;
        }

        //
        // Retrieves a best match with a configured value:
        //
        //  eg.    "/"     -> application id: 1
        //         "/a/b/c -> application id: 2
        //
        //  Url "/a/d/" should map to application 1.
        //
        public MatchTreeNode<T> getLongestMatchingNode(List<String> path) {
            MatchTreeNode<T> node = root;

            for (String name : path) {
                MatchTreeNode<T> child = node.child(name);
                if (child == null) {
                    break;
                }
                node = child;
            }

            return node;
        }

        public String printTree() {
            MatchTreeNode<T> node = root;
            StringBuilder sb = new StringBuilder();
            sb.append('\n');
            printTreeInternal(node, 0, sb);
            return sb.toString();
        }

        private String printTreeInternal(MatchTreeNode<T> node, int level, StringBuilder sb) {
            sb.append(getIndent(level));
            sb.append(format("%s: %s\n", node.name(),
                    node.value() == null ? "null" : node.value().toString()));

            for (String name : node.children()) {
                MatchTreeNode<T> child = node.child(name);
                printTreeInternal(child, level + 1, sb);
            }

            return sb.toString();
        }

        private String getIndent(int level) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
            return sb.toString();
        }
    }

    private static class MatchTreeNode<T> {
        private final String name;
        private final Map<String, MatchTreeNode<T>> children;
        private final MatchTreeNode<T> parent;
        private final int level;
        private T value;

        MatchTreeNode(String name, MatchTreeNode<T> parent) {
            this.name = name;
            this.children = new HashMap<>();
            this.value = null;
            this.parent = parent;
            if (parent == null) {
                this.level = 0;
            } else {
                this.level = parent.level() + 1;
            }
        }

        public MatchTreeNode<T> child(String name) {
            return this.children.get(name);
        }

        public MatchTreeNode<T> newChild(String name) {
            if (children.containsKey(name)) {
                throw new IllegalArgumentException(format("Duplicate child name in node '%s'.", this.name));
            }

            MatchTreeNode<T> childNode = new MatchTreeNode<>(name, this);
            children.put(name, childNode);

            return childNode;
        }

        public T value() {
            return value;
        }

        public void value(T value) {
            this.value = value;
        }

        public String name() {
            return this.name;
        }

        public List<String> children() {
            return new LinkedList<>(this.children.keySet());
        }

        public MatchTreeNode<T> parent() {
            return parent;
        }

        public int level() {
            return level;
        }

        public void delete() {
            if (parent != null) {
                parent.children.remove(name);
            }
        }
    }
}
