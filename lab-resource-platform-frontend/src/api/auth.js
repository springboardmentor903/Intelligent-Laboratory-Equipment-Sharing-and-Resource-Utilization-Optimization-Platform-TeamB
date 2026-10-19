import apiClient from './client';

// Endpoints supported by AuthController in the Spring Boot backend:
// - POST /api/auth/register (fullName, email, password)
// - POST /api/auth/login (email, password)
//
// Note: Password reset endpoints are not currently mounted in the backend.
// We avoid defining stubs here so nothing calls unmounted routes.

export const registerUser = async (data) => {
  const response = await apiClient.post('/auth/register', data);
  return response.data;
};

export const loginUser = async (data) => {
  const response = await apiClient.post('/auth/login', data);
  return response.data;
};

// Resolves the Google OAuth authorization initiation URL if configured via environment variables.
// Note: Backend OAuth2 endpoints are not currently implemented or mounted.
export const getGoogleAuthUrl = () => {
  return import.meta.env.VITE_GOOGLE_AUTH_URL || null;
};
