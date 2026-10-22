
import { useState, useEffect, useCallback } from 'react';
import { AuthContext } from './AuthContext';
import apiClient from '../api/client';
import {
  getStoredToken,
  getStoredUser,
  setAuthStorage,
  clearAuthStorage,
  decodeJwt,
} from '../utils/storage';

const getInitialSession = () => {
  const token = getStoredToken();

  if (!token) {
    return null;
  }

  const storedUser = getStoredUser();

  if (storedUser) {
    return storedUser;
  }

  const claims = decodeJwt(token);

  if (claims?.sub) {
    return {
      email: claims.sub,
      role: claims.role || 'RESEARCHER_STUDENT',
    };
  }

  return null;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => getInitialSession());
  const [loading, setLoading] = useState(true);

  // Retrieve the authenticated user's profile using the backend cookie.
  const refreshUser = useCallback(async () => {
    try {
      const response = await apiClient.get('/users/me');
      setUser(response.data);
      return response.data;
    } catch (error) {
      setUser(null);
      throw error;
    }
  }, []);

  // Restore an existing session or check for a Google login cookie.
  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      try {
        if (getStoredToken()) {
          return;
        }

        const response = await apiClient.get('/users/me');

        if (!cancelled) {
          setUser(response.data);
        }
      } catch {
        if (!cancelled) {
          setUser(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    restoreSession();

    return () => {
      cancelled = true;
    };
  }, []);

  // Preserve existing email/password login.
  const login = (token, userData = null, rememberMe = false) => {
    let resolvedUser = userData;

    if (token && typeof token === 'string' && !resolvedUser) {
      const claims = decodeJwt(token);

      if (claims?.sub) {
        resolvedUser = {
          email: claims.sub,
          role: claims.role || 'RESEARCHER_STUDENT',
        };
      }
    }

    setAuthStorage(token, resolvedUser, rememberMe);
    setUser(resolvedUser);
  };

  const logout = () => {
    clearAuthStorage();
    setUser(null);
  };

  const value = {
    user,
    setUser,
    loading,
    isAuthenticated: Boolean(user),
    login,
    logout,
    refreshUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export default AuthProvider;
