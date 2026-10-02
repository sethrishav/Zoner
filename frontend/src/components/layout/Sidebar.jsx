import React, { useState } from 'react';
import { Plus, Calendar as CalendarIcon, Users, Check, ChevronLeft, ChevronRight, Share2, Settings } from 'lucide-react';
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
}) {
  const [miniDate, setMiniDate] = useState(selectedDate);

  // Group calendars into owned and shared
  const myCalendars = calendars.filter((c) => c.isOwner);
  const sharedCalendars = calendars.filter((c) => !c.isOwner);

  // Mini-calendar generation
  const monthStart = startOfMonth(miniDate);
  const monthEnd = endOfMonth(monthStart);
  const startDate = startOfWeek(monthStart);
  const endDate = endOfWeek(monthEnd);
  const days = eachDayOfInterval({ start: startDate, end: endDate });

  return (
    <aside className="w-64 bg-white border-r border-slate-200 flex flex-col h-[calc(100vh-4rem)] p-4 overflow-y-auto">
      {/* Primary Action Button */}
      <button
        onClick={onOpenCreateEvent}
        className="w-full py-2.5 px-4 bg-brand-600 hover:bg-brand-700 text-white rounded-xl font-semibold text-sm shadow-md shadow-brand-500/20 flex items-center justify-center gap-2 transition-all hover:scale-[1.02] active:scale-[0.98]"
      >
        <Plus className="w-4 h-4 stroke-[2.5]" />
        <span>Create Event</span>
      </button>

      {/* Mini Month Navigator */}
      <div className="mt-6 select-none">
        <div className="flex items-center justify-between mb-3 px-1">
          <span className="text-xs font-bold text-slate-700">
            {format(miniDate, 'MMMM yyyy')}
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setMiniDate(subMonths(miniDate, 1))}
              className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded"
              title="Previous month"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setMiniDate(addMonths(miniDate, 1))}
              className="p-1 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded"
              title="Next month"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Days of week header */}
        <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-semibold text-slate-400 mb-1">
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
                className={`h-7 w-7 text-xs rounded-full flex items-center justify-center transition-colors mx-auto ${
                  !isCurrentMonth ? 'text-slate-300' : 'text-slate-700 hover:bg-slate-100'
                } ${isCurrentDay && !isSelected ? 'font-bold text-brand-600 border border-brand-300' : ''} ${
                  isSelected ? 'bg-brand-600 text-white font-semibold' : ''
                }`}
              >
                {format(day, 'd')}
              </button>
            );
          })}
        </div>
      </div>

      <div className="my-5 border-t border-slate-100" />

      {/* My Calendars Section */}
      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            My Calendars
          </span>
          <button
            onClick={onOpenCreateCalendar}
            className="p-1 text-slate-400 hover:text-brand-600 hover:bg-slate-100 rounded transition-colors"
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
                className="group flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-50 transition-colors"
              >
                <label className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer">
                  <div
                    onClick={() => onToggleCalendar && onToggleCalendar(cal.id)}
                    className="w-4 h-4 rounded border flex items-center justify-center transition-colors"
                    style={{
                      backgroundColor: isChecked ? cal.color || '#4f46e5' : 'transparent',
                      borderColor: cal.color || '#4f46e5',
                    }}
                  >
                    {isChecked && <Check className="w-3 h-3 text-white stroke-[3]" />}
                  </div>
                  <span className="text-xs font-medium text-slate-700 truncate">
                    {cal.name}
                  </span>
                  {cal.isDefault && (
                    <span className="px-1.5 py-0.2 bg-slate-100 text-slate-500 text-[9px] rounded font-medium">
                      Default
                    </span>
                  )}
                </label>

                <button
                  onClick={() => onOpenShareCalendar && onOpenShareCalendar(cal)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-slate-600 rounded transition-opacity"
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
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Shared with me
            </span>
          </div>

          <div className="space-y-1">
            {sharedCalendars.map((cal) => {
              const isChecked = selectedCalendarIds.includes(cal.id);
              return (
                <div
                  key={cal.id}
                  className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                >
                  <label className="flex items-center gap-2.5 flex-1 min-w-0 cursor-pointer">
                    <div
                      onClick={() => onToggleCalendar && onToggleCalendar(cal.id)}
                      className="w-4 h-4 rounded border flex items-center justify-center transition-colors"
                      style={{
                        backgroundColor: isChecked ? cal.color || '#10b981' : 'transparent',
                        borderColor: cal.color || '#10b981',
                      }}
                    >
                      {isChecked && <Check className="w-3 h-3 text-white stroke-[3]" />}
                    </div>
                    <span className="text-xs font-medium text-slate-700 truncate">
                      {cal.name}
                    </span>
                    <span className="px-1.5 py-0.2 bg-emerald-50 text-emerald-600 text-[9px] rounded font-medium uppercase">
                      {cal.permission || 'VIEW'}
                    </span>
                  </label>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </aside>
  );
}
