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
package com.hotels.styx.client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.hotels.styx.api.extension.ActiveOrigins;
import com.hotels.styx.api.extension.RemoteHost;
import com.hotels.styx.api.extension.loadbalancing.spi.LoadBalancer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A load balancing strategy that restricts available origins according to a cookie value.
 */
public class OriginRestrictionLoadBalancingStrategy implements LoadBalancer {
    private static final Splitter COOKIE_SPLITTER = Splitter.on(',').trimResults();

    private static final Logger LOG = getLogger(OriginRestrictionLoadBalancingStrategy.class);
    private static final Pattern MATCH_ALL = Pattern.compile(".*");

    private final ActiveOrigins activeOrigins;
    private final LoadBalancer delegate;
    private Random rng;

    public OriginRestrictionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancer delegate) {
        this(activeOrigins, delegate, new Random());
    }

    @VisibleForTesting
    OriginRestrictionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancer delegate, Random rng) {
        this.activeOrigins = activeOrigins;
        this.delegate = requireNonNull(delegate);
        this.rng = requireNonNull(rng);
    }

    @Override
    public Optional<RemoteHost> choose(LoadBalancer.Preferences context) {
        return context.preferredOrigins()
                .map(hostPreference -> {
                            List<RemoteHost> list = stream(activeOrigins.snapshot().spliterator(), false)
                                    .filter(originIsAllowed(hostPreference))
                                    .collect(toList());
                            if (list.size() > 0) {
                                return Optional.of(list.get(rng.nextInt(list.size())));
                            } else {
                                return Optional.<RemoteHost>empty();
                            }
                        }
                )
                .orElse(delegate.choose(context));
    }

    private Predicate<RemoteHost> originIsAllowed(String cookieValue) {
        return originIdMatcherStream(cookieValue)
                .reduce(Predicate::or)
                .orElse(input -> false);
    }

    private Stream<Predicate<RemoteHost>> originIdMatcherStream(String cookieValue) {
        return regularExpressionStream(cookieValue)
                .map(this::compileRegularExpression)
                .map(this::originIdMatches);
    }

    private Stream<String> regularExpressionStream(String cookieValue) {
        return stream(COOKIE_SPLITTER.split(cookieValue).spliterator(), false);
    }

    private Pattern compileRegularExpression(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (Exception e) {
            LOG.error("Invalid origin restriction cookie value={}, Cause={}", regex, e.getMessage());
            return MATCH_ALL;
        }
    }

    private Predicate<RemoteHost> originIdMatches(Pattern pattern) {
        return remoteHost -> pattern.matcher(remoteHost.id().toString()).matches();
    }

}
