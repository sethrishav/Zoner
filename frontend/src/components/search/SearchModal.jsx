import React, { useState, useEffect } from 'react';
import { api } from '../../api/client';
import { Search, X, Calendar, Clock, MapPin, ArrowRight } from 'lucide-react';
import { format } from 'date-fns';

export default function SearchModal({
  isOpen,
  onClose,
  onSelectEvent,
  calendars = [],
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);

  // Debounced search
  useEffect(() => {
    if (!isOpen) return;

    if (!query.trim()) {
      setResults([]);
      setIsSearching(false);
      return;
    }

    let isMounted = true;
    setIsSearching(true);

    const timer = setTimeout(async () => {
      try {
        const data = await api.events.search(query.trim());
        if (isMounted) setResults(data);
      } catch (err) {
        if (isMounted) setResults([]);
      } finally {
        if (isMounted) setIsSearching(false);
      }
    }, 300);

    return () => {
      isMounted = false;
      clearTimeout(timer);
    };
  }, [query, isOpen]);

  // Keyboard shortcut listener for Escape
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-6 sm:pt-20 p-3 sm:p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-xl w-full border border-slate-100 overflow-hidden flex flex-col max-h-[85vh]">
        {/* Search Input Bar */}
        <div className="p-4 border-b border-slate-100 flex items-center gap-3">
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search across all accessible events, notes, locations..."
            className="flex-1 text-sm text-slate-900 placeholder-slate-400 border-0 focus:ring-0 outline-none"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="p-1 text-slate-400 hover:text-slate-600 rounded"
            >
              <X className="w-4 h-4" />
            </button>
          )}
          <kbd className="px-1.5 py-0.5 text-[10px] font-semibold bg-slate-100 border border-slate-200 rounded text-slate-500">
            ESC
          </kbd>
        </div>

        {/* Search Results List */}
        <div className="overflow-y-auto p-2 flex-1">
          {isSearching ? (
            <div className="py-12 text-center text-xs text-slate-400">
              Searching your calendars...
            </div>
          ) : query && results.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-400">
              No matching events found for "{query}"
            </div>
          ) : !query ? (
            <div className="py-10 text-center text-xs text-slate-400">
              Type to search by event title, location, description, or attendee...
            </div>
          ) : (
            <div className="space-y-1">
              {results.map((item) => {
                const cal = calendars.find((c) => c.id === item.calendarId);
                const startDate = new Date(item.startAt);

                return (
                  <button
                    key={item.id}
                    onClick={() => {
                      onSelectEvent(item);
                      onClose();
                    }}
                    className="w-full text-left p-3 rounded-xl hover:bg-slate-50 flex items-center justify-between gap-3 transition-colors group"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span
                          className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                          style={{ backgroundColor: item.color || cal?.color || '#4f46e5' }}
                        />
                        <p className="text-xs font-semibold text-slate-800 truncate">
                          {item.title}
                        </p>
                        <span className="text-[10px] text-slate-400 font-medium">
                          {cal?.name}
                        </span>
                      </div>

                      <div className="mt-1 flex items-center gap-3 text-[11px] text-slate-500">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-400" />
                          {format(startDate, 'MMM d, yyyy · h:mm a')}
                        </span>
                        {item.location && (
                          <span className="flex items-center gap-1 truncate max-w-[150px]">
                            <MapPin className="w-3 h-3 text-slate-400" />
                            {item.location}
                          </span>
                        )}
                      </div>

                      {item.description && (
                        <p className="mt-1 text-[11px] text-slate-400 line-clamp-1">
                          {item.description}
                        </p>
                      )}
                    </div>

                    <ArrowRight className="w-4 h-4 text-slate-300 group-hover:text-brand-600 group-hover:translate-x-0.5 transition-all flex-shrink-0" />
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
