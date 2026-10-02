import React, { useState, useEffect } from 'react';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import { api } from '../../api/client';

export default function AppShell({
  children,
  onOpenCreateEvent,
  onOpenCreateCalendar,
  onOpenShareCalendar,
  onOpenSearch,
  calendars,
  selectedCalendarIds,
  onToggleCalendar,
  selectedDate,
  onSelectDate,
}) {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar onOpenSearch={onOpenSearch} />
      <div className="flex-1 flex overflow-hidden">
        <Sidebar
          calendars={calendars}
          selectedCalendarIds={selectedCalendarIds}
          onToggleCalendar={onToggleCalendar}
          onOpenCreateEvent={onOpenCreateEvent}
          onOpenCreateCalendar={onOpenCreateCalendar}
          onOpenShareCalendar={onOpenShareCalendar}
          selectedDate={selectedDate}
          onSelectDate={onSelectDate}
        />
        <main className="flex-1 overflow-y-auto bg-white p-4 sm:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
