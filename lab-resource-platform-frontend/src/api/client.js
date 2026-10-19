import axios from 'axios';
import { getStoredToken, clearAuthStorage } from '../utils/storage';

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// Shared Axios instance for backend API requests.
const apiClient = axios.create({
  baseURL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Attach the Bearer token to outgoing requests whenever we have an active session.
apiClient.interceptors.request.use(
  (config) => {
    try {
      const token = getStoredToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      // Continue without a token if storage can't be accessed
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Standardize error responses and clear stored credentials on 401 Unauthorized.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response ? error.response.status : null;

    // Drop stored credentials if the token is rejected by the backend
    if (status === 401) {
      clearAuthStorage();
    }

    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      (status === 404
        ? 'Endpoint not found (404)'
        : status === 401 || status === 403
        ? 'Authentication or authorization required'
        : error.code === 'ECONNABORTED'
        ? 'Request timed out after 10 seconds'
        : error.message === 'Network Error' || !error.response
        ? 'Backend not reachable (Network Error)'
        : `Request failed with status ${status}`);

    const standardizedError = new Error(message);
    standardizedError.status = status;
    standardizedError.code = error.code;
    standardizedError.response = error.response;
    standardizedError.isNetworkError = !error.response;

    return Promise.reject(standardizedError);
  }
);

export default apiClient;
