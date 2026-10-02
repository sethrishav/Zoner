import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../../api/client';
import { getErrorMessage } from '../../api/errors';
import { useToast } from '../../context/ToastContext';
import { X, Users, Share2, Mail, Shield, Trash2, Plus, Check } from 'lucide-react';

export default function ShareCalendarModal({ isOpen, calendar, onClose }) {
  const toast = useToast();
  const [shares, setShares] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [email, setEmail] = useState('');
  const [permission, setPermission] = useState('VIEW');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadShares = useCallback(async () => {
    if (!calendar?.id) return;
    try {
      setIsLoading(true);
      const data = await api.calendars.listShares(calendar.id);
      setShares(data);
    } catch (err) {
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, [calendar?.id]);

  useEffect(() => {
    if (isOpen) {
      loadShares();
      setEmail('');
      setError('');
    }
  }, [isOpen, loadShares]);

  if (!isOpen || !calendar) return null;

  const handleShare = async (e) => {
    e.preventDefault();
    if (!email.trim()) return;

    try {
      setIsSubmitting(true);
      setError('');
      await api.calendars.share(calendar.id, {
        email: email.trim().toLowerCase(),
        permission,
      });
      toast.success(`Calendar shared with ${email.trim()}`);
      setEmail('');
      loadShares();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRemoveShare = async (userId, targetEmail) => {
    try {
      await api.calendars.removeShare(calendar.id, userId);
      toast.success(`Removed share with ${targetEmail}`);
      setShares((prev) => prev.filter((s) => s.userId !== userId));
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-in fade-in duration-100">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full border border-slate-100 p-6">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-100">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Share2 className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-semibold text-base text-slate-800">
                Share "{calendar.name}"
              </h3>
              <p className="text-[11px] text-slate-400">
                Manage who can view or edit events on this calendar.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {error && (
          <div className="mt-4 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800">
            {error}
          </div>
        )}

        {/* Add share form */}
        <form onSubmit={handleShare} className="mt-4 space-y-3">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
                <Mail className="w-4 h-4" />
              </div>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="User's email (e.g. colleague@work.com)"
                className="w-full pl-9 pr-3 py-2 text-xs rounded-lg border border-slate-200 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
            </div>

            <select
              value={permission}
              onChange={(e) => setPermission(e.target.value)}
              className="text-xs rounded-lg border border-slate-200 bg-white py-2 px-2.5 text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-500"
            >
              <option value="VIEW">Can view</option>
              <option value="EDIT">Can edit</option>
            </select>

            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-semibold text-white bg-brand-600 hover:bg-brand-700 rounded-lg shadow-sm disabled:opacity-60 transition-colors flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Share</span>
            </button>
          </div>
        </form>

        {/* Existing shares list */}
        <div className="mt-6">
          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
            People with access
          </h4>

          {isLoading ? (
            <div className="py-6 text-center text-xs text-slate-400">Loading access list...</div>
          ) : shares.length === 0 ? (
            <div className="py-6 text-center text-xs text-slate-400 bg-slate-50/60 rounded-xl border border-dashed border-slate-200">
              This calendar is currently private. Only you can access it.
            </div>
          ) : (
            <div className="divide-y divide-slate-100 max-h-48 overflow-y-auto">
              {shares.map((s) => (
                <div key={s.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className="w-7 h-7 rounded-full bg-slate-100 text-slate-600 font-semibold text-xs flex items-center justify-center">
                      {s.email.charAt(0).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-slate-800 truncate">{s.email}</p>
                      <p className="text-[10px] text-slate-400">
                        {s.permission === 'EDIT' ? 'Full edit access' : 'View only access'}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-600 uppercase">
                      {s.permission}
                    </span>
                    <button
                      onClick={() => handleRemoveShare(s.userId, s.email)}
                      className="p-1 text-slate-400 hover:text-rose-600 rounded transition-colors"
                      title="Remove access"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="mt-6 pt-3 border-t border-slate-100 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}
