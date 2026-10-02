import React, { useState, useEffect } from 'react';
import { api } from '../../api/client';
import { getErrorMessage } from '../../api/errors';
import { useToast } from '../../context/ToastContext';
import { X, Calendar, Clock, MapPin, AlignLeft, RefreshCw, Bell, AlertTriangle, Trash2, Plus } from 'lucide-react';
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
      const base = initialDate ? new Date(initialDate) : new Date();
      setTitle('');
      setCalendarId(defaultCal?.id || '');
      setAllDay(false);
      setStartDate(format(base, 'yyyy-MM-dd'));
      setStartTime('09:00');
      setEndDate(format(base, 'yyyy-MM-dd'));
      setEndTime('10:00');
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

  // Check availability conflicts
  useEffect(() => {
    if (!startDate || !startTime || !endDate || !endTime) return;

    let isMounted = true;
    const checkTimer = setTimeout(async () => {
      try {
        setIsCheckingConflicts(true);
        const startIso = allDay
          ? new Date(`${startDate}T00:00:00Z`).toISOString()
          : new Date(`${startDate}T${startTime}:00Z`).toISOString();
        const endIso = allDay
          ? new Date(`${endDate}T23:59:59Z`).toISOString()
          : new Date(`${endDate}T${endTime}:00Z`).toISOString();

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
      : `${startDate}T${startTime}:00Z`;
    const endInstant = allDay
      ? `${endDate}T23:59:59Z`
      : `${endDate}T${endTime}:00Z`;

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
        <form onSubmit={handleSubmit} className="p-5 overflow-y-auto space-y-4 flex-1">
          {error && (
            <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800 flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {/* Conflict Warning Banner */}
          {conflicts.length > 0 && (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Scheduling conflict detected:</p>
                <p className="mt-0.5 text-amber-700">
                  Overlaps with: {conflicts.map((c) => c.title).join(', ')}
                </p>
              </div>
            </div>
          )}

          {/* Title */}
          <div>
            <input
              type="text"
              required
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Add event title..."
              className="w-full text-base font-semibold text-slate-900 placeholder-slate-400 border-0 border-b border-slate-200 pb-2 focus:ring-0 focus:border-brand-600 transition-colors"
            />
          </div>

          {/* Calendar Picker & Color */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-1">
                Calendar
              </label>
              <select
                value={calendarId}
                onChange={(e) => {
                  setCalendarId(e.target.value);
                  const selected = calendars.find((c) => c.id === Number(e.target.value));
                  if (selected?.color) setColor(selected.color);
                }}
                className="w-full text-xs rounded-lg border border-slate-200 bg-white py-2 px-2.5 text-slate-700 focus:outline-none focus:ring-1 focus:ring-brand-500"
              >
                {calendars.map((cal) => (
                  <option key={cal.id} value={cal.id}>
                    {cal.name} {cal.isDefault ? '(Default)' : ''}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-1">
                Event Color
              </label>
              <div className="flex items-center gap-1.5 py-1">
                {PRESET_COLORS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setColor(c)}
                    className={`w-5 h-5 rounded-full transition-transform ${
                      color === c ? 'scale-125 ring-2 ring-slate-400 ring-offset-1' : 'hover:scale-110'
                    }`}
                    style={{ backgroundColor: c }}
                  />
                ))}
              </div>
            </div>
          </div>

          {/* Date & Time */}
          <div className="space-y-2 pt-1">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" />
                Time & Date
              </span>
              <label className="flex items-center gap-2 cursor-pointer text-xs text-slate-600">
                <input
                  type="checkbox"
                  checked={allDay}
                  onChange={(e) => setAllDay(e.target.checked)}
                  className="rounded text-brand-600 focus:ring-brand-500"
                />
                All day
              </label>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[10px] text-slate-400 mb-0.5">Start</label>
                <div className="flex gap-2">
                  <input
                    type="date"
                    required
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="flex-1 text-xs rounded-lg border border-slate-200 py-1.5 px-2 text-slate-700"
                  />
                  {!allDay && (
                    <input
                      type="time"
                      required
                      value={startTime}
                      onChange={(e) => setStartTime(e.target.value)}
                      className="w-24 text-xs rounded-lg border border-slate-200 py-1.5 px-2 text-slate-700"
                    />
                  )}
                </div>
              </div>

              <div>
                <label className="block text-[10px] text-slate-400 mb-0.5">End</label>
                <div className="flex gap-2">
                  <input
                    type="date"
                    required
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="flex-1 text-xs rounded-lg border border-slate-200 py-1.5 px-2 text-slate-700"
                  />
                  {!allDay && (
                    <input
                      type="time"
                      required
                      value={endTime}
                      onChange={(e) => setEndTime(e.target.value)}
                      className="w-24 text-xs rounded-lg border border-slate-200 py-1.5 px-2 text-slate-700"
                    />
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Recurrence Selector */}
          <div className="space-y-1.5 pt-1">
            <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <RefreshCw className="w-3.5 h-3.5" />
              Repeat
            </span>
            <div className="grid grid-cols-2 gap-3">
              <select
                value={recurrenceFreq}
                onChange={(e) => setRecurrenceFreq(e.target.value)}
                className="w-full text-xs rounded-lg border border-slate-200 bg-white py-2 px-2.5 text-slate-700"
              >
                <option value="NONE">Does not repeat</option>
                <option value="DAILY">Repeats Daily</option>
                <option value="WEEKLY">Repeats Weekly</option>
                <option value="MONTHLY">Repeats Monthly</option>
              </select>

              {recurrenceFreq !== 'NONE' && (
                <div>
                  <input
                    type="date"
                    value={recurrenceUntil}
                    onChange={(e) => setRecurrenceUntil(e.target.value)}
                    placeholder="Repeat until"
                    className="w-full text-xs rounded-lg border border-slate-200 py-2 px-2.5 text-slate-700"
                  />
                </div>
              )}
            </div>
          </div>

          {/* Location */}
          <div>
            <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-1 flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" />
              Location
            </label>
            <input
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="Room 402, Zoom, or coffee shop..."
              className="w-full text-xs rounded-lg border border-slate-200 py-2 px-3 text-slate-700 placeholder-slate-400"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 mb-1 flex items-center gap-1.5">
              <AlignLeft className="w-3.5 h-3.5" />
              Description
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Add agenda, notes, or links..."
              className="w-full text-xs rounded-lg border border-slate-200 py-2 px-3 text-slate-700 placeholder-slate-400"
            />
          </div>

          {/* Reminders Section */}
          <div className="space-y-2 pt-1">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <Bell className="w-3.5 h-3.5" />
                Reminders
              </span>
              {reminders.length < 3 && (
                <button
                  type="button"
                  onClick={handleAddReminder}
                  className="text-xs text-brand-600 hover:text-brand-700 font-medium flex items-center gap-1"
                >
                  <Plus className="w-3 h-3" />
                  Add Reminder
                </button>
              )}
            </div>

            <div className="space-y-2">
              {reminders.map((r, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <select
                    value={r.channel}
                    onChange={(e) => handleReminderChange(idx, 'channel', e.target.value)}
                    className="text-xs rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-slate-700"
                  >
                    <option value="IN_APP">In-App</option>
                    <option value="EMAIL">Email</option>
                    <option value="SMS">SMS</option>
                  </select>

                  <select
                    value={r.minutesBefore}
                    onChange={(e) => handleReminderChange(idx, 'minutesBefore', Number(e.target.value))}
                    className="flex-1 text-xs rounded-lg border border-slate-200 bg-white py-1.5 px-2 text-slate-700"
                  >
                    {REMINDER_PRESETS.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </select>

                  <button
                    type="button"
                    onClick={() => handleRemoveReminder(idx)}
                    className="p-1.5 text-slate-400 hover:text-rose-600 rounded"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Footer Actions */}
          <div className="pt-3 border-t border-slate-100 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2 text-xs font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-lg shadow-sm disabled:opacity-60 transition-colors flex items-center gap-1.5"
            >
              {isSubmitting ? 'Saving...' : isEditing ? 'Save Changes' : 'Create Event'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
