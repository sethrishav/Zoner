import React, { useState, useEffect, useCallback } from 'react';
import AppShell from '../components/layout/AppShell';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Calendar as CalendarIcon, Plus, Users, Sparkles, Clock, Globe } from 'lucide-react';
import { format } from 'date-fns';

export default function CalendarPage() {
  const { user } = useAuth();
  const [calendars, setCalendars] = useState([]);
  const [selectedCalendarIds, setSelectedCalendarIds] = useState([]);
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [isLoading, setIsLoading] = useState(true);

  // Load calendars
  const loadCalendars = useCallback(async () => {
    try {
      setIsLoading(true);
      const data = await api.calendars.list();
      setCalendars(data);
      // Select all enabled calendars by default
      const enabledIds = data.filter((c) => c.enabled !== false).map((c) => c.id);
      setSelectedCalendarIds(enabledIds);
    } catch (err) {
      console.error('Failed to load calendars', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCalendars();
  }, [loadCalendars]);

  const handleToggleCalendar = (id) => {
    setSelectedCalendarIds((prev) =>
      prev.includes(id) ? prev.filter((calId) => calId !== id) : [...prev, id]
    );
  };

  return (
    <AppShell
      calendars={calendars}
      selectedCalendarIds={selectedCalendarIds}
      onToggleCalendar={handleToggleCalendar}
      onOpenCreateEvent={() => alert('Event creation modal will be wired in M7')}
      onOpenCreateCalendar={() => alert('Calendar creation will be wired in M7')}
      onOpenShareCalendar={(cal) => alert(`Share ${cal.name}`)}
      onOpenSearch={() => alert('Search will be wired in M7')}
      selectedDate={selectedDate}
      onSelectDate={setSelectedDate}
    >
      <div className="h-full flex flex-col">
        {/* Header with selected date banner and timezone badge */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-100">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-800">
              {format(selectedDate, 'EEEE, MMMM d, yyyy')}
            </h1>
            <p className="text-xs text-slate-400 mt-1 flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5 text-brand-600" />
              <span>Viewing in {user?.timeZone || 'UTC'}</span>
            </p>
          </div>

          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-brand-50 text-brand-700 border border-brand-200/50">
              <Sparkles className="w-3.5 h-3.5 text-brand-500" />
              Milestone 6: Frontend Foundation Ready
            </span>
          </div>
        </div>

        {/* Content Area / Empty Calendar Canvas */}
        {isLoading ? (
          <div className="flex-1 flex flex-col items-center justify-center">
            <div className="w-8 h-8 border-3 border-brand-200 border-t-brand-600 rounded-full animate-spin"></div>
            <p className="text-xs text-slate-400 mt-3 font-medium">Syncing calendars...</p>
          </div>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-center p-8">
            <div className="w-16 h-16 bg-gradient-to-tr from-brand-100 to-indigo-50 border border-brand-200/60 rounded-2xl flex items-center justify-center shadow-inner mb-4">
              <CalendarIcon className="w-8 h-8 text-brand-600" />
            </div>

            <h2 className="text-lg font-semibold text-slate-800">
              Welcome to your Zoner Calendar, {user?.displayName || 'there'}!
            </h2>
            <p className="max-w-md text-sm text-slate-500 mt-1.5">
              Your default calendar <strong className="text-slate-700 font-medium">"{calendars.find(c => c.isDefault)?.name || 'Personal'}"</strong> is active and synced.
            </p>

            <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
              <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-left max-w-xs">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
                  <Clock className="w-4 h-4 text-brand-600" />
                  Time-Zone Engine
                </div>
                <p className="text-xs text-slate-400 mt-1">
                  Accurate across UTC instants and wall-clock times.
                </p>
              </div>

              <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-left max-w-xs">
                <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
                  <Users className="w-4 h-4 text-emerald-600" />
                  Sharing & Permissions
                </div>
                <p className="text-xs text-slate-400 mt-1">
                  Enforces VIEW/EDIT with strict access policies.
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
