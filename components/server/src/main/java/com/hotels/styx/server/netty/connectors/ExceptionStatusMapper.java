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
package com.hotels.styx.server.netty.connectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.hotels.styx.api.HttpResponseStatus;

import java.util.Comparator;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static com.hotels.styx.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;

/**
 * Default list of exception mappers.
 */
public final class ExceptionStatusMapper {
    private final Multimap<HttpResponseStatus, FancyExceptionMatcher> multimap;

    private ExceptionStatusMapper(Builder builder) {
        this.multimap = ImmutableMultimap.copyOf(builder.multimap);
    }

    public HttpResponseStatus statusFor(Throwable throwable) {
        return matches(throwable)
                .limit(1)
                .map(Matched::status)
                .findAny()
                .orElse(INTERNAL_SERVER_ERROR);
    }

    @VisibleForTesting
    Stream<Matched> matches(Throwable error) {
        return this.multimap.entries().stream()
                .map(entry -> {
                    HttpResponseStatus status = entry.getKey();
                    FancyExceptionMatcher matcher = entry.getValue();

                    int matchLevel = matcher.matchLevel(error);

                    return new Matched(matchLevel, status);
                }).filter(matched -> matched.matchLevel() > 0)
                .sorted(reverse(comparingInt(Matched::matchLevel)));
    }

    // makes other code cleaner (the generics confused the compiler)
    private static Comparator<Matched> reverse(Comparator<Matched> comparator) {
        return comparator.reversed();
    }

    /**
     * Builds exception status mapper.
     */
    public static final class Builder {
        private final Multimap<HttpResponseStatus, FancyExceptionMatcher> multimap;

        public Builder() {
            this.multimap = HashMultimap.create();
        }

        public final Builder add(HttpResponseStatus status, FancyExceptionMatcher matcher) {
            multimap.put(status, matcher);
            return this;
        }

        @SafeVarargs
        public final Builder add(HttpResponseStatus status, Class<? extends Exception>... delegateChain) {
            multimap.put(status, new FancyExceptionMatcher(delegateChain));
            return this;
        }

        public ExceptionStatusMapper build() {
            return new ExceptionStatusMapper(this);
        }
    }

    @VisibleForTesting
    static class Matched {
        private final int matchLevel;
        private final HttpResponseStatus status;

        Matched(int matchLevel, HttpResponseStatus status) {
            this.matchLevel = matchLevel;
            this.status = requireNonNull(status);
        }

        int matchLevel() {
            return matchLevel;
        }

        public HttpResponseStatus status() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Matched matched = (Matched) o;
            return matchLevel == matched.matchLevel &&
                    status.equals(matched.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchLevel, status);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Matched.class.getSimpleName() + "[", "]")
                    .add("matchLevel=" + matchLevel)
                    .add("status=" + status)
                    .toString();
        }
    }
}
