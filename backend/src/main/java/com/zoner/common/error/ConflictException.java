package com.zoner.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    public ConflictException() {
        this("The request conflicts with the current state.");
    }
}
