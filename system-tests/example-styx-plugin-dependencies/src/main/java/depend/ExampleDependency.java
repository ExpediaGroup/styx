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
package depend;

/**
 * A project can attempt to reference this to prove the dependency relationship is in effect.
 */
public final class ExampleDependency {
    // This is intentionally left non-final to prevent the compiler from inlining it.
    public static String exampleDependencyProperty = "Example-Dependency-Available";

    private ExampleDependency() {
    }
}
