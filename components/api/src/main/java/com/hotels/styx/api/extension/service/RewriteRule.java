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
package com.hotels.styx.api.extension.service;


import java.util.Optional;

/**
 * Rule for URL rewrites - checks whether the url passed matches the rule, and if so rewrites it. Otherwise returns absent.
 */
public interface RewriteRule {
    /**
     * If the rule matches, returns Optional of rewritten url, otherwise returns absent.
     *
     * @param originalUri original uri
     * @return rewritten uri or absent
     */
    Optional<String> rewrite(String originalUri);
}
