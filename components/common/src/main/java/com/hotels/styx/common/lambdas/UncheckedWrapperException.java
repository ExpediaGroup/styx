/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.common.lambdas;

import static java.util.Objects.requireNonNull;

/**
 * A wrapper for exceptions when transforming lambdas with exceptions into java SL lambdas.
 * Note that unlike a typical exception, this only wraps Exceptions, not other Throwables.
 */
public class UncheckedWrapperException extends RuntimeException {
    private final Exception wrapped;

    UncheckedWrapperException(Exception wrap) {
        super(wrap);
        this.wrapped = requireNonNull(wrap);
    }

    public Exception wrapped() {
        return wrapped;
    }
}
