package com.zoner.event;

import java.time.Instant;
import java.util.List;

public interface RecurrenceExpander {

    /**
     * Validates that the provided string is a valid RFC 5545 RRULE.
     * Throws BusinessRuleException if invalid or unsupported.
     */
    void validateRule(String rrule);

    /**
     * Parses the UNTIL date from an RRULE, if present, returning it as an Instant.
     */
    Instant parseUntil(String rrule, String timeZone);

    /**
     * Expands occurrence start instants that overlap with the window [rangeStart, rangeEnd).
     * Result is sorted in chronological order.
     */
    List<Instant> expand(Event event, Instant rangeStart, Instant rangeEnd, int maxOccurrences);
}
