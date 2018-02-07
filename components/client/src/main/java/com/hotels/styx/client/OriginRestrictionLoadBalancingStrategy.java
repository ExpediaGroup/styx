/**
 * Copyright (C) 2013-2018 Expedia Inc.
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
import com.hotels.styx.api.client.OriginsInventorySnapshot;
import com.hotels.styx.api.client.RemoteHost;
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
public class OriginRestrictionLoadBalancingStrategy implements LoadBalancingStrategy {
    private static final Splitter COOKIE_SPLITTER = Splitter.on(',').trimResults();

    private static final Logger LOG = getLogger(OriginRestrictionLoadBalancingStrategy.class);
    private static final Pattern MATCH_ALL = Pattern.compile(".*");

    private final ActiveOrigins activeOrigins;
    private final LoadBalancingStrategy delegate;
    private final String cookieName;

    public OriginRestrictionLoadBalancingStrategy(ActiveOrigins activeOrigins, LoadBalancingStrategy delegate, String cookieName) {
        this.activeOrigins = activeOrigins;
        this.delegate = checkNotNull(delegate);
        this.cookieName = checkNotNull(cookieName);
    }

    @Override
    public Iterable<RemoteHost> vote(Context context) {
        Iterable<RemoteHost> connectionPools = delegate.vote(context);
        Optional<Set<RemoteHost>> matchingOrigins = originPartition(activeOrigins.snapshot(), context);

        if (matchingOrigins.isPresent()) {
            Set<RemoteHost> origins = matchingOrigins.get();
            return stream(connectionPools.spliterator(), false)
                    .filter(origins::contains)
                    .collect(toList());
        }
        return connectionPools;
    }

    private Optional<Set<RemoteHost>> originPartition(Iterable<RemoteHost> origins, Context context) {
        return context.currentRequest().cookie(cookieName)
                .map(cookie -> restrictedOrigins(origins, cookie));
    }

    private Set<RemoteHost> restrictedOrigins(Iterable<RemoteHost> origins, HttpCookie cookie) {
        return stream(origins.spliterator(), false)
                .filter(originIsPermittedByCookie(cookie.value()))
                .collect(toSet());
    }

    private Predicate<RemoteHost> originIsPermittedByCookie(String cookieValue) {
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
            LOG.error("Invalid origin restriction cookie value={}", regex, e);
            return MATCH_ALL;
        }
    }

    private Predicate<RemoteHost> originIdMatches(Pattern pattern) {
        return remoteHost -> pattern.matcher(remoteHost.id().toString()).matches();
    }

    @Override
    public void originsInventoryStateChanged(OriginsInventorySnapshot snapshot) {
        delegate.originsInventoryStateChanged(snapshot);
    }
}
