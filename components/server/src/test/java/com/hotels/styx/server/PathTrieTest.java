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

import org.testng.annotations.Test;

import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class PathTrieTest {
    @Test
    public void mapPathComponents() {
        PathTrie<Integer> pathTrie = new PathTrie<>();

        pathTrie.put("/foo/", 1);
        pathTrie.put("/foo", 2);

        assertThat(pathTrie.get("/foo"), isValue(2));
        assertThat(pathTrie.get("/foo/"), isValue(1));
        assertThat(pathTrie.get("/foo/bar"), isValue(1));
    }

    @Test
    public void mapExactlyMatchingUrls() {
        PathTrie<Integer> pathTrie = defaultPathTrie();

        assertThat(pathTrie.get("/"), isValue(1));
        assertThat(pathTrie.get(""), isValue(1));

        assertThat(pathTrie.get("/a/b"), isValue(2));
        assertThat(pathTrie.get("/a/b/"), isValue(1));

        assertThat(pathTrie.get("/a/c"), isValue(3));
        assertThat(pathTrie.get("/a/c/"), isValue(1));

        assertThat(pathTrie.get("/a/b/c"), isValue(4));
        assertThat(pathTrie.get("/a/b/c/"), isValue(1));
    }

    @Test
    public void matchesDefaultWhenNothingElseMatches() {
        PathTrie<Integer> pathTrie = defaultpathTrieWithStar();

        // The tree has "/a/b/*" configured, but not "/a/b". Therefore asserts.
        assertThat(pathTrie.get("/a/b"), isValue(1));
    }

    @Test
    public void mapExactlyMatchingUrlsWithStarConfig() {
        PathTrie<Integer> pathTrie = defaultpathTrieWithStar();

        assertThat(pathTrie.get("/"), isValue(1));
        assertThat(pathTrie.get(""), isValue(1));

        assertThat(pathTrie.get("/a/b/"), isValue(2));
        assertThat(pathTrie.get("/a/c/"), isValue(3));
        assertThat(pathTrie.get("/a/b/c/"), isValue(4));
    }

    @Test
    public void nonMatchingUrlsMapToRoot() {
        PathTrie<Integer> pathTrie = defaultPathTrie();

        assertThat(pathTrie.get("foobar"), isValue(1));
        assertThat(pathTrie.get("/d"), isValue(1));
        assertThat(pathTrie.get("/d/"), isValue(1));
        assertThat(pathTrie.get("/d/foo"), isValue(1));
    }

    @Test
    public void nonMatchingUrlsMapToRootWithStarConfig() {
        PathTrie<Integer> pathTrie = defaultpathTrieWithStar();

        assertThat(pathTrie.get("foobar"), isValue(1));
        assertThat(pathTrie.get("/d"), isValue(1));
        assertThat(pathTrie.get("/d/"), isValue(1));
        assertThat(pathTrie.get("/d/foo"), isValue(1));
    }

    @Test
    public void partiallyMatchingUrlsMapToRootWhenApplicationIdNotAvailable() {
        PathTrie<Integer> pathTrie = defaultPathTrie();

        assertThat(pathTrie.get("/a/bar"), isValue(1));
        assertThat(pathTrie.get("/a/foo"), isValue(1));
    }

    @Test
    public void partiallyMatchingUrlsMapToRootWhenApplicationIdNotAvailableWithStarConfig() {
        PathTrie<Integer> pathTrie = defaultpathTrieWithStar();

        assertThat(pathTrie.get("/a/bar"), isValue(1));
        assertThat(pathTrie.get("/a/foo"), isValue(1));
    }

    @Test
    public void mapToBestMatch() {
        PathTrie<Integer> pathTrie = defaultPathTrie();

        assertThat(pathTrie.get("/a/b/foo"), isValue(1));
        assertThat(pathTrie.get("/a/b/foo/bar"), isValue(1));

        assertThat(pathTrie.get("/a/c/foo"), isValue(1));
        assertThat(pathTrie.get("/a/c/foo/bar"), isValue(1));

        assertThat(pathTrie.get("/a/b/c/foo"), isValue(1));
        assertThat(pathTrie.get("/a/b/c/foo/bar"), isValue(1));
    }

    @Test
    public void mapToBestMatchWithStarConfig() {
        PathTrie<Integer> pathTrie = defaultpathTrieWithStar();

        assertThat(pathTrie.get("/a/b/foo"), isValue(2));
        assertThat(pathTrie.get("/a/b/foo/bar"), isValue(2));

        assertThat(pathTrie.get("/a/c/foo"), isValue(3));
        assertThat(pathTrie.get("/a/c/foo/bar"), isValue(3));

        assertThat(pathTrie.get("/a/b/c/foo"), isValue(4));
        assertThat(pathTrie.get("/a/b/c/foo/bar"), isValue(4));
    }

    @Test(expectedExceptions = DuplicatePathException.class)
    public void failWhenIdenticalPathsConfigured() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/a/b/c", 1);
        pathTrie.put("/a/b/c", 2);
    }

    @Test(expectedExceptions = DuplicatePathException.class)
    public void failWhenIdenticalPathsConfiguredWithStar() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/a/b/c/", 1);
        pathTrie.put("/a/b/c/*", 2);
    }

    @Test
    public void fileAndPathElementsMapToSeparateUrls() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/a/b/c", 1);
        pathTrie.put("/a/b/c/", 2);

        assertThat(pathTrie.get("/a/b/c"), isValue(1));
        assertThat(pathTrie.get("/a/b/c/"), isValue(2));
        assertThat(pathTrie.get("/a/b/c/d"), isValue(2));
    }

    @Test(expectedExceptions = DuplicatePathException.class)
    public void failWhenIdenticalPathsConfiguredInRoot() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/", 1);
        pathTrie.put("/", 2);
    }

    @Test(expectedExceptions = DuplicatePathException.class)
    public void failWhenIdenticalPathsConfiguredInRootWithStar() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/", 1);
        pathTrie.put("/*", 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void failWhenPathIsNull() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put(null, 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void failWhenPathIsEmpty() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("", 1);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void failWhenValueIsNull() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/", null);
    }

    @Test
    public void failWhenMatchingIdNotFound() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/foo", 1);

        assertThat(pathTrie.get("/bar"), isAbsent());
    }

    @Test
    public void removeAddedPath() {
        PathTrie<Integer> pathTrie = new PathTrie<>();
        pathTrie.put("/foo", 1);
        pathTrie.remove("/foo");

        assertThat(pathTrie.get("/foo"), isAbsent());
    }

    private static PathTrie<Integer> defaultPathTrie() {
        PathTrie<Integer> pathTrie = new PathTrie<>();

        pathTrie.put("/a/b/c", 4);
        pathTrie.put("/a/c", 3);
        pathTrie.put("/a/b", 2);
        pathTrie.put("/", 1);

        return pathTrie;
    }

    private static PathTrie<Integer> defaultpathTrieWithStar() {
        PathTrie<Integer> pathTrie = new PathTrie<>();

        pathTrie.put("/a/b/c/*", 4);
        pathTrie.put("/a/c/*", 3);
        pathTrie.put("/a/b/*", 2);
        pathTrie.put("/*", 1);

        return pathTrie;
    }
}
