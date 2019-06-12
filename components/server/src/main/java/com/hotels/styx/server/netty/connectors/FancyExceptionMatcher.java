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

    int matchLevel(Throwable error) {
        return deepMatchLevel(error, delegateChain, 1);
    }

    private static int deepMatchLevel(Throwable error, List<Class<? extends Exception>> delegateChain, int level) {
        System.out.println("Comparing " + error + " to " + delegateChain + " at level=" + level);

        if (delegateChain.isEmpty()) {
            return level;
        }

        if (delegateChain.get(0).isInstance(error)) {
            if (delegateChain.size() == 1) {
                return level;
            }

            return deepMatchLevel(error.getCause(), delegateChain.subList(1, delegateChain.size()), level + 1);
        }

        return 0;
    }


}
