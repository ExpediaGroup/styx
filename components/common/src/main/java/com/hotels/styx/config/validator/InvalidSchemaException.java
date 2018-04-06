package com.hotels.styx.config.validator;

public class InvalidSchemaException extends RuntimeException {
    public InvalidSchemaException(String message) {
        super(message);
    }
}
