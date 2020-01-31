package com.hotels.styx.debug;

import com.hotels.styx.api.LiveHttpRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides a static context to keep a list of the requests we want to debug.
 */
public class RequestDebugger {

    private static Set<String> requestIdPrefixes = new HashSet<>();

    public static void add(String requestIdPrefix) {
        if (requestIdPrefix != null) {
            requestIdPrefixes.add(requestIdPrefix);
        }
    }

    public static boolean shouldDebugRequest(LiveHttpRequest request) {
        String requestId = request.id().toString();
        String requestPrefix = requestId.substring(0, requestId.indexOf('-'));
        return requestIdPrefixes.contains(requestPrefix);
    }
}

