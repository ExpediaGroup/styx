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
package com.hotels.styx.common;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Represents the outcome of attempting to do something.
 * On a success a value is stored (defaults to "success" if not specified).
 * On a failure an exception is stored (defaults to a new Exception if not specified).
 *
 * @param <T> type of success value
 */
public class Result<T> {
    private final boolean success;
    private final T valueOnSuccess;
    private final Exception causeOnFailure;

    private Result(boolean success, T valueOnSuccess, Exception causeOnFailure) {
        this.success = success;
        this.valueOnSuccess = valueOnSuccess;
        this.causeOnFailure = causeOnFailure;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(true, requireNonNull(value), null);
    }

    public static Result<String> success() {
        return success("success");
    }

    public static <T> Result<T> failure(Exception e) {
        return new Result<>(false, null, requireNonNull(e));
    }

    public static <T> Result<T> failure() {
        return failure(new Exception("Failure"));
    }

    public Optional<T> successValue() {
        return Optional.ofNullable(valueOnSuccess);
    }

    public <R> Result<R> mapSuccess(Function<T, R> mapper) {
        return success
                ? success(mapper.apply(valueOnSuccess))
                : (Result<R>) this;
    }

    public Result<T> defaultOnFailure(Function<Exception, T> mapper) {
        return success
                ? this
                : success(mapper.apply(causeOnFailure));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Result<?> result = (Result<?>) o;
        return success == result.success
                && Objects.equals(valueOnSuccess, result.valueOnSuccess)
                && Objects.equals(causeOnFailure, result.causeOnFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, valueOnSuccess, causeOnFailure);
    }

    @Override
    public String toString() {
        return format("%s[%s]", success ? "SUCCESS" : "FAILURE", success ? valueOnSuccess : causeOnFailure);
    }
}
