package com.hotels.styx.server.netty.connectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Arrays.asList;

/**
 *
 */
@VisibleForTesting
class FancyExceptionMatcher {
    private final List<Class<? extends Exception>> delegateChain;

    private FancyExceptionMatcher(List<Class<? extends Exception>> delegateChain) {
        this.delegateChain = ImmutableList.copyOf(delegateChain);
        if (delegateChain.isEmpty()) {
            throw new IllegalArgumentException("Must compare against an exception");
        }
    }

    @SafeVarargs
    FancyExceptionMatcher(Class<? extends Exception>... delegateChain) {
        this(asList(delegateChain));
    }

    boolean isMatch(Throwable error) {
        return deepMatch(error, delegateChain);
    }

    private static boolean deepMatch(Throwable error, List<Class<? extends Exception>> delegateChain) {
        return delegateChain.isEmpty()
                || delegateChain.get(0).isInstance(error)
                && (delegateChain.size() == 1 || deepMatch(error.getCause(), delegateChain.subList(1, delegateChain.size())));
    }


}
