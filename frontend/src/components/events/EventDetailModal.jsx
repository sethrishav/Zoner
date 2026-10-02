import React from 'react';
import { X, Calendar, Clock, MapPin, AlignLeft, RefreshCw, Bell, Edit3, Trash2, Globe } from 'lucide-react';
import { format, parseISO } from 'date-fns';

export default function EventDetailModal({
  isOpen,
  event,
  calendar,
  onClose,
  onEdit,
  onDelete,
}) {
  if (!isOpen || !event) return null;

  const canEdit = calendar?.isOwner || calendar?.permission === 'EDIT';

  const formatEventDate = () => {
    try {
      const start = event.start ? new Date(event.start) : new Date(event.startAt);
      const end = event.end ? new Date(event.end) : new Date(event.endAt);

      if (event.allDay) {
        return format(start, 'EEEE, MMMM d, yyyy (All day)');
      }

      const sameDay = format(start, 'yyyy-MM-dd') === format(end, 'yyyy-MM-dd');
      if (sameDay) {
        return `${format(start, 'EEEE, MMMM d, yyyy')} · ${format(start, 'h:mm a')} – ${format(end, 'h:mm a')}`;
      }
      return `${format(start, 'MMM d, h:mm a')} – ${format(end, 'MMM d, h:mm a')}`;
    } catch (e) {
      return '';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full border border-slate-100 overflow-hidden">
        {/* Color top banner */}
        <div
          className="h-3 w-full"
          style={{ backgroundColor: event.color || calendar?.color || '#4f46e5' }}
        />

        <div className="p-5">
          {/* Header */}
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: calendar?.color || '#4f46e5' }}
                />
                <span className="text-xs font-semibold text-slate-500">
                  {calendar?.name || 'Calendar'}
                </span>
                {!canEdit && (
                  <span className="px-1.5 py-0.2 bg-slate-100 text-slate-500 text-[10px] font-semibold rounded">
                    View only
                  </span>
                )}
              </div>
              <h2 className="text-lg font-bold text-slate-900 leading-snug">
                {event.title}
              </h2>
            </div>
            <button
              onClick={onClose}
              className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Details list */}
          <div className="mt-4 space-y-3 text-xs text-slate-600">
            {/* Time */}
            <div className="flex items-center gap-3">
              <Clock className="w-4 h-4 text-slate-400 flex-shrink-0" />
              <span className="font-medium text-slate-700">{formatEventDate()}</span>
            </div>

            {/* Recurrence rule */}
            {event.recurrenceRule && (
              <div className="flex items-center gap-3">
                <RefreshCw className="w-4 h-4 text-brand-600 flex-shrink-0" />
                <span className="text-brand-700 font-medium">
                  {event.recurrenceRule.includes('DAILY')
                    ? 'Repeats daily'
                    : event.recurrenceRule.includes('WEEKLY')
                    ? 'Repeats weekly'
                    : event.recurrenceRule.includes('MONTHLY')
                    ? 'Repeats monthly'
                    : 'Repeats according to custom rule'}
                </span>
              </div>
            )}

            {/* Location */}
            {event.location && (
              <div className="flex items-center gap-3">
                <MapPin className="w-4 h-4 text-slate-400 flex-shrink-0" />
                <span className="truncate">{event.location}</span>
              </div>
            )}

            {/* Description */}
            {event.description && (
              <div className="flex items-start gap-3 pt-1">
                <AlignLeft className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" />
                <p className="whitespace-pre-line text-slate-600 leading-relaxed">
                  {event.description}
                </p>
              </div>
            )}

            {/* Reminders */}
            {event.reminders && event.reminders.length > 0 && (
              <div className="flex items-start gap-3 pt-1">
                <Bell className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" />
                <div className="space-y-0.5">
                  {event.reminders.map((r, i) => (
                    <p key={i} className="text-slate-500">
                      {r.minutesBefore}m before via {r.channel}
                    </p>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Action Bar */}
          {canEdit && (
            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-end gap-2">
              <button
                onClick={() => {
                  onDelete(event);
                }}
                className="px-3 py-1.5 text-xs font-semibold text-rose-600 hover:bg-rose-50 rounded-lg transition-colors flex items-center gap-1.5"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Delete</span>
              </button>
              <button
                onClick={() => {
                  onEdit(event);
                }}
                className="px-4 py-1.5 text-xs font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>Edit</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
