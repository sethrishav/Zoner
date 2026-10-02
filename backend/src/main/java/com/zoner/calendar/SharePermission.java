package com.zoner.calendar;

/**
 * Access levels for calendars.
 * - OWNER: Full administrative control, delete, rename, manage shares, event CRUD
 * - EDIT: Read events, create, update, delete events
 * - VIEW: Read events only
 */
public enum SharePermission {
    OWNER,
    EDIT,
    VIEW;

    public boolean canRead() {
        return true;
    }

    public boolean canEditEvents() {
        return this == OWNER || this == EDIT;
    }

    public boolean canManageCalendar() {
        return this == OWNER;
    }
}
