import React, { useState } from 'react';
import { X, Calendar, RefreshCw } from 'lucide-react';

export default function RecurringChoiceModal({
  isOpen,
  actionType = 'edit', // 'edit' or 'delete'
  eventTitle,
  onConfirm,
  onClose,
}) {
  const [selectedMode, setSelectedMode] = useState('THIS');

  if (!isOpen) return null;

  const isDelete = actionType === 'delete';

  const handleSubmit = (e) => {
    e.preventDefault();
    onConfirm(selectedMode);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full border border-slate-100 p-6">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-brand-50 text-brand-600 flex items-center justify-center">
              <RefreshCw className="w-4 h-4" />
            </div>
            <h3 className="font-semibold text-base text-slate-800">
              {isDelete ? 'Delete Recurring Event' : 'Edit Recurring Event'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <p className="mt-3 text-xs text-slate-500 leading-relaxed">
          <strong className="text-slate-700 font-medium">"{eventTitle}"</strong> is a repeating event. How would you like to apply your changes?
        </p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-2.5">
          <label
            className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
              selectedMode === 'THIS'
                ? 'border-brand-500 bg-brand-50/30 ring-1 ring-brand-500'
                : 'border-slate-200 hover:bg-slate-50'
            }`}
          >
            <input
              type="radio"
              name="recurrence_mode"
              value="THIS"
              checked={selectedMode === 'THIS'}
              onChange={() => setSelectedMode('THIS')}
              className="mt-0.5 text-brand-600 focus:ring-brand-500"
            />
            <div>
              <p className="text-xs font-semibold text-slate-800">This event only</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {isDelete
                  ? 'Cancel only this occurrence. Other occurrences remain untouched.'
                  : 'Change only this occurrence without altering the rest of the series.'}
              </p>
            </div>
          </label>

          <label
            className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
              selectedMode === 'THIS_AND_FOLLOWING'
                ? 'border-brand-500 bg-brand-50/30 ring-1 ring-brand-500'
                : 'border-slate-200 hover:bg-slate-50'
            }`}
          >
            <input
              type="radio"
              name="recurrence_mode"
              value="THIS_AND_FOLLOWING"
              checked={selectedMode === 'THIS_AND_FOLLOWING'}
              onChange={() => setSelectedMode('THIS_AND_FOLLOWING')}
              className="mt-0.5 text-brand-600 focus:ring-brand-500"
            />
            <div>
              <p className="text-xs font-semibold text-slate-800">This and following events</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {isDelete
                  ? 'Delete this occurrence and all future occurrences in the series.'
                  : 'Splits the series. Changes apply to this occurrence and all future ones.'}
              </p>
            </div>
          </label>

          <label
            className={`flex items-start gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
              selectedMode === 'ALL'
                ? 'border-brand-500 bg-brand-50/30 ring-1 ring-brand-500'
                : 'border-slate-200 hover:bg-slate-50'
            }`}
          >
            <input
              type="radio"
              name="recurrence_mode"
              value="ALL"
              checked={selectedMode === 'ALL'}
              onChange={() => setSelectedMode('ALL')}
              className="mt-0.5 text-brand-600 focus:ring-brand-500"
            />
            <div>
              <p className="text-xs font-semibold text-slate-800">All events in series</p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {isDelete
                  ? 'Delete the entire series across past and future.'
                  : 'Update the main recurring event across all past and future occurrences.'}
              </p>
            </div>
          </label>

          <div className="mt-6 flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              className={`px-4 py-2 text-xs font-semibold text-white rounded-lg shadow-sm transition-colors ${
                isDelete
                  ? 'bg-rose-600 hover:bg-rose-700'
                  : 'bg-brand-600 hover:bg-brand-700'
              }`}
            >
              {isDelete ? 'Confirm Delete' : 'Continue Editing'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
