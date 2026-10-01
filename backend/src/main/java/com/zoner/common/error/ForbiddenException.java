package com.zoner.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException() {
        this("You do not have permission to do that.");
    }
}
