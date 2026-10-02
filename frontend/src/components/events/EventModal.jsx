import React, { useState, useEffect } from 'react';
import { api } from '../../api/client';
import { getErrorMessage } from '../../api/errors';
import { useToast } from '../../context/ToastContext';
import { X, Calendar, Clock, MapPin, AlignLeft, RefreshCw, Bell, AlertTriangle, Trash2, Plus, ChevronDown } from 'lucide-react';
import { format, parseISO, addHours } from 'date-fns';

const PRESET_COLORS = [
  '#4f46e5', // Indigo
  '#3b82f6', // Blue
  '#06b6d4', // Cyan
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#f43f5e', // Rose
  '#8b5cf6', // Purple
  '#64748b', // Slate
];

const REMINDER_PRESETS = [
  { label: '5 minutes before', value: 5 },
  { label: '10 minutes before', value: 10 },
  { label: '15 minutes before', value: 15 },
  { label: '30 minutes before', value: 30 },
  { label: '1 hour before', value: 60 },
  { label: '1 day before', value: 1440 },
];

export default function EventModal({
  isOpen,
  onClose,
  onSaved,
  calendars = [],
  initialDate,
  event = null, // if editing an existing event
  editMode = 'ALL',
  occurrenceStart = null,
}) {
  const toast = useToast();
  const isEditing = !!event;

  // Find default calendar
  const defaultCal = calendars.find((c) => c.isDefault) || calendars[0];

  // Form states
  const [title, setTitle] = useState('');
  const [calendarId, setCalendarId] = useState('');
  const [allDay, setAllDay] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endDate, setEndDate] = useState('');
  const [endTime, setEndTime] = useState('10:00');
  const [location, setLocation] = useState('');
  const [description, setDescription] = useState('');
  const [color, setColor] = useState(PRESET_COLORS[0]);
  const [recurrenceFreq, setRecurrenceFreq] = useState('NONE');
  const [recurrenceUntil, setRecurrenceUntil] = useState('');
  const [reminders, setReminders] = useState([{ minutesBefore: 15, channel: 'IN_APP' }]);
  
  const [conflicts, setConflicts] = useState([]);
  const [isCheckingConflicts, setIsCheckingConflicts] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Initialize form
  useEffect(() => {
    if (!isOpen) return;

    if (event) {
      setTitle(event.title || '');
      setCalendarId(event.calendarId || defaultCal?.id || '');
      setAllDay(!!event.allDay);
      
      const start = event.startAt ? new Date(event.startAt) : new Date();
      const end = event.endAt ? new Date(event.endAt) : addHours(start, 1);
      
      setStartDate(format(start, 'yyyy-MM-dd'));
      setStartTime(format(start, 'HH:mm'));
      setEndDate(format(end, 'yyyy-MM-dd'));
      setEndTime(format(end, 'HH:mm'));
      
      setLocation(event.location || '');
      setDescription(event.description || '');
      setColor(event.color || defaultCal?.color || PRESET_COLORS[0]);

      if (event.recurrenceRule) {
        if (event.recurrenceRule.includes('DAILY')) setRecurrenceFreq('DAILY');
        else if (event.recurrenceRule.includes('WEEKLY')) setRecurrenceFreq('WEEKLY');
        else if (event.recurrenceRule.includes('MONTHLY')) setRecurrenceFreq('MONTHLY');
        else setRecurrenceFreq('CUSTOM');
      } else {
        setRecurrenceFreq('NONE');
      }

      setRecurrenceUntil(
        event.recurrenceUntil ? format(new Date(event.recurrenceUntil), 'yyyy-MM-dd') : ''
      );

      if (event.reminders && event.reminders.length > 0) {
        setReminders(event.reminders);
      } else {
        setReminders([{ minutesBefore: 15, channel: 'IN_APP' }]);
      }
    } else {
      // New event
      const baseStart = initialDate?.start
        ? new Date(initialDate.start)
        : initialDate instanceof Date
        ? initialDate
        : new Date();
      const baseEnd = initialDate?.end
        ? new Date(initialDate.end)
        : addHours(baseStart, 1);
      const isSlotAllDay = !!initialDate?.allDay;

      setTitle('');
      setCalendarId(defaultCal?.id || (calendars.length > 0 ? calendars[0].id : ''));
      setAllDay(isSlotAllDay);
      setStartDate(format(baseStart, 'yyyy-MM-dd'));
      setStartTime(format(baseStart, 'HH:mm'));
      setEndDate(format(baseEnd, 'yyyy-MM-dd'));
      setEndTime(format(baseEnd, 'HH:mm'));
      setLocation('');
      setDescription('');
      setColor(defaultCal?.color || PRESET_COLORS[0]);
      setRecurrenceFreq('NONE');
      setRecurrenceUntil('');
      setReminders([{ minutesBefore: 15, channel: 'IN_APP' }]);
    }
    setError('');
    setConflicts([]);
  }, [isOpen, event, initialDate, defaultCal]);

  // Fallback to guarantee a calendar is selected if calendars load after modal opens
  useEffect(() => {
    if (!calendarId && calendars && calendars.length > 0) {
      const target = calendars.find((c) => c.isDefault) || calendars[0];
      if (target) {
        setCalendarId(target.id);
        setColor(target.color || PRESET_COLORS[0]);
      }
    }
  }, [calendars, calendarId]);

  // Check availability conflicts
  useEffect(() => {
    if (!startDate || !startTime || !endDate || !endTime) return;

    let isMounted = true;
    const checkTimer = setTimeout(async () => {
      try {
        setIsCheckingConflicts(true);
        const startIso = allDay
          ? new Date(`${startDate}T00:00:00Z`).toISOString()
          : new Date(`${startDate}T${startTime}:00`).toISOString();
        const endIso = allDay
          ? new Date(`${endDate}T23:59:59Z`).toISOString()
          : new Date(`${endDate}T${endTime}:00`).toISOString();

        if (new Date(endIso) > new Date(startIso)) {
          const res = await api.events.checkAvailability(startIso, endIso, event?.id);
          if (isMounted) {
            setConflicts(res.conflicts || []);
          }
        }
      } catch (err) {
        // tolerate conflict check failures
      } finally {
        if (isMounted) setIsCheckingConflicts(false);
      }
    }, 400);

    return () => {
      isMounted = false;
      clearTimeout(checkTimer);
    };
  }, [startDate, startTime, endDate, endTime, allDay, event]);

  if (!isOpen) return null;

  const handleAddReminder = () => {
    if (reminders.length < 5) {
      setReminders([...reminders, { minutesBefore: 15, channel: 'IN_APP' }]);
    }
  };

  const handleRemoveReminder = (idx) => {
    setReminders(reminders.filter((_, i) => i !== idx));
  };

  const handleReminderChange = (idx, field, val) => {
    setReminders(
      reminders.map((r, i) => (i === idx ? { ...r, [field]: val } : r))
    );
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim()) {
      setError('Please provide an event title.');
      return;
    }
    if (!calendarId) {
      setError('Please select a calendar.');
      return;
    }

    const startInstant = allDay
      ? `${startDate}T00:00:00Z`
      : new Date(`${startDate}T${startTime}:00`).toISOString();
    const endInstant = allDay
      ? `${endDate}T23:59:59Z`
      : new Date(`${endDate}T${endTime}:00`).toISOString();

    if (new Date(endInstant) <= new Date(startInstant)) {
      setError('Event end time must be after the start time.');
      return;
    }

    let rrule = null;
    if (recurrenceFreq === 'DAILY') rrule = 'FREQ=DAILY';
    else if (recurrenceFreq === 'WEEKLY') rrule = 'FREQ=WEEKLY';
    else if (recurrenceFreq === 'MONTHLY') rrule = 'FREQ=MONTHLY';

    const untilInstant = recurrenceUntil ? `${recurrenceUntil}T23:59:59Z` : null;

    const payload = {
      calendarId: Number(calendarId),
      title: title.trim(),
      description: description.trim() || null,
      location: location.trim() || null,
      color: color || null,
      allDay,
      startAt: startInstant,
      endAt: endInstant,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      recurrenceRule: rrule,
      recurrenceUntil: untilInstant,
      version: event?.version,
      reminders: reminders.filter((r) => r.minutesBefore > 0),
    };

    try {
      setIsSubmitting(true);
      setError('');

      if (isEditing) {
        await api.events.update(event.id, payload, editMode, occurrenceStart);
        toast.success('Event updated successfully');
      } else {
        await api.events.create(payload);
        toast.success('Event created successfully');
      }

      onSaved && onSaved();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full border border-slate-100 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div
              className="w-3.5 h-3.5 rounded-full ring-2 ring-white shadow-xs"
              style={{ backgroundColor: color }}
            />
            <h3 className="font-semibold text-base text-slate-800">
              {isEditing ? 'Edit Event' : 'Create New Event'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-5 flex-1">
          {error && (
            <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800 flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
              <span className="leading-relaxed">{error}</span>
            </div>
          )}

          {/* Conflict Warning Banner */}
          {conflicts.length > 0 && (
            <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-start gap-2.5">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Scheduling conflict detected:</p>
                <p className="mt-0.5 text-amber-700">
                  Overlaps with: {conflicts.map((c) => c.title).join(', ')}
                </p>
              </div>
            </div>
          )}

          {/* Title Input */}
          <div>
            <input
              type="text"
              required
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Add event title..."
              className="w-full text-lg pl-2 pt-2 font-bold text-slate-900 placeholder-slate-400 border-0 border-b-2 border-slate-100 pb-2.5 focus:ring-0 focus:border-brand-600 transition-colors bg-transparent"
            />
          </div>

          {/* Calendar Picker & Color */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
            <div>
              <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Calendar
              </label>
              <div className="relative">
                <select
                  value={calendarId}
                  onChange={(e) => {
                    setCalendarId(e.target.value);
                    const selected = calendars.find((c) => c.id === Number(e.target.value));
                    if (selected?.color) setColor(selected.color);
                  }}
                  className="w-full appearance-none bg-slate-50/60 hover:bg-white focus:bg-white text-xs font-medium text-slate-700 rounded-xl border border-slate-200 py-2.5 pl-3.5 pr-10 focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 transition-all cursor-pointer shadow-2xs"
                >
                  {calendars.map((cal) => (
                    <option key={cal.id} value={cal.id}>
                      {cal.name} {cal.isDefault ? '(Default)' : ''}
                    </option>
                  ))}
                </select>
                <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            </div>

            <div>
              <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
                Event Color
              </label>
              <div className="flex items-center gap-2 py-1.5">
                {PRESET_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setColor(c)}
                    className={`w-6 h-6 rounded-full transition-all relative flex items-center justify-center ${
                      color === c
                        ? 'scale-115 ring-2 ring-brand-500 ring-offset-2 shadow-sm'
                        : 'hover:scale-108 opacity-85 hover:opacity-100'
                    }`}
                    style={{ backgroundColor: c }}
                  >
                    {color === c && (
                      <span className="w-1.5 h-1.5 bg-white rounded-full shadow-xs" />
                    )}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Date & Time */}
          <div className="space-y-2.5 pt-1">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-brand-600" />
                Time & Date
              </span>
              <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-600 select-none">
                <input
                  type="checkbox"
                  checked={allDay}
                  onChange={(e) => setAllDay(e.target.checked)}
                  className="rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                />
                All day
              </label>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3 bg-slate-50/70 border border-slate-200/80 rounded-xl space-y-1.5">
                <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Starts
                </label>
                <div className="flex gap-2">
                  <input
                    type="date"
                    required
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="flex-1 text-xs font-medium rounded-lg border border-slate-200 bg-white py-1.5 px-2.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-500 shadow-2xs"
                  />
                  {!allDay && (
                    <input
                      type="time"
                      required
                      value={startTime}
                      onChange={(e) => setStartTime(e.target.value)}
                      className="w-24 text-xs font-medium rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-500 shadow-2xs"
                    />
                  )}
                </div>
              </div>

              <div className="p-3 bg-slate-50/70 border border-slate-200/80 rounded-xl space-y-1.5">
                <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Ends
                </label>
                <div className="flex gap-2">
                  <input
                    type="date"
                    required
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="flex-1 text-xs font-medium rounded-lg border border-slate-200 bg-white py-1.5 px-2.5 text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-500 shadow-2xs"
                  />
                  {!allDay && (
                    <input
                      type="time"
                      required
                      value={endTime}
                      onChange={(e) => setEndTime(e.target.value)}
                      className="w-24 text-xs font-medium rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-500 shadow-2xs"
                    />
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Recurrence Selector */}
          <div className="space-y-1.5 pt-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <RefreshCw className="w-3.5 h-3.5 text-brand-600" />
              Repeat
            </span>
            <div className={`grid gap-3 ${recurrenceFreq !== 'NONE' ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-1'}`}>
              <div className="relative">
                <select
                  value={recurrenceFreq}
                  onChange={(e) => setRecurrenceFreq(e.target.value)}
                  className="w-full appearance-none bg-slate-50/60 hover:bg-white focus:bg-white text-xs font-medium text-slate-700 rounded-xl border border-slate-200 py-2.5 pl-3.5 pr-10 focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 transition-all cursor-pointer shadow-2xs"
                >
                  <option value="NONE">Does not repeat</option>
                  <option value="DAILY">Repeats Daily</option>
                  <option value="WEEKLY">Repeats Weekly</option>
                  <option value="MONTHLY">Repeats Monthly</option>
                </select>
                <ChevronDown className="w-4 h-4 text-slate-400 absolute right-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>

              {recurrenceFreq !== 'NONE' && (
                <div className="relative">
                  <input
                    type="date"
                    value={recurrenceUntil}
                    onChange={(e) => setRecurrenceUntil(e.target.value)}
                    placeholder="Repeat until"
                    className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-slate-50/60 hover:bg-white focus:bg-white py-2.5 px-3.5 text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-500/20 shadow-2xs"
                  />
                </div>
              )}
            </div>
          </div>

          {/* Location */}
          <div>
            <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1.5 flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              Location
            </label>
            <input
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="Conference room, Zoom link, or address..."
              className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-slate-50/60 hover:bg-white focus:bg-white py-2.5 px-3.5 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 transition-all shadow-2xs"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1.5 flex items-center gap-1.5">
              <AlignLeft className="w-3.5 h-3.5 text-slate-400" />
              Description
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Add agenda, notes, or links..."
              className="w-full text-xs font-medium rounded-xl border border-slate-200 bg-slate-50/60 hover:bg-white focus:bg-white py-2.5 px-3.5 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20 transition-all shadow-2xs"
            />
          </div>

          {/* Reminders Section */}
          <div className="space-y-2.5 pt-1">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <Bell className="w-3.5 h-3.5 text-brand-600" />
                Reminders
              </span>
              {reminders.length < 3 && (
                <button
                  type="button"
                  onClick={handleAddReminder}
                  className="text-xs text-brand-600 hover:text-brand-700 font-semibold flex items-center gap-1 px-2 py-0.5 rounded-lg hover:bg-brand-50 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Reminder
                </button>
              )}
            </div>

            <div className="space-y-2">
              {reminders.map((r, idx) => (
                <div key={idx} className="flex items-center gap-2.5">
                  <div className="relative w-32">
                    <select
                      value={r.channel}
                      onChange={(e) => handleReminderChange(idx, 'channel', e.target.value)}
                      className="w-full appearance-none text-xs font-medium rounded-xl border border-slate-200 bg-slate-50/60 hover:bg-white focus:bg-white py-2 pl-3 pr-8 text-slate-700 focus:outline-none focus:border-brand-500 shadow-2xs cursor-pointer"
                    >
                      <option value="IN_APP">In-App</option>
                      <option value="EMAIL">Email</option>
                      <option value="SMS">SMS</option>
                    </select>
                    <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  </div>

                  <div className="relative flex-1">
                    <select
                      value={r.minutesBefore}
                      onChange={(e) => handleReminderChange(idx, 'minutesBefore', Number(e.target.value))}
                      className="w-full appearance-none text-xs font-medium rounded-xl border border-slate-200 bg-slate-50/60 hover:bg-white focus:bg-white py-2 pl-3 pr-8 text-slate-700 focus:outline-none focus:border-brand-500 shadow-2xs cursor-pointer"
                    >
                      {REMINDER_PRESETS.map((p) => (
                        <option key={p.value} value={p.value}>
                          {p.label}
                        </option>
                      ))}
                    </select>
                    <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  </div>

                  <button
                    type="button"
                    onClick={() => handleRemoveReminder(idx)}
                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                    title="Remove reminder"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Footer Actions */}
          <div className="pt-4 border-t border-slate-100 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2.5 text-xs font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-xl shadow-md shadow-brand-500/20 disabled:opacity-60 transition-all hover:scale-[1.02] active:scale-[0.98] flex items-center gap-1.5"
            >
              {isSubmitting ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Event'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
