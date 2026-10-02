package com.zoner.event;

import com.zoner.common.error.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;
import org.springframework.stereotype.Component;

@Component
public class LibRecurExpander implements RecurrenceExpander {

    private static final int DEFAULT_MAX_OCCURRENCES = 500;

    @Override
    public void validateRule(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            return;
        }

        String cleaned = cleanRuleString(rrule);
        try {
            RecurrenceRule rule = new RecurrenceRule(cleaned);
            Freq freq = rule.getFreq();
            if (freq != Freq.DAILY && freq != Freq.WEEKLY && freq != Freq.MONTHLY && freq != Freq.YEARLY) {
                throw new BusinessRuleException("Unsupported recurrence frequency: " + freq + ". Supported: DAILY, WEEKLY, MONTHLY, YEARLY.");
            }
        } catch (InvalidRecurrenceRuleException e) {
            throw new BusinessRuleException("Invalid recurrence rule (RRULE): " + e.getMessage());
        }
    }

    @Override
    public Instant parseUntil(String rrule, String timeZone) {
        if (rrule == null || rrule.isBlank()) {
            return null;
        }
        try {
            RecurrenceRule rule = new RecurrenceRule(cleanRuleString(rrule));
            DateTime until = rule.getUntil();
            if (until != null) {
                return Instant.ofEpochMilli(until.getTimestamp());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Instant> expand(Event event, Instant rangeStart, Instant rangeEnd, int maxOccurrences) {
        if (event.getRecurrenceRule() == null || event.getRecurrenceRule().isBlank()) {
            // Non-recurring event: returns its own start if it overlaps the range
            Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
            Instant end = event.getStartAt().plus(duration);
            if (end.isAfter(rangeStart) && event.getStartAt().isBefore(rangeEnd)) {
                return List.of(event.getStartAt());
            }
            return List.of();
        }

        int limit = maxOccurrences > 0 ? maxOccurrences : DEFAULT_MAX_OCCURRENCES;
        String tzName = (event.getTimeZone() != null && !event.getTimeZone().isBlank())
                ? event.getTimeZone()
                : "UTC";
        TimeZone tz = TimeZone.getTimeZone(tzName);

        Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
        List<Instant> occurrences = new ArrayList<>();

        try {
            String cleaned = cleanRuleString(event.getRecurrenceRule());
            RecurrenceRule rule = new RecurrenceRule(cleaned);

            DateTime start = new DateTime(tz, event.getStartAt().toEpochMilli());
            RecurrenceRuleIterator it = rule.iterator(start);

            // Safety limit on total iterations to prevent infinite loops for dense rules
            int iterations = 0;
            int maxIterations = limit * 10;

            while (it.hasNext() && occurrences.size() < limit && iterations < maxIterations) {
                iterations++;
                DateTime next = it.nextDateTime();
                Instant occStart = Instant.ofEpochMilli(next.getTimestamp());

                // If occurrence start is beyond rangeEnd, subsequent occurrences will also be beyond rangeEnd
                if (occStart.isAfter(rangeEnd) || occStart.equals(rangeEnd)) {
                    break;
                }

                // If occurrence start is beyond the series end boundary (recurrenceUntil), the series has ended
                if (event.getRecurrenceUntil() != null && occStart.isAfter(event.getRecurrenceUntil())) {
                    break;
                }

                Instant occEnd = occStart.plus(duration);

                // Overlap condition: start < rangeEnd AND end > rangeStart
                if (occStart.isBefore(rangeEnd) && occEnd.isAfter(rangeStart)) {
                    occurrences.add(occStart);
                }
            }
        } catch (InvalidRecurrenceRuleException e) {
            throw new BusinessRuleException("Failed to expand invalid recurrence rule: " + e.getMessage());
        }

        return occurrences;
    }

    private String cleanRuleString(String rrule) {
        String trimmed = rrule.trim();
        if (trimmed.toUpperCase().startsWith("RRULE:")) {
            return trimmed.substring(6).trim();
        }
        return trimmed;
    }
}
