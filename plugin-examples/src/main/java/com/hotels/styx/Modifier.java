package com.hotels.styx;

import com.hotels.styx.api.HttpResponse;

/**
 * Marker interface for classes that perform some modification
 */
public interface Modifier {
    String modify(String input);
}
