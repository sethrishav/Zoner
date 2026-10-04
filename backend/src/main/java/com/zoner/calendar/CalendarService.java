package com.zoner.calendar;

import com.zoner.auth.AuthService.UserRegisteredEvent;
import com.zoner.auth.User;
import com.zoner.auth.UserRepository;
import com.zoner.calendar.AccessPolicy.CalendarAccess;
import com.zoner.calendar.CalendarDto.CalendarResponse;
import com.zoner.calendar.CalendarDto.CalendarShareResponse;
import com.zoner.calendar.CalendarDto.CreateCalendarRequest;
import com.zoner.calendar.CalendarDto.ShareCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdateCalendarRequest;
import com.zoner.calendar.CalendarDto.UpdatePreferenceRequest;
import com.zoner.common.error.BusinessRuleException;
import com.zoner.common.error.ConflictException;
import com.zoner.common.error.ForbiddenException;
import com.zoner.common.error.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository calendarShareRepository;
    private final UserCalendarPrefRepository userCalendarPrefRepository;
    private final UserRepository userRepository;
    private final AccessPolicy accessPolicy;

    public CalendarService(
            CalendarRepository calendarRepository,
            CalendarShareRepository calendarShareRepository,
            UserCalendarPrefRepository userCalendarPrefRepository,
            UserRepository userRepository,
            AccessPolicy accessPolicy) {
        this.calendarRepository = calendarRepository;
        this.calendarShareRepository = calendarShareRepository;
        this.userCalendarPrefRepository = userCalendarPrefRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Automatically provisions a default "Personal" calendar whenever a new user registers.
     */
    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        createDefaultCalendar(event.user());
    }

    @Transactional
    public Calendar createDefaultCalendar(User user) {
        if (calendarRepository.findDefaultByOwnerId(user.getId()).isPresent()) {
            return calendarRepository.findDefaultByOwnerId(user.getId()).get();
        }
        Calendar defaultCalendar = new Calendar(user, "Personal", "Default calendar", "#3B82F6", true);
        return calendarRepository.save(defaultCalendar);
    }

    @Transactional(readOnly = true)
    public List<CalendarResponse> listCalendars(Long userId) {
        List<Calendar> owned = calendarRepository.findAllByOwnerId(userId);
        List<CalendarShare> shared = calendarShareRepository.findAllByUserIdWithCalendar(userId);
        Map<Long, UserCalendarPref> prefs = userCalendarPrefRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(p -> p.getCalendar().getId(), p -> p, (a, b) -> a));

        List<CalendarResponse> results = new ArrayList<>();

        for (Calendar c : owned) {
            UserCalendarPref pref = prefs.get(c.getId());
            boolean enabled = pref == null || pref.isEnabled();
            String color = (pref != null && pref.getColorOverride() != null) ? pref.getColorOverride() : c.getColor();
            results.add(CalendarResponse.of(c, SharePermission.OWNER, enabled, color));
        }

        for (CalendarShare s : shared) {
            Calendar c = s.getCalendar();
            UserCalendarPref pref = prefs.get(c.getId());
            boolean enabled = pref == null || pref.isEnabled();
            String color = (pref != null && pref.getColorOverride() != null) ? pref.getColorOverride() : c.getColor();
            results.add(CalendarResponse.of(c, s.getPermission(), enabled, color));
        }

        log.info("[CALENDAR LIST] userId={}, returned {} calendars: {}",
                userId, results.size(),
                results.stream().map(r -> r.name() + "(id=" + r.id() + ",enabled=" + r.enabled() + ")").toList());

        return results;
    }

    @Transactional
    public CalendarResponse createCalendar(Long userId, CreateCalendarRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        String trimmedName = request.name().trim();
        if (calendarRepository.existsByOwnerIdAndNameIgnoreCase(userId, trimmedName)) {
            throw new ConflictException("You already have a calendar named '" + trimmedName + "'.");
        }

        Calendar calendar = new Calendar(
                user,
                trimmedName,
                request.description(),
                request.color(),
                false
        );
        calendar = calendarRepository.save(calendar);

        return CalendarResponse.of(calendar, SharePermission.OWNER, true, calendar.getColor());
    }

    @Transactional(readOnly = true)
    public CalendarResponse getCalendar(Long userId, Long calendarId) {
        CalendarAccess access = accessPolicy.getAccess(userId, calendarId);
        UserCalendarPref pref = userCalendarPrefRepository.findByUserIdAndCalendarId(userId, calendarId).orElse(null);

        boolean enabled = pref == null || pref.isEnabled();
        String color = (pref != null && pref.getColorOverride() != null)
                ? pref.getColorOverride()
                : access.calendar().getColor();

        return CalendarResponse.of(access.calendar(), access.permission(), enabled, color);
    }

    @Transactional
    public CalendarResponse updateCalendar(Long userId, Long calendarId, UpdateCalendarRequest request) {
        CalendarAccess access = accessPolicy.requireAccess(userId, calendarId, SharePermission.OWNER);
        Calendar calendar = access.calendar();

        String trimmedName = request.name().trim();
        if (!calendar.getName().equalsIgnoreCase(trimmedName)
                && calendarRepository.existsByOwnerIdAndNameIgnoreCase(userId, trimmedName)) {
            throw new ConflictException("You already have a calendar named '" + trimmedName + "'.");
        }

        calendar.setName(trimmedName);
        calendar.setDescription(request.description());
        if (request.color() != null && !request.color().isBlank()) {
            calendar.setColor(request.color());
        }

        calendar = calendarRepository.save(calendar);
        return CalendarResponse.of(calendar, SharePermission.OWNER, true, calendar.getColor());
    }

    @Transactional
    public void deleteCalendar(Long userId, Long calendarId) {
        CalendarAccess access = accessPolicy.requireAccess(userId, calendarId, SharePermission.OWNER);
        if (access.calendar().isDefault()) {
            throw new BusinessRuleException("Your default calendar cannot be deleted.");
        }
        calendarRepository.delete(access.calendar());
    }

    @Transactional
    public CalendarResponse updatePreference(Long userId, Long calendarId, UpdatePreferenceRequest request) {
        CalendarAccess access = accessPolicy.getAccess(userId, calendarId);

        UserCalendarPref pref = userCalendarPrefRepository.findByUserIdAndCalendarId(userId, calendarId)
                .orElseGet(() -> {
                    User userRef = userRepository.getReferenceById(userId);
                    return new UserCalendarPref(userRef, access.calendar(), true, null);
                });

        if (request.enabled() != null) {
            pref.setEnabled(request.enabled());
        }
        if (request.colorOverride() != null) {
            pref.setColorOverride(request.colorOverride().isBlank() ? null : request.colorOverride());
        }

        pref = userCalendarPrefRepository.save(pref);
        String color = pref.getColorOverride() != null ? pref.getColorOverride() : access.calendar().getColor();
        log.info("[CALENDAR PREF UPDATED] userId={}, calendarId={}, enabled={}, colorOverride='{}'",
                userId, calendarId, pref.isEnabled(), pref.getColorOverride());

        return CalendarResponse.of(access.calendar(), access.permission(), pref.isEnabled(), color);
    }

    @Transactional(readOnly = true)
    public List<CalendarShareResponse> listShares(Long userId, Long calendarId) {
        accessPolicy.requireAccess(userId, calendarId, SharePermission.OWNER);
        return calendarShareRepository.findAllByCalendarIdWithUser(calendarId).stream()
                .map(CalendarShareResponse::from)
                .toList();
    }

    @Transactional
    public CalendarShareResponse shareCalendar(Long userId, Long calendarId, ShareCalendarRequest request) {
        CalendarAccess access = accessPolicy.requireAccess(userId, calendarId, SharePermission.OWNER);

        User targetUser = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NotFoundException("No registered user found with email '" + request.email().trim() + "'."));

        if (targetUser.getId().equals(userId)) {
            throw new BusinessRuleException("You cannot share a calendar with yourself.");
        }

        CalendarShare share = calendarShareRepository.findByCalendarIdAndUserId(calendarId, targetUser.getId())
                .orElseGet(() -> new CalendarShare(access.calendar(), targetUser, request.permission()));

        share.setPermission(request.permission());
        share = calendarShareRepository.save(share);

        return CalendarShareResponse.from(share);
    }

    @Transactional
    public void removeShare(Long userId, Long calendarId, Long targetUserId) {
        // Can remove if caller is OWNER, or caller is the target user unsharing themselves
        CalendarAccess access = accessPolicy.getAccess(userId, calendarId);
        boolean isOwner = access.permission() == SharePermission.OWNER;
        boolean isSelfUnsharing = userId.equals(targetUserId);

        if (!isOwner && !isSelfUnsharing) {
            throw new ForbiddenException("Only the calendar owner can manage sharing permissions.");
        }

        calendarShareRepository.deleteByCalendarIdAndUserId(calendarId, targetUserId);
    }
}
