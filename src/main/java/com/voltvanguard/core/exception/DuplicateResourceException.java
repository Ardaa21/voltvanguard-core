package com.voltvanguard.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a uniqueness constraint would be violated.
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resourceName, String fieldName, String fieldValue) {
        super("%s already exists with %s: '%s'".formatted(resourceName, fieldName, fieldValue));
    }
}
