import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type }]);

    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
  }, []);

  const removeToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = {
    success: (msg, duration) => addToast(msg, 'success', duration),
    error: (msg, duration) => addToast(msg, 'error', duration),
    info: (msg, duration) => addToast(msg, 'info', duration),
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      {/* Toast container */}
      <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none">
        {toasts.map((t) => {
          let bg = 'bg-slate-900 text-white';
          let Icon = Info;
          let iconColor = 'text-brand-400';

          if (t.type === 'success') {
            bg = 'bg-emerald-900/95 text-emerald-50 border border-emerald-700/60 shadow-lg shadow-emerald-950/20';
            Icon = CheckCircle2;
            iconColor = 'text-emerald-400';
          } else if (t.type === 'error') {
            bg = 'bg-rose-950/95 text-rose-50 border border-rose-800/60 shadow-lg shadow-rose-950/20';
            Icon = AlertCircle;
            iconColor = 'text-rose-400';
          }

          return (
            <div
              key={t.id}
              className={`pointer-events-auto p-3.5 rounded-xl flex items-start gap-3 backdrop-blur-md shadow-xl animate-in slide-in-from-bottom-2 duration-150 ${bg}`}
            >
              <Icon className={`w-5 h-5 flex-shrink-0 mt-0.5 ${iconColor}`} />
              <div className="text-xs font-medium flex-1 leading-relaxed">{t.message}</div>
              <button
                onClick={() => removeToast(t.id)}
                className="text-slate-400 hover:text-white p-0.5 rounded transition-colors"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
