import React, { useState } from 'react';
import { Plus, Calendar as CalendarIcon, Users, Check, ChevronLeft, ChevronRight, Share2, Settings, X } from 'lucide-react';
import { format, addMonths, subMonths, startOfMonth, endOfMonth, startOfWeek, endOfWeek, eachDayOfInterval, isSameMonth, isSameDay, isToday } from 'date-fns';

export default function Sidebar({
  calendars = [],
  selectedCalendarIds = [],
  onToggleCalendar,
  onOpenCreateEvent,
  onOpenCreateCalendar,
  onOpenShareCalendar,
  selectedDate = new Date(),
  onSelectDate,
  onClose,
  isMobile = false,
}) {
  const [miniDate, setMiniDate] = useState(selectedDate);

  // Group calendars into owned and shared
  const myCalendars = calendars.filter((c) => c.isOwner || c.permission === 'OWNER');
  const sharedCalendars = calendars.filter((c) => !c.isOwner && c.permission !== 'OWNER');

  // Mini-calendar generation
  const monthStart = startOfMonth(miniDate);
  const monthEnd = endOfMonth(monthStart);
  const startDate = startOfWeek(monthStart);
  const endDate = endOfWeek(monthEnd);
  const days = eachDayOfInterval({ start: startDate, end: endDate });

  return (
    <aside className={`w-full md:w-64 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 flex flex-col ${isMobile ? 'h-full' : 'h-[calc(100vh-4rem)]'} p-4 overflow-y-auto`}>
      {/* Mobile Drawer Header */}
      {isMobile && (
        <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <CalendarIcon className="w-4 h-4 text-brand-600 dark:text-brand-400" />
            <span className="text-sm font-bold text-slate-800 dark:text-slate-100">Calendars & Filters</span>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
            aria-label="Close Sidebar"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Primary Action Button */}
      <button
        onClick={onOpenCreateEvent}
        className="w-full py-2.5 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-xl font-semibold text-sm shadow-md shadow-brand-500/20 flex items-center justify-center gap-2 transition-all hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
      >
        <Plus className="w-4 h-4 stroke-[2.5]" />
        <span>Create Event</span>
      </button>

      {/* Mini Month Navigator */}
      <div className="mt-6 select-none">
        <div className="flex items-center justify-between mb-3 px-1">
          <span className="text-xs font-bold text-slate-700 dark:text-slate-200">
            {format(miniDate, 'MMMM yyyy')}
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setMiniDate(subMonths(miniDate, 1))}
              className="p-1 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded cursor-pointer"
              title="Previous month"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setMiniDate(addMonths(miniDate, 1))}
              className="p-1 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded cursor-pointer"
              title="Next month"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Days of week header */}
        <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-semibold text-slate-400 dark:text-slate-500 mb-1">
          <span>Su</span>
          <span>Mo</span>
          <span>Tu</span>
          <span>We</span>
          <span>Th</span>
          <span>Fr</span>
          <span>Sa</span>
        </div>

        {/* Mini Calendar Grid */}
        <div className="grid grid-cols-7 gap-1 text-center">
          {days.map((day, idx) => {
            const isSelected = isSameDay(day, selectedDate);
            const isCurrentMonth = isSameMonth(day, miniDate);
            const isCurrentDay = isToday(day);

            return (
              <button
                key={idx}
                onClick={() => onSelectDate && onSelectDate(day)}
                className={`h-7 w-7 text-xs rounded-full flex items-center justify-center transition-colors mx-auto cursor-pointer ${
                  !isCurrentMonth
                    ? 'text-slate-300 dark:text-slate-600'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                } ${isCurrentDay && !isSelected ? 'font-bold text-brand-600 dark:text-brand-400 border border-brand-300 dark:border-brand-700' : ''} ${
                  isSelected ? 'bg-brand-600 text-white font-semibold' : ''
                }`}
              >
                {format(day, 'd')}
              </button>
            );
          })}
        </div>
      </div>

      <div className="my-5 border-t border-slate-100 dark:border-slate-800" />

      {/* My Calendars Section */}
      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
            My Calendars
          </span>
          <button
            onClick={onOpenCreateCalendar}
            className="p-1 text-slate-400 hover:text-brand-600 dark:hover:text-brand-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-colors cursor-pointer"
            title="Create calendar"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
        </div>

        <div className="space-y-1">
          {myCalendars.map((cal) => {
            const isChecked = selectedCalendarIds.includes(cal.id);
            return (
              <div
                key={cal.id}
                className="group flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
              >
                <div
                  onClick={() => onToggleCalendar && onToggleCalendar(cal.id)}
                  className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer select-none"
                >
                  <div
                    className="w-4 h-4 rounded border flex items-center justify-center transition-colors shrink-0"
                    style={{
                      backgroundColor: isChecked ? cal.color || '#4f46e5' : 'transparent',
                      borderColor: cal.color || '#4f46e5',
                    }}
                  >
                    {isChecked && <Check className="w-3 h-3 text-white stroke-[3]" />}
                  </div>
                  <span className="text-xs font-medium text-slate-700 dark:text-slate-200 truncate">
                    {cal.name}
                  </span>
                  {cal.isDefault && (
                    <span className="px-1.5 py-0.2 bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 text-[9px] rounded font-medium">
                      Default
                    </span>
                  )}
                </div>

                <button
                  onClick={() => onOpenShareCalendar && onOpenShareCalendar(cal)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded transition-opacity cursor-pointer"
                  title="Share calendar"
                >
                  <Share2 className="w-3 h-3" />
                </button>
              </div>
            );
          })}
        </div>
      </div>

      {/* Shared With Me Section */}
      {sharedCalendars.length > 0 && (
        <div className="mt-5 space-y-2">
          <div className="flex items-center justify-between px-1">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              Shared with me
            </span>
          </div>

          <div className="space-y-1">
            {sharedCalendars.map((cal) => {
              const isChecked = selectedCalendarIds.includes(cal.id);
              return (
                <div
                  key={cal.id}
                  className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
                >
                  <div
                    onClick={() => onToggleCalendar && onToggleCalendar(cal.id)}
                    className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer select-none"
                  >
                    <div
                      className="w-4 h-4 rounded border flex items-center justify-center transition-colors shrink-0"
                      style={{
                        backgroundColor: isChecked ? cal.color || '#10b981' : 'transparent',
                        borderColor: cal.color || '#10b981',
                      }}
                    >
                      {isChecked && <Check className="w-3 h-3 text-white stroke-[3]" />}
                    </div>
                    <span className="text-xs font-medium text-slate-700 dark:text-slate-200 truncate">
                      {cal.name}
                    </span>
                    <span className="px-1.5 py-0.2 bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 text-[9px] rounded font-medium uppercase">
                      {cal.permission || 'VIEW'}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </aside>
  );
}
