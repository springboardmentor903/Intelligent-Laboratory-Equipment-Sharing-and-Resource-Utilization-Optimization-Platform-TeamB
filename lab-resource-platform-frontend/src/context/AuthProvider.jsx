import { useState } from 'react';
import { AuthContext } from './AuthContext';
import {
  getStoredToken,
  getStoredUser,
  setAuthStorage,
  clearAuthStorage,
  decodeJwt,
} from '../utils/storage';

const getInitialSession = () => {
  const token = getStoredToken();
  // Require a valid, non-expired token before restoring an active session
  if (!token) {
    return null;
  }

  const storedUser = getStoredUser();
  if (storedUser) {
    return storedUser;
  }

  // Fall back to decoded JWT claims for UI display if no cached profile exists.
  // Note: this is strictly for presentation—the backend authorizes real requests.
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
    isAuthenticated: Boolean(user),
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export default AuthProvider;
