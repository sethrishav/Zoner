import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { api, tokenStorage } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // Initialize auth state from stored tokens
  const initAuth = useCallback(async () => {
    const accessToken = tokenStorage.getAccessToken();
    const refreshToken = tokenStorage.getRefreshToken();

    if (!accessToken && !refreshToken) {
      setIsLoading(false);
      return;
    }

    try {
      // Attempt to load current user profile
      const userProfile = await api.user.getMe();
      setUser(userProfile);
    } catch (err) {
      // If profile fetch fails, try refreshing token
      if (refreshToken) {
        try {
          const refreshed = await api.auth.refresh(refreshToken);
          tokenStorage.setTokens(refreshed.accessToken, refreshed.refreshToken);
          const userProfile = await api.user.getMe();
          setUser(userProfile);
        } catch (refreshErr) {
          tokenStorage.clearTokens();
          setUser(null);
        }
      } else {
        tokenStorage.clearTokens();
        setUser(null);
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    initAuth();

    // Listen for auth-expired events dispatched by API client
    const handleExpired = () => {
      tokenStorage.clearTokens();
      setUser(null);
    };

    window.addEventListener('zoner:auth-expired', handleExpired);
    return () => window.removeEventListener('zoner:auth-expired', handleExpired);
  }, [initAuth]);

  const login = async (email, password) => {
    const data = await api.auth.login({ email, password });
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    setUser(data.user);
    return data.user;
  };

  const register = async (email, password, displayName, timeZone) => {
    const tz = timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    const data = await api.auth.register({ email, password, displayName, timeZone: tz });
    tokenStorage.setTokens(data.accessToken, data.refreshToken);
    setUser(data.user);
    return data.user;
  };

  const logout = async () => {
    const refreshToken = tokenStorage.getRefreshToken();
    if (refreshToken) {
      try {
        await api.auth.logout(refreshToken);
      } catch (err) {
        // Silently continue cleanup
      }
    }
    tokenStorage.clearTokens();
    setUser(null);
  };

  const updateUser = async (profile) => {
    const updated = await api.user.updateMe(profile);
    setUser(updated);
    return updated;
  };

  const value = {
    user,
    isAuthenticated: !!user,
    isLoading,
    login,
    register,
    logout,
    updateUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
