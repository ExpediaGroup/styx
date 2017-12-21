/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.client;

import com.google.common.base.Splitter;
import com.hotels.styx.api.HttpCookie;
import com.hotels.styx.api.client.ActiveOrigins;
import com.hotels.styx.api.client.ConnectionPool;
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.loadbalancing.spi.LoadBalancingStrategy;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A load balancing strategy that restricts available origins according to a cookie value.
 */
class OriginRestrictionLoadBalancingStrategy implements LoadBalancingStrategy {
    private static final Splitter COOKIE_SPLITTER = Splitter.on(',').trimResults();

    private static final Logger LOG = getLogger(OriginRestrictionLoadBalancingStrategy.class);
    private static final Pattern MATCH_ALL = Pattern.compile(".*");

    private ActiveOrigins activeOrigins;
    private final LoadBalancingStrategy delegate;
    private final String cookieName;

    OriginRestrictionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancingStrategy delegate, String cookieName) {
        this.activeOrigins = activeOrigins;
        this.delegate = checkNotNull(delegate);
        this.cookieName = checkNotNull(cookieName);
    }

    @Override
    public Iterable<ConnectionPool> vote(Context context) {
        Iterable<ConnectionPool> connectionPools = delegate.vote(context);
        Optional<Set<ConnectionPool>> matchingOrigins = originPartition(activeOrigins.snapshot(), context);

        if (matchingOrigins.isPresent()) {
            Set<ConnectionPool> origins = matchingOrigins.get();
            return stream(connectionPools.spliterator(), false)
                    .filter(origins::contains)
                    .collect(toList());
        }
        return connectionPools;
    }

    private Optional<Set<ConnectionPool>> originPartition(Iterable<ConnectionPool> origins, Context context) {
        return context.currentRequest().cookie(cookieName)
                .map(cookie -> restrictedOrigins(origins, cookie));
    }

    private Set<ConnectionPool> restrictedOrigins(Iterable<ConnectionPool> origins, HttpCookie cookie) {
        return stream(origins.spliterator(), false)
                .filter(originIsPermittedByCookie(cookie.value()))
                .collect(toSet());
    }

    private Predicate<ConnectionPool> originIsPermittedByCookie(String cookieValue) {
        return originIdMatcherStream(cookieValue)
                .reduce(Predicate::or)
                .orElse(input -> false);
    }

    private Stream<Predicate<ConnectionPool>> originIdMatcherStream(String cookieValue) {
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
            LOG.error("Invalid origin restriction cookie value={}", regex, e);
            return MATCH_ALL;
        }
    }

    private Predicate<ConnectionPool> originIdMatches(Pattern pattern) {
        return connectionPool -> pattern.matcher(originId(connectionPool)).matches();
    }

    private String originId(ConnectionPool pool) {
        return pool.getOrigin().id().toString();
    }

    @Override
    public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        delegate.originsInventoryStateChanged(snapshot);
    }
}
