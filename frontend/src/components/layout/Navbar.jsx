import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useTheme } from '../../context/ThemeContext';
import { api } from '../../api/client';
import { Calendar, Bell, Search, Globe, LogOut, Check, Trash2, Clock, ChevronDown, Key, Menu, Sun, Moon, X } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import SettingsModal from '../settings/SettingsModal';

export default function Navbar({ onOpenSearch, onViewChange, currentView = 'timeGridWeek', onToggleMobileSidebar }) {
  const { user, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [floatingToasts, setFloatingToasts] = useState([]);
  const seenNotificationIdsRef = useRef(new Set());
  const isInitialMountRef = useRef(true);
  const notifRef = useRef(null);
  const profileRef = useRef(null);

  const isNotificationRead = (n) => Boolean(n.read || n.isRead);

  // Trigger top-right floating toast (auto-dismiss in 3.5s)
  const triggerFloatingToast = (notif) => {
    const toastId = `${notif.id}-${Date.now()}`;
    const newToast = {
      id: toastId,
      notificationId: notif.id,
      title: notif.title || 'Reminder Alert',
      message: notif.message,
      eventTitle: notif.eventTitle,
      createdAt: notif.createdAt,
    };

    setFloatingToasts((prev) => [newToast, ...prev].slice(0, 3));

    // Auto-dismiss in 3.5 seconds (between 3-4s as requested)
    setTimeout(() => {
      setFloatingToasts((prev) => prev.filter((t) => t.id !== toastId));
    }, 3500);
  };

  const dismissFloatingToast = (toastId) => {
    setFloatingToasts((prev) => prev.filter((t) => t.id !== toastId));
  };

  const handleClickFloatingToast = () => {
    setShowNotifications(true);
  };

  // Poll unread count every 10 seconds & check for newly arrived notifications
  useEffect(() => {
    let isMounted = true;

    const parseCount = (res) => {
      if (typeof res === 'number') return res;
      if (res && typeof res.unreadCount === 'number') return res.unreadCount;
      return 0;
    };

    const pollNotifications = async () => {
      try {
        const countRes = await api.notifications.getUnreadCount();
        const count = parseCount(countRes);
        if (isMounted) setUnreadCount(count);

        // Fetch notifications to detect newly arrived items
        const items = await api.notifications.list(false);
        if (!isMounted || !Array.isArray(items)) return;

        setNotifications(items);

        if (isInitialMountRef.current) {
          // On first page load, register existing IDs so we don't spam toasts for old notifications
          items.forEach((item) => seenNotificationIdsRef.current.add(item.id));
          isInitialMountRef.current = false;
        } else {
          // On subsequent polls, find unread notifications that haven't been shown yet
          const newItems = items.filter(
            (item) => !isNotificationRead(item) && !seenNotificationIdsRef.current.has(item.id)
          );

          if (newItems.length > 0) {
            newItems.forEach((item) => {
              seenNotificationIdsRef.current.add(item.id);
              triggerFloatingToast(item);
            });
          }
        }
      } catch (err) {
        // Silently tolerate background poll errors
      }
    };

    pollNotifications();
    const interval = setInterval(pollNotifications, 10000);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        pollNotifications();
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleVisibilityChange);

    return () => {
      isMounted = false;
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleVisibilityChange);
    };
  }, []);

  // Fetch notifications when dropdown opens
  const handleToggleNotifications = async () => {
    const nextState = !showNotifications;
    setShowNotifications(nextState);
    if (nextState) {
      try {
        const items = await api.notifications.list(false);
        setNotifications(items);
        const unreadItems = items.filter((n) => !isNotificationRead(n));
        setUnreadCount(unreadItems.length);
      } catch (err) {
        // Silently tolerate
      }
    }
  };

  const handleMarkAsRead = async (id, e) => {
    e.stopPropagation();
    try {
      await api.notifications.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true, isRead: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {}
  };

  const handleMarkAllRead = async () => {
    try {
      await api.notifications.markAllAsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true, isRead: true })));
      setUnreadCount(0);
    } catch (err) {}
  };

  const handleDeleteNotif = async (id, e) => {
    e.stopPropagation();
    try {
      await api.notifications.delete(id);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {}
  };

  // Close menus on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setShowNotifications(false);
      }
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setShowProfileMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <header className="h-16 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-3 sm:px-6 flex items-center justify-between z-30 sticky top-0 transition-colors">
      {/* Brand & Mobile Hamburger */}
      <div className="flex items-center gap-2 sm:gap-3">
        {/* Mobile Hamburger Menu Button */}
        <button
          onClick={onToggleMobileSidebar}
          className="md:hidden p-1.5 -ml-1 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
          aria-label="Toggle Navigation & Calendars"
          title="Open Calendars"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="w-8 h-8 sm:w-9 sm:h-9 bg-gradient-to-tr from-brand-600 to-indigo-500 rounded-lg flex items-center justify-center shadow-md shadow-brand-500/15 shrink-0">
          <Calendar className="w-4 h-4 sm:w-5 sm:h-5 text-white" />
        </div>
        <div>
          <span className="text-lg sm:text-xl font-bold tracking-tight bg-gradient-to-r from-slate-900 to-slate-700 dark:from-white dark:to-slate-300 bg-clip-text text-transparent">
            Zoner
          </span>
          <span className="hidden sm:inline-block ml-2 text-xs font-medium text-slate-400 dark:text-slate-500">
            Smart Calendar
          </span>
        </div>
      </div>

      {/* Center Search Trigger (Desktop) */}
      <div className="flex-1 max-w-md mx-4 hidden md:block">
        <button
          onClick={onOpenSearch}
          className="w-full flex items-center justify-between px-3.5 py-1.5 bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 rounded-lg text-sm text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <Search className="w-4 h-4" />
            <span>Search events, descriptions, attendees...</span>
          </div>
          <kbd className="px-1.5 py-0.5 text-[10px] font-semibold bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded shadow-xs text-slate-500 dark:text-slate-400">
            ⌘K
          </kbd>
        </button>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-1.5 sm:gap-3">
        {/* Mobile Search Button */}
        <button
          onClick={onOpenSearch}
          className="md:hidden p-2 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
          aria-label="Search"
          title="Search events"
        >
          <Search className="w-5 h-5" />
        </button>

        {/* Time Zone Indicator */}
        <div className="hidden lg:flex items-center gap-1.5 px-2.5 py-1 bg-slate-50 dark:bg-slate-800 border border-slate-200/80 dark:border-slate-700 rounded-full text-xs font-medium text-slate-600 dark:text-slate-300">
          <Globe className="w-3.5 h-3.5 text-brand-600 dark:text-brand-400" />
          <span>{user?.timeZone || 'UTC'}</span>
        </div>

        {/* Dark Mode Toggle */}
        <button
          onClick={toggleTheme}
          className="p-2 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
          aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {isDark ? <Sun className="w-5 h-5 text-amber-400" /> : <Moon className="w-5 h-5 text-slate-500" />}
        </button>

        {/* Notifications Bell */}
        <div className="relative" ref={notifRef}>
          <button
            onClick={handleToggleNotifications}
            className="relative p-2 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
            aria-label="Notifications"
          >
            <Bell className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 flex h-2.5 w-2.5 pointer-events-none">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500 ring-2 ring-white dark:ring-slate-900 shadow-xs shadow-emerald-500/50"></span>
              </span>
            )}
          </button>

          {/* Notifications Dropdown */}
          {showNotifications && (
            <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white dark:bg-slate-900 rounded-xl shadow-2xl border border-slate-100 dark:border-slate-800 py-2 z-50 animate-in fade-in zoom-in-95 duration-100">
              <div className="px-4 py-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-sm text-slate-800 dark:text-slate-100">Notifications</h3>
                  {unreadCount > 0 && (
                    <span className="px-2 py-0.5 bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 text-xs font-semibold rounded-full flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      {unreadCount} new
                    </span>
                  )}
                </div>
                {unreadCount > 0 && (
                  <button
                    onClick={handleMarkAllRead}
                    className="text-xs font-medium text-brand-600 dark:text-brand-400 hover:text-brand-700 dark:hover:text-brand-300 cursor-pointer"
                  >
                    Mark all read
                  </button>
                )}
              </div>

              <div className="max-h-80 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
                {notifications.length === 0 ? (
                  <div className="py-8 text-center text-sm text-slate-400 dark:text-slate-500">
                    <Clock className="w-8 h-8 mx-auto mb-2 text-slate-300 dark:text-slate-600" />
                    No notifications right now
                  </div>
                ) : (
                  notifications.map((n) => {
                    const isUnread = !isNotificationRead(n);
                    return (
                      <div
                        key={n.id}
                        className={`p-3.5 hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors flex items-start justify-between gap-3 ${
                          isUnread ? 'bg-brand-50/30 dark:bg-brand-950/30' : ''
                        }`}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <p className="text-xs font-semibold text-slate-800 dark:text-slate-200 truncate">
                              {n.title}
                            </p>
                            {isUnread && (
                              <span className="w-1.5 h-1.5 bg-brand-600 dark:bg-brand-400 rounded-full" />
                            )}
                          </div>
                          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 line-clamp-2">
                            {n.message}
                          </p>
                          <p className="text-[10px] text-slate-400 dark:text-slate-500 mt-1">
                            {n.createdAt ? `${formatDistanceToNow(new Date(n.createdAt))} ago` : 'Just now'}
                          </p>
                        </div>
                        <div className="flex items-center gap-1">
                          {isUnread && (
                            <button
                              onClick={(e) => handleMarkAsRead(n.id, e)}
                              title="Mark as read"
                              className="p-1 text-slate-400 hover:text-brand-600 dark:hover:text-brand-400 rounded cursor-pointer"
                            >
                              <Check className="w-3.5 h-3.5" />
                            </button>
                          )}
                          <button
                            onClick={(e) => handleDeleteNotif(n.id, e)}
                            title="Dismiss"
                            className="p-1 text-slate-400 hover:text-rose-600 dark:hover:text-rose-400 rounded cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          )}
        </div>

        {/* User Profile Menu */}
        <div className="relative" ref={profileRef}>
          <button
            onClick={() => setShowProfileMenu(!showProfileMenu)}
            className="flex items-center gap-2 p-1.5 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-brand-600 to-indigo-500 text-white font-semibold text-xs flex items-center justify-center shadow-xs">
              {user?.displayName ? user.displayName.charAt(0).toUpperCase() : 'U'}
            </div>
            <span className="hidden sm:inline-block text-xs font-semibold text-slate-700 max-w-[120px] truncate">
              {user?.displayName || 'User'}
            </span>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
          </button>

          {showProfileMenu && (
            <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-xl border border-slate-100 py-1.5 z-50 animate-in fade-in zoom-in-95 duration-100">
              <div className="px-4 py-2 border-b border-slate-100">
                <p className="text-xs font-semibold text-slate-800 truncate">{user?.displayName}</p>
                <p className="text-[11px] text-slate-400 truncate">{user?.email}</p>
                <div className="mt-1 text-[10px] text-brand-600 flex items-center gap-1 font-medium">
                  <Globe className="w-3 h-3" />
                  <span>{user?.timeZone}</span>
                </div>
              </div>

              <div className="py-1 border-b border-slate-100">
                <button
                  onClick={() => {
                    setShowProfileMenu(false);
                    setShowSettingsModal(true);
                  }}
                  className="w-full px-4 py-2 text-left text-xs font-medium text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors cursor-pointer"
                >
                  <Key className="w-3.5 h-3.5 text-brand-600" />
                  <span>Settings & MCP</span>
                </button>
              </div>

              <div className="pt-1">
                <button
                  onClick={logout}
                  className="w-full px-4 py-2 text-left text-xs font-medium text-rose-600 hover:bg-rose-50 flex items-center gap-2 transition-colors cursor-pointer"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Sign out</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Settings & MCP Modal */}
      <SettingsModal
        isOpen={showSettingsModal}
        onClose={() => setShowSettingsModal(false)}
      />

      {/* Floating Toast Notification Container (Top Right, auto-dismiss 3-4s) */}
      <div className="fixed top-18 sm:top-20 right-4 sm:right-6 z-50 flex flex-col gap-2.5 max-w-sm w-full pointer-events-none">
        {floatingToasts.map((t) => (
          <div
            key={t.id}
            className="pointer-events-auto bg-white/95 dark:bg-slate-900/95 backdrop-blur-md border border-emerald-500/40 dark:border-emerald-500/50 rounded-2xl p-4 shadow-2xl shadow-emerald-950/15 dark:shadow-black/60 flex items-start gap-3.5 animate-in slide-in-from-top-3 duration-200 transition-all hover:scale-[1.01]"
          >
            <div className="w-9 h-9 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 text-emerald-600 dark:text-emerald-400 flex items-center justify-center shrink-0 shadow-xs">
              <Bell className="w-4 h-4 animate-bounce" />
            </div>
            <div
              className="flex-1 min-w-0 cursor-pointer"
              onClick={() => handleClickFloatingToast(t)}
            >
              <div className="flex items-center justify-between gap-1.5 mb-1">
                <span className="text-xs font-bold text-slate-900 dark:text-slate-100 truncate">
                  {t.title}
                </span>
                <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-emerald-600 dark:text-emerald-400 px-1.5 py-0.5 bg-emerald-50 dark:bg-emerald-950/60 rounded-full shrink-0">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  New
                </span>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-300 leading-snug line-clamp-2">
                {t.message}
              </p>
            </div>
            <button
              onClick={() => dismissFloatingToast(t.id)}
              className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 -mr-1 -mt-1 rounded-lg transition-colors cursor-pointer"
              aria-label="Dismiss notification"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>
    </header>
  );
}
