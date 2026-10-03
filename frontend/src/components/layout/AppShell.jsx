import React, { useState } from 'react';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import { Plus } from 'lucide-react';

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
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 dark:bg-slate-950">
      <Navbar
        onOpenSearch={onOpenSearch}
        onToggleMobileSidebar={() => setMobileSidebarOpen(!mobileSidebarOpen)}
      />

      <div className="flex-1 flex overflow-hidden relative">
        {/* Desktop static sidebar */}
        <div className="hidden md:flex">
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
        </div>

        {/* Mobile slide-over drawer */}
        {mobileSidebarOpen && (
          <div className="fixed inset-0 z-40 md:hidden animate-in fade-in duration-150">
            {/* Backdrop */}
            <div
              className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs transition-opacity"
              onClick={() => setMobileSidebarOpen(false)}
            />

            {/* Drawer Container */}
            <div className="fixed inset-y-0 left-0 max-w-xs w-full bg-white dark:bg-slate-900 shadow-2xl z-50 flex flex-col animate-in slide-in-from-left duration-200">
              <Sidebar
                calendars={calendars}
                selectedCalendarIds={selectedCalendarIds}
                onToggleCalendar={onToggleCalendar}
                onOpenCreateEvent={() => {
                  setMobileSidebarOpen(false);
                  onOpenCreateEvent();
                }}
                onOpenCreateCalendar={() => {
                  setMobileSidebarOpen(false);
                  onOpenCreateCalendar();
                }}
                onOpenShareCalendar={(cal) => {
                  setMobileSidebarOpen(false);
                  onOpenShareCalendar(cal);
                }}
                selectedDate={selectedDate}
                onSelectDate={(date) => {
                  setMobileSidebarOpen(false);
                  onSelectDate(date);
                }}
                onClose={() => setMobileSidebarOpen(false)}
                isMobile={true}
              />
            </div>
          </div>
        )}

        <main className="flex-1 overflow-y-auto bg-white dark:bg-slate-900 p-2 sm:p-6 min-w-0">
          {children}
        </main>
      </div>

      {/* Mobile Floating Action Button (FAB) for 1-tap event creation */}
      <button
        onClick={onOpenCreateEvent}
        className="md:hidden fixed bottom-5 right-5 z-30 w-14 h-14 bg-gradient-to-tr from-brand-600 to-indigo-600 text-white rounded-full shadow-lg shadow-brand-500/40 flex items-center justify-center active:scale-95 transition-transform cursor-pointer"
        aria-label="Create Event"
        title="Create Event"
      >
        <Plus className="w-7 h-7 stroke-[2.5]" />
      </button>
    </div>
  );
}
