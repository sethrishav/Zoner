package com.zoner.common.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleException() {
        this("The request breaks a business rule.");
    }
}
