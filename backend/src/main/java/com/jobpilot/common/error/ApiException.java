package com.jobpilot.common.error;

import org.springframework.http.HttpStatus;

/** An error whose message is safe to show to the client. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
