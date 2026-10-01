package com.zoner.common.error;

/**
 * Stable, machine-readable error codes. The frontend maps these to friendly copy,
 * so raw API messages never have to be shown to users.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    BAD_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    CONFLICT,
    STALE_VERSION,
    BUSINESS_RULE_VIOLATION,
    RATE_LIMITED,
    INTERNAL_ERROR
}
