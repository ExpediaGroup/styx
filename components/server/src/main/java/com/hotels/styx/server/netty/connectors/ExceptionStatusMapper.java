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
package com.hotels.styx.server.netty.connectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.hotels.styx.api.HttpResponseStatus;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Default list of exception mappers.
 */
final class ExceptionStatusMapper {
    private static final Logger LOG = getLogger(ExceptionStatusMapper.class);

    private final Multimap<HttpResponseStatus, Class<? extends Exception>> multimap;

    private ExceptionStatusMapper(Builder builder) {
        this.multimap = ImmutableMultimap.copyOf(builder.multimap);
    }

    Optional<HttpResponseStatus> statusFor(Throwable throwable) {
        List<HttpResponseStatus> matchingStatuses = this.multimap.entries().stream()
                .filter(entry -> entry.getValue().isInstance(throwable))
                .sorted(comparing(entry -> entry.getKey().code()))
                .map(Map.Entry::getKey)
                .collect(toList());

        if (matchingStatuses.size() > 1) {
            LOG.error("Multiple matching statuses for throwable={} statuses={}", throwable, matchingStatuses);
            return Optional.empty();
        }

        return matchingStatuses.stream().findFirst();
    }

    static final class Builder {
        private final Multimap<HttpResponseStatus, Class<? extends Exception>> multimap;

        public Builder() {
            this.multimap = HashMultimap.create();
        }

        @SafeVarargs
        public final Builder add(HttpResponseStatus status, Class<? extends Exception>... classes) {
            multimap.putAll(status, asList(classes));
            return this;
        }

        public ExceptionStatusMapper build() {
            return new ExceptionStatusMapper(this);
        }
    }
}
