package com.zoner.common.error;

import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing failures. Services throw these; the handler maps them. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    protected ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
