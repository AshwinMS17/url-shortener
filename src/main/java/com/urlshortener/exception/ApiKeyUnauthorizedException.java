package com.urlshortener.exception;

/**
 * Thrown when a request to a protected endpoint carries no API key or an
 * unrecognised one. The global handler turns this into a 401.
 */
public class ApiKeyUnauthorizedException extends RuntimeException {

    public ApiKeyUnauthorizedException(String message) {
        super(message);
    }
}
