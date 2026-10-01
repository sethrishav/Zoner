package com.zoner.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** The one error body returned by every endpoint. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        ErrorCode code,
        String message,
        List<FieldIssue> details,
        String traceId,
        Instant timestamp) {

    public record FieldIssue(String field, String message) {}
}
