package com.zoner.common.error;

import org.springframework.http.HttpStatus;

public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, message);
    }

    public UnauthenticatedException() {
        this("Authentication is required to access this resource.");
    }
}
