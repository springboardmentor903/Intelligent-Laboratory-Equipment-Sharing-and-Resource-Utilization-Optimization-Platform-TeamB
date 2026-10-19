// Storage keys for active auth tokens and cached user profiles.
// Includes legacy keys for backward compatibility.
const TOKEN_KEY = 'auth_token';
const LEGACY_TOKEN_KEY = 'token';
const USER_KEY = 'auth_user';
const LEGACY_USER_KEY = 'user';

// Decodes standard JWT payloads on the client without extra dependencies.
//
// Security note: We only use decoded claims for UI presentation (e.g. showing
// the user's email or role). Client decoding does NOT verify cryptographic
// signatures—the Spring Boot backend remains the authority on token validity.
export const decodeJwt = (token) => {
  try {
    if (!token || typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
};

// Checks whether the token has expired based on its 'exp' claim (converted from seconds to ms).
export const isTokenExpired = (token) => {
  const claims = decodeJwt(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 <= Date.now();
};

// Checks sessionStorage first (active tab) and falls back to localStorage (remember me).
// Clears stored credentials immediately if the token is expired.
export const getStoredToken = () => {
  try {
    const sessionToken =
      sessionStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(LEGACY_TOKEN_KEY);
    if (sessionToken) {
      if (isTokenExpired(sessionToken)) {
        clearAuthStorage();
        return null;
      }
      return sessionToken.trim();
    }

    const localToken =
      localStorage.getItem(TOKEN_KEY) || localStorage.getItem(LEGACY_TOKEN_KEY);
    if (localToken) {
      if (isTokenExpired(localToken)) {
        clearAuthStorage();
        return null;
      }
      return localToken.trim();
    }
  } catch {
    // Graceful fallback if storage access is restricted
  }
  return null;
};

// Retrieves cached user details from sessionStorage or localStorage.
export const getStoredUser = () => {
  try {
    const rawUser =
      sessionStorage.getItem(USER_KEY) ||
      sessionStorage.getItem(LEGACY_USER_KEY) ||
      localStorage.getItem(USER_KEY) ||
      localStorage.getItem(LEGACY_USER_KEY);

    if (rawUser) {
      return JSON.parse(rawUser);
    }
  } catch {
    return null;
  }
  return null;
};

// Saves credentials to localStorage if 'remember me' is checked, otherwise sessionStorage.
// Clears both stores first to avoid leftover tokens or mismatched sessions.
export const setAuthStorage = (token, user, rememberMe = false) => {
  clearAuthStorage();

  try {
    const storage = rememberMe ? localStorage : sessionStorage;
    if (token && typeof token === 'string') {
      storage.setItem(TOKEN_KEY, token.trim());
      storage.setItem(LEGACY_TOKEN_KEY, token.trim());
    }
    if (user && typeof user === 'object') {
      storage.setItem(USER_KEY, JSON.stringify(user));
      storage.setItem(LEGACY_USER_KEY, JSON.stringify(user));
    }
  } catch {
    // Storage quota or permission fallback
  }
};

// Removes auth keys from both stores on logout.
// We remove specific keys rather than calling clear() to avoid wiping unrelated app data.
export const clearAuthStorage = () => {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(LEGACY_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(LEGACY_USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(LEGACY_TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(LEGACY_USER_KEY);
  } catch {
    // Storage access fallback
  }
};
