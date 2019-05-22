package com.hotels.styx;

import static java.util.Objects.requireNonNull;

public class ReplaceLiveContentExampleConfig {
    private String replacement;

    public ReplaceLiveContentExampleConfig(String replacement){
        this.replacement = requireNonNull(replacement);
    }

    public String replacement() {
        return replacement;
    }
}
