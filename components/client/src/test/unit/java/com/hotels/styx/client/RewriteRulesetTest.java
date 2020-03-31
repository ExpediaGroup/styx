/*
  Copyright (C) 2013-2020 Expedia Inc.

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
package com.hotels.styx.client;

import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.api.extension.service.RewriteRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hotels.styx.common.Collections.listOf;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RewriteRulesetTest {
    List<RewriteRule> config = asList(
            new RewriteConfig("/sp/foo/bar/(.*)", "/foo/$1"),
            new RewriteConfig("/sp/(.*)/bar/(.*)", "/$2/$1"),
            new RewriteConfig("/sp/(.*)", "/$1")
    );

    @Test
    public void returnsTheOriginalUrlPathWithEmptyConfiguration() {
        RewriteRuleset rewriter = new RewriteRuleset(emptyList());
        assertThat(rewriter.rewrite(requestWithUrl("/foo/bar")).path(), is("/foo/bar"));
    }

    @Test
    public void substitutesCaptureGroupIntoReplacementStringWhenUrlMatchesPattern() {
        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/sp/significant/path")).path(), is("/significant/path"));
    }

    @Test
    public void returnsTheOriginalUrlWhenPatternDoesNotMatch() {
        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/significant/path")).path(), is("/significant/path"));
    }

    @Test
    public void appliesUrlRewritesInConfiguredOrder() {
        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/sp/foo/bar/path")).path(), is("/foo/path"));
    }

    @Test
    public void returnsReplacementStringWhenItDoesNotReferToCaptureGroups() {
        List<RewriteRule> config = listOf(new RewriteConfig("/sp/(.*)", "/constant/replacement"));

        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/sp/foo/bar/path")).path(), is("/constant/replacement"));
    }

    @Test
    public void handlesPlaceholderLookalikesInNonCapturedParts() {
        List<RewriteRule> config = listOf(new RewriteConfig("/sp/(.*)/\\$1/(.*)", "/$2/$1"));

        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/sp/foo/$1/bar")).path(), is("/bar/foo"));
    }

    @Test
    public void handlesPlaceholderLookalikesInCapturedParts() {
        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/sp/$2/bar/path")).path(), is("/path/$2"));
    }

    @Test
    public void shouldNotModifyQueryParameters() throws Exception {
        RewriteRuleset rewriter = new RewriteRuleset(config);
        assertThat(rewriter.rewrite(requestWithUrl("/significant/path?a=b&c=d")).url().toString(), is("/significant/path?a=b&c=d"));
    }

    private LiveHttpRequest requestWithUrl(String url) {
        return LiveHttpRequest.get(url).build();
    }
}