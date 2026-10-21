import apiClient from './client';

// Calls GET /health to check if the backend service is reachable.
export const fetchBackendHealth = async () => {
  const response = await apiClient.get('/health');
  return response.data;
};
