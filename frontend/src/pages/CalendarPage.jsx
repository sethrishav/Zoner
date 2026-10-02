import React, { useState, useEffect, useCallback } from 'react';
import AppShell from '../components/layout/AppShell';
import CalendarView from '../components/calendar/CalendarView';
import EventModal from '../components/events/EventModal';
import EventDetailModal from '../components/events/EventDetailModal';
import RecurringChoiceModal from '../components/events/RecurringChoiceModal';
import CreateCalendarModal from '../components/calendars/CreateCalendarModal';
import ShareCalendarModal from '../components/calendars/ShareCalendarModal';
import SearchModal from '../components/search/SearchModal';
import { api } from '../api/client';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function CalendarPage() {
  const { user } = useAuth();
  const toast = useToast();

  // Calendars
  const [calendars, setCalendars] = useState([]);
  const [selectedCalendarIds, setSelectedCalendarIds] = useState([]);
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // Modals state
  const [isEventModalOpen, setIsEventModalOpen] = useState(false);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [editingEvent, setEditingEvent] = useState(null);
  const [editMode, setEditMode] = useState('ALL');
  const [occurrenceStart, setOccurrenceStart] = useState(null);

  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [viewingEvent, setViewingEvent] = useState(null);

  const [isRecurringChoiceOpen, setIsRecurringChoiceOpen] = useState(false);
  const [recurringActionType, setRecurringActionType] = useState('edit'); // 'edit' or 'delete'
  const [pendingRecurringEvent, setPendingRecurringEvent] = useState(null);

  const [isCreateCalendarOpen, setIsCreateCalendarOpen] = useState(false);
  const [isShareCalendarOpen, setIsShareCalendarOpen] = useState(false);
  const [sharingCalendar, setSharingCalendar] = useState(null);

  const [isSearchOpen, setIsSearchOpen] = useState(false);

  // Load user calendars
  const loadCalendars = useCallback(async () => {
    try {
      const data = await api.calendars.list();
      setCalendars(data);
      // If none selected yet, default to all enabled calendars
      setSelectedCalendarIds((prev) => {
        if (prev.length > 0) return prev;
        return data.filter((c) => c.enabled !== false).map((c) => c.id);
      });
    } catch (err) {
      console.error('Failed to load calendars', err);
    }
  }, []);

  useEffect(() => {
    loadCalendars();
  }, [loadCalendars]);

  // Global keyboard shortcut for Search (⌘K / Ctrl+K)
  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsSearchOpen(true);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleToggleCalendar = (id) => {
    setSelectedCalendarIds((prev) =>
      prev.includes(id) ? prev.filter((calId) => calId !== id) : [...prev, id]
    );
  };

  // Triggered when slot is clicked or dragged
  const handleSelectSlot = (slotInfo) => {
    setSelectedSlot(slotInfo);
    setEditingEvent(null);
    setEditMode('ALL');
    setOccurrenceStart(null);
    setIsEventModalOpen(true);
  };

  // Triggered when event is clicked
  const handleSelectEvent = (eventData) => {
    setViewingEvent(eventData);
    setIsDetailModalOpen(true);
  };

  // Edit event clicked in detail modal
  const handleRequestEdit = (eventData) => {
    setIsDetailModalOpen(false);
    if (eventData.recurrenceRule) {
      setPendingRecurringEvent(eventData);
      setRecurringActionType('edit');
      setIsRecurringChoiceOpen(true);
    } else {
      setEditingEvent(eventData);
      setEditMode('ALL');
      setOccurrenceStart(null);
      setIsEventModalOpen(true);
    }
  };

  // Delete event clicked in detail modal
  const handleRequestDelete = (eventData) => {
    setIsDetailModalOpen(false);
    if (eventData.recurrenceRule) {
      setPendingRecurringEvent(eventData);
      setRecurringActionType('delete');
      setIsRecurringChoiceOpen(true);
    } else {
      executeDelete(eventData.id, 'ALL', null);
    }
  };

  // Handle recurring mode confirmation (THIS, THIS_AND_FOLLOWING, ALL)
  const handleRecurringChoiceConfirm = (mode) => {
    setIsRecurringChoiceOpen(false);
    const eventData = pendingRecurringEvent;
    if (!eventData) return;

    const occStart = eventData.start || eventData.startAt;

    if (recurringActionType === 'delete') {
      executeDelete(eventData.id, mode, occStart);
    } else {
      setEditingEvent(eventData);
      setEditMode(mode);
      setOccurrenceStart(occStart);
      setIsEventModalOpen(true);
    }
  };

  const executeDelete = async (eventId, mode, occStart) => {
    try {
      await api.events.delete(eventId, mode, occStart);
      toast.success('Event deleted');
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const handleOpenShare = (cal) => {
    setSharingCalendar(cal);
    setIsShareCalendarOpen(true);
  };

  return (
    <AppShell
      calendars={calendars}
      selectedCalendarIds={selectedCalendarIds}
      onToggleCalendar={handleToggleCalendar}
      onOpenCreateEvent={() => {
        setEditingEvent(null);
        setSelectedSlot(new Date());
        setIsEventModalOpen(true);
      }}
      onOpenCreateCalendar={() => setIsCreateCalendarOpen(true)}
      onOpenShareCalendar={handleOpenShare}
      onOpenSearch={() => setIsSearchOpen(true)}
      selectedDate={selectedDate}
      onSelectDate={setSelectedDate}
    >
      <CalendarView
        key={refreshTrigger}
        calendars={calendars}
        selectedCalendarIds={selectedCalendarIds}
        selectedDate={selectedDate}
        onSelectSlot={handleSelectSlot}
        onSelectEvent={handleSelectEvent}
        onEventModified={() => setRefreshTrigger((prev) => prev + 1)}
      />

      {/* Event Create / Edit Modal */}
      <EventModal
        isOpen={isEventModalOpen}
        onClose={() => setIsEventModalOpen(false)}
        onSaved={() => setRefreshTrigger((prev) => prev + 1)}
        calendars={calendars.filter((c) => c.isOwner || c.permission === 'OWNER' || c.permission === 'EDIT')}
        initialDate={selectedSlot}
        event={editingEvent}
        editMode={editMode}
        occurrenceStart={occurrenceStart}
      />

      {/* Event Detail Modal */}
      <EventDetailModal
        isOpen={isDetailModalOpen}
        event={viewingEvent}
        calendar={calendars.find((c) => c.id === viewingEvent?.calendarId)}
        onClose={() => setIsDetailModalOpen(false)}
        onEdit={handleRequestEdit}
        onDelete={handleRequestDelete}
      />

      {/* Recurring Edit / Delete Mode Selection Dialog */}
      <RecurringChoiceModal
        isOpen={isRecurringChoiceOpen}
        actionType={recurringActionType}
        eventTitle={pendingRecurringEvent?.title}
        onConfirm={handleRecurringChoiceConfirm}
        onClose={() => setIsRecurringChoiceOpen(false)}
      />

      {/* Create Calendar Modal */}
      <CreateCalendarModal
        isOpen={isCreateCalendarOpen}
        onClose={() => setIsCreateCalendarOpen(false)}
        onCreated={loadCalendars}
      />

      {/* Share Calendar Modal */}
      <ShareCalendarModal
        isOpen={isShareCalendarOpen}
        calendar={sharingCalendar}
        onClose={() => setIsShareCalendarOpen(false)}
      />

      {/* Global Search Modal */}
      <SearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        calendars={calendars}
        onSelectEvent={(evt) => {
          setSelectedDate(new Date(evt.startAt));
          setViewingEvent(evt);
          setIsDetailModalOpen(true);
        }}
      />
    </AppShell>
  );
}
