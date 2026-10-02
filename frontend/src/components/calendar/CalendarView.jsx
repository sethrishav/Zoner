import React, { useRef, useState, useEffect, useCallback } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import { api } from '../../api/client';
import { getErrorMessage } from '../../api/errors';
import { useToast } from '../../context/ToastContext';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon, Clock, Sparkles } from 'lucide-react';
import { format, parseISO } from 'date-fns';
import './calendar.css';

export default function CalendarView({
  calendars = [],
  selectedCalendarIds = [],
  onSelectSlot,
  onSelectEvent,
  onEventModified,
  selectedDate,
}) {
  const toast = useToast();
  const calendarRef = useRef(null);

  const [events, setEvents] = useState([]);
  const [currentTitle, setCurrentTitle] = useState('');
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 640;
  const [currentView, setCurrentView] = useState(isMobile ? 'timeGridDay' : 'timeGridWeek');
  const [currentRange, setCurrentRange] = useState({ from: null, to: null });
  const [isLoading, setIsLoading] = useState(false);

  // Sync external selectedDate changes (e.g. from sidebar mini-calendar)
  useEffect(() => {
    if (selectedDate && calendarRef.current) {
      const apiObj = calendarRef.current.getApi();
      apiObj.gotoDate(selectedDate);
      setCurrentTitle(apiObj.view.title);
    }
  }, [selectedDate]);

  // Fetch events for current visible range and selected calendars
  const fetchEvents = useCallback(async (from, to, calIds) => {
    if (!from || !to) return;
    if (!calIds || calIds.length === 0) {
      setEvents([]);
      return;
    }

    try {
      setIsLoading(true);
      const data = await api.events.list({
        from: from.toISOString(),
        to: to.toISOString(),
        calendarIds: calIds,
      });
      setEvents(data);
    } catch (err) {
      console.error('Failed to fetch events:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // When selectedCalendarIds or currentRange change, refetch
  useEffect(() => {
    if (currentRange.from && currentRange.to) {
      fetchEvents(currentRange.from, currentRange.to, selectedCalendarIds);
    }
  }, [currentRange, selectedCalendarIds, fetchEvents]);

  // Called by FullCalendar whenever the view or date range changes
  const handleDatesSet = (dateInfo) => {
    setCurrentTitle(dateInfo.view.title);
    setCurrentRange({ from: dateInfo.start, to: dateInfo.end });
  };

  // Navigation handlers
  const handlePrev = () => {
    const apiObj = calendarRef.current.getApi();
    apiObj.prev();
  };

  const handleNext = () => {
    const apiObj = calendarRef.current.getApi();
    apiObj.next();
  };

  const handleToday = () => {
    const apiObj = calendarRef.current.getApi();
    apiObj.today();
  };

  const handleViewChange = (viewName) => {
    setCurrentView(viewName);
    const apiObj = calendarRef.current.getApi();
    apiObj.changeView(viewName);
  };

  // Convert backend events to FullCalendar event format
  const fcEvents = events.map((e) => {
    const cal = calendars.find((c) => c.id === e.calendarId);
    const isSharedReadOnly = cal && !cal.isOwner && cal.permission === 'VIEW';
    const color = e.color || cal?.color || '#4f46e5';

    return {
      id: `${e.id}_${e.startAt}`,
      rawId: e.id,
      title: e.title,
      start: e.startAt,
      end: e.endAt,
      allDay: e.allDay,
      backgroundColor: color,
      borderColor: 'transparent',
      textColor: '#ffffff',
      editable: !isSharedReadOnly,
      extendedProps: {
        ...e,
        calendar: cal,
      },
    };
  });

  // Slot click / drag range selection
  const handleSelect = (selectionInfo) => {
    onSelectSlot &&
      onSelectSlot({
        start: selectionInfo.start,
        end: selectionInfo.end,
        allDay: selectionInfo.allDay,
      });
  };

  // Event click
  const handleEventClick = (clickInfo) => {
    const rawEvent = clickInfo.event.extendedProps;
    onSelectEvent && onSelectEvent(rawEvent);
  };

  // Drag and drop event move (Optimistic UI)
  const handleEventDrop = async (dropInfo) => {
    const raw = dropInfo.event.extendedProps;
    const newStart = dropInfo.event.start.toISOString();
    const newEnd = dropInfo.event.end ? dropInfo.event.end.toISOString() : newStart;

    // Check if event is recurring
    if (raw.recurrenceRule) {
      dropInfo.revert();
      toast.info('To move a recurring event, please open the event and edit the time.');
      return;
    }

    try {
      await api.events.update(raw.id, {
        calendarId: raw.calendarId,
        title: raw.title,
        description: raw.description,
        location: raw.location,
        color: raw.color,
        allDay: dropInfo.event.allDay,
        startAt: newStart,
        endAt: newEnd,
        timeZone: raw.timeZone,
        version: raw.version,
      });
      toast.success('Event moved');
      onEventModified && onEventModified();
    } catch (err) {
      dropInfo.revert();
      toast.error(getErrorMessage(err));
    }
  };

  // Edge drag to resize event (Optimistic UI)
  const handleEventResize = async (resizeInfo) => {
    const raw = resizeInfo.event.extendedProps;
    const newStart = resizeInfo.event.start.toISOString();
    const newEnd = resizeInfo.event.end.toISOString();

    if (raw.recurrenceRule) {
      resizeInfo.revert();
      toast.info('To resize a recurring event, please edit the event duration in the editor.');
      return;
    }

    try {
      await api.events.update(raw.id, {
        calendarId: raw.calendarId,
        title: raw.title,
        description: raw.description,
        location: raw.location,
        color: raw.color,
        allDay: resizeInfo.event.allDay,
        startAt: newStart,
        endAt: newEnd,
        timeZone: raw.timeZone,
        version: raw.version,
      });
      toast.success('Event duration updated');
      onEventModified && onEventModified();
    } catch (err) {
      resizeInfo.revert();
      toast.error(getErrorMessage(err));
    }
  };

  // Custom event renderer with smart inline layout for short (10-25m) and standard events
  const renderEventContent = (eventInfo) => {
    const { event, timeText, view } = eventInfo;
    const isMonth = view.type === 'dayGridMonth';
    const isAllDay = event.allDay;
    const hasRecurrence = Boolean(event.extendedProps?.recurrenceRule);

    if (isMonth || isAllDay) {
      return (
        <div className="flex items-center gap-1.5 px-1.5 py-0.5 w-full overflow-hidden text-[11px] leading-tight text-white">
          {!isAllDay && timeText && (
            <span className="font-semibold text-[10px] opacity-80 shrink-0">{timeText}</span>
          )}
          <span className="font-medium truncate">{event.title}</span>
          {hasRecurrence && <span className="opacity-75 shrink-0 text-[10px]">↻</span>}
        </div>
      );
    }

    const start = event.start;
    const end = event.end || start;
    const durationMinutes = start && end ? Math.round((end.getTime() - start.getTime()) / 60000) : 30;
    const isShort = durationMinutes <= 25;

    if (isShort) {
      // Single-line compact horizontal badge for short events (10 min, 15 min, 20 min)
      return (
        <div className="flex items-center gap-1.5 px-2 py-0.5 w-full h-full overflow-hidden leading-none select-none text-white">
          {timeText && (
            <span className="font-bold text-[10px] opacity-90 shrink-0 tracking-tight">
              {timeText}
            </span>
          )}
          <span className="font-semibold text-[11px] truncate flex-1">{event.title}</span>
          {hasRecurrence && <span className="opacity-80 shrink-0 text-[10px]">↻</span>}
        </div>
      );
    }

    // Standard multi-line layout for 30+ minute events
    return (
      <div className="flex flex-col justify-start p-1.5 w-full h-full overflow-hidden select-none leading-tight text-white">
        <div className="flex items-center justify-between gap-1 mb-0.5">
          <span className="font-bold text-[10px] opacity-90 tracking-tight">{timeText}</span>
          {hasRecurrence && <span className="opacity-80 text-[10px]">↻</span>}
        </div>
        <div className="font-semibold text-[11px] truncate">{event.title}</div>
        {event.extendedProps?.location && durationMinutes >= 45 && (
          <div className="text-[10px] opacity-80 truncate mt-0.5">
            📍 {event.extendedProps.location}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="h-full flex flex-col min-w-0">
      {/* Calendar Top Toolbar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pb-3 mb-2 border-b border-slate-100">
        <div className="flex items-center justify-between sm:justify-start gap-2 sm:gap-3 w-full sm:w-auto">
          <div className="flex items-center gap-1.5 shrink-0">
            <button
              onClick={handleToday}
              className="px-2.5 py-1 text-xs font-semibold text-slate-700 hover:text-slate-900 bg-slate-100 hover:bg-slate-200/80 rounded-lg transition-colors shadow-2xs cursor-pointer"
            >
              Today
            </button>
            <div className="flex items-center">
              <button
                onClick={handlePrev}
                className="p-1 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                title="Previous"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                onClick={handleNext}
                className="p-1 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                title="Next"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="flex items-center gap-1.5 min-w-0">
            <h2 className="text-sm sm:text-lg font-bold text-slate-800 tracking-tight truncate">
              {currentTitle || 'Calendar'}
            </h2>
            {isLoading && (
              <div className="w-3.5 h-3.5 border-2 border-brand-200 border-t-brand-600 rounded-full animate-spin shrink-0" />
            )}
          </div>
        </div>

        {/* View Switcher */}
        <div className="flex items-center gap-0.5 sm:gap-1 bg-slate-100/80 p-0.5 sm:p-1 rounded-xl border border-slate-200/50 self-end sm:self-auto shrink-0">
          <button
            onClick={() => handleViewChange('dayGridMonth')}
            className={`px-2.5 sm:px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
              currentView === 'dayGridMonth'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Month
          </button>
          <button
            onClick={() => handleViewChange('timeGridWeek')}
            className={`px-2.5 sm:px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
              currentView === 'timeGridWeek'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Week
          </button>
          <button
            onClick={() => handleViewChange('timeGridDay')}
            className={`px-2.5 sm:px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
              currentView === 'timeGridDay'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            Day
          </button>
        </div>
      </div>

      {/* FullCalendar Body */}
      <div className="flex-1 min-h-[450px] overflow-hidden">
        <FullCalendar
          ref={calendarRef}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView={isMobile ? 'timeGridDay' : 'timeGridWeek'}
          timeZone="local"
          headerToolbar={false}
          dayHeaderFormat={{ weekday: 'short', day: 'numeric', omitCommas: true }}
          allDaySlot={true}
          slotMinTime="00:00:00"
          slotMaxTime="24:00:00"
          scrollTime="08:00:00"
          nowIndicator={true}
          selectable={true}
          editable={true}
          eventDurationEditable={true}
          selectMirror={true}
          dayMaxEvents={3}
          eventMinHeight={22}
          eventShortHeight={26}
          eventContent={renderEventContent}
          events={fcEvents}
          datesSet={handleDatesSet}
          select={handleSelect}
          eventClick={handleEventClick}
          eventDrop={handleEventDrop}
          eventResize={handleEventResize}
          height="100%"
        />
      </div>
    </div>
  );
}
