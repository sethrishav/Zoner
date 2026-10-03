import React, { useState, useEffect } from 'react';
import {
  X,
  Calendar,
  Clock,
  MapPin,
  AlignLeft,
  RefreshCw,
  Bell,
  Edit3,
  Trash2,
  Users,
  CheckCircle2,
  XCircle,
  HelpCircle,
  Check,
} from 'lucide-react';
import { format } from 'date-fns';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../api/client';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../api/errors';

export default function EventDetailModal({
  isOpen,
  event,
  calendar,
  onClose,
  onEdit,
  onDelete,
  onRsvpSuccess,
}) {
  const { user } = useAuth();
  const toast = useToast();

  const [localAttendees, setLocalAttendees] = useState(event?.attendees || []);
  const [isSubmittingRsvp, setIsSubmittingRsvp] = useState(false);

  useEffect(() => {
    if (event?.attendees) {
      setLocalAttendees(event.attendees);
    } else {
      setLocalAttendees([]);
    }
  }, [event]);

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

  // Find current user's RSVP status
  const myAttendee = localAttendees.find(
    (a) => a.email.toLowerCase() === user?.email?.toLowerCase()
  );
  const myStatus = myAttendee?.status;

  const handleRsvp = async (newStatus) => {
    const eventId = event.rawId || event.id;
    if (!eventId) return;

    try {
      setIsSubmittingRsvp(true);
      const updated = await api.events.rsvp(eventId, newStatus);
      if (updated?.attendees) {
        setLocalAttendees(updated.attendees);
      } else {
        setLocalAttendees((prev) => {
          const exists = prev.find((a) => a.email.toLowerCase() === user?.email?.toLowerCase());
          if (exists) {
            return prev.map((a) =>
              a.email.toLowerCase() === user?.email?.toLowerCase()
                ? { ...a, status: newStatus }
                : a
            );
          }
          return [...prev, { email: user.email, displayName: user.displayName, status: newStatus }];
        });
      }

      const statusLabels = {
        ACCEPTED: 'Going',
        TENTATIVE: 'Maybe',
        DECLINED: 'Not going',
      };
      toast.success(`RSVP updated: ${statusLabels[newStatus] || newStatus}`);
      onRsvpSuccess && onRsvpSuccess();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setIsSubmittingRsvp(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACCEPTED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 border border-emerald-200/60 dark:border-emerald-800/60">
            <CheckCircle2 className="w-3 h-3 text-emerald-600 dark:text-emerald-400" />
            Going
          </span>
        );
      case 'TENTATIVE':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-50 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 border border-amber-200/60 dark:border-amber-800/60">
            <HelpCircle className="w-3 h-3 text-amber-600 dark:text-amber-400" />
            Maybe
          </span>
        );
      case 'DECLINED':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 border border-rose-200/60 dark:border-rose-800/60">
            <XCircle className="w-3 h-3 text-rose-600 dark:text-rose-400" />
            Declined
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            <Clock className="w-3 h-3 text-slate-400" />
            Pending
          </span>
        );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full border border-slate-100 dark:border-slate-800 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Color top banner */}
        <div
          className="h-3 w-full shrink-0"
          style={{ backgroundColor: event.color || calendar?.color || '#4f46e5' }}
        />

        <div className="p-5 overflow-y-auto flex-1">
          {/* Header */}
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: calendar?.color || '#4f46e5' }}
                />
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                  {calendar?.name || 'Calendar'}
                </span>
                {!canEdit && (
                  <span className="px-1.5 py-0.2 bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 text-[10px] font-semibold rounded">
                    View only
                  </span>
                )}
              </div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 leading-snug">
                {event.title}
              </h2>
            </div>
            <button
              onClick={onClose}
              className="p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Quick RSVP Section */}
          <div className="mt-4 p-3 bg-slate-50/80 dark:bg-slate-800/60 rounded-xl border border-slate-200/70 dark:border-slate-700/70 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
            <span className="text-xs font-semibold text-slate-700 dark:text-slate-200 flex items-center gap-1.5">
              <span>Going?</span>
              {myStatus && (
                <span className="font-normal text-[11px] text-slate-400">
                  ({myStatus === 'ACCEPTED' ? 'Going' : myStatus === 'DECLINED' ? 'Declined' : 'Maybe'})
                </span>
              )}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                disabled={isSubmittingRsvp}
                onClick={() => handleRsvp('ACCEPTED')}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer flex items-center gap-1 ${
                  myStatus === 'ACCEPTED'
                    ? 'bg-emerald-600 text-white shadow-xs'
                    : 'bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 hover:bg-emerald-50 dark:hover:bg-emerald-950/50 border border-slate-200 dark:border-slate-600'
                }`}
              >
                <Check className="w-3 h-3" />
                Yes
              </button>
              <button
                type="button"
                disabled={isSubmittingRsvp}
                onClick={() => handleRsvp('TENTATIVE')}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer flex items-center gap-1 ${
                  myStatus === 'TENTATIVE'
                    ? 'bg-amber-500 text-white shadow-xs'
                    : 'bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 hover:bg-amber-50 dark:hover:bg-amber-950/50 border border-slate-200 dark:border-slate-600'
                }`}
              >
                <HelpCircle className="w-3 h-3" />
                Maybe
              </button>
              <button
                type="button"
                disabled={isSubmittingRsvp}
                onClick={() => handleRsvp('DECLINED')}
                className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer flex items-center gap-1 ${
                  myStatus === 'DECLINED'
                    ? 'bg-rose-600 text-white shadow-xs'
                    : 'bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 hover:bg-rose-50 dark:hover:bg-rose-950/50 border border-slate-200 dark:border-slate-600'
                }`}
              >
                <X className="w-3 h-3" />
                No
              </button>
            </div>
          </div>

          {/* Details list */}
          <div className="mt-4 space-y-3 text-xs text-slate-600 dark:text-slate-300">
            {/* Time */}
            <div className="flex items-center gap-3">
              <Clock className="w-4 h-4 text-slate-400 flex-shrink-0" />
              <span className="font-medium text-slate-700 dark:text-slate-200">{formatEventDate()}</span>
            </div>

            {/* Recurrence rule */}
            {event.recurrenceRule && (
              <div className="flex items-center gap-3">
                <RefreshCw className="w-4 h-4 text-brand-600 dark:text-brand-400 flex-shrink-0" />
                <span className="text-brand-700 dark:text-brand-300 font-medium">
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
                <p className="whitespace-pre-line text-slate-600 dark:text-slate-300 leading-relaxed">
                  {event.description}
                </p>
              </div>
            )}

            {/* Attendees list */}
            {localAttendees.length > 0 && (
              <div className="pt-2 border-t border-slate-100 dark:border-slate-800">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
                    <Users className="w-3.5 h-3.5 text-brand-600 dark:text-brand-400" />
                    Guests / Attendees ({localAttendees.length})
                  </span>
                </div>
                <div className="space-y-1.5 max-h-36 overflow-y-auto pr-1">
                  {localAttendees.map((att, idx) => (
                    <div
                      key={att.id || att.email || idx}
                      className="flex items-center justify-between gap-2 p-2 rounded-lg bg-slate-50/70 dark:bg-slate-800/50 border border-slate-100 dark:border-slate-800"
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <div className="w-6 h-6 rounded-full bg-brand-100 dark:bg-brand-900/60 text-brand-700 dark:text-brand-300 text-[10px] font-bold flex items-center justify-center shrink-0">
                          {att.email.charAt(0).toUpperCase()}
                        </div>
                        <span className="text-xs font-medium text-slate-800 dark:text-slate-200 truncate">
                          {att.email}
                          {user?.email?.toLowerCase() === att.email.toLowerCase() && (
                            <span className="text-[10px] text-slate-400 font-normal ml-1">(you)</span>
                          )}
                        </span>
                      </div>
                      <div className="shrink-0">{getStatusBadge(att.status)}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Reminders */}
            {event.reminders && event.reminders.length > 0 && (
              <div className="flex items-start gap-3 pt-1">
                <Bell className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" />
                <div className="space-y-0.5">
                  {event.reminders.map((r, i) => (
                    <p key={i} className="text-slate-500 dark:text-slate-400">
                      {r.minutesBefore}m before via {r.channel}
                    </p>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Action Bar */}
          {canEdit && (
            <div className="mt-6 pt-4 border-t border-slate-100 dark:border-slate-800 flex items-center justify-end gap-2">
              <button
                onClick={() => {
                  onDelete(event);
                }}
                className="px-3 py-1.5 text-xs font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 rounded-lg transition-colors flex items-center gap-1.5 cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Delete</span>
              </button>
              <button
                onClick={() => {
                  onEdit(event);
                }}
                className="px-4 py-1.5 text-xs font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-lg shadow-sm transition-colors flex items-center gap-1.5 cursor-pointer"
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
