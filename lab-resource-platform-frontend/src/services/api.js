import axios from "axios";

const rawBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";
const normalizedBaseUrl = rawBaseUrl.replace(/\/+$/, "");

const api = axios.create({
  baseURL: normalizedBaseUrl,
  withCredentials: true,
});

// Request interceptor to normalize path prefixes
// If baseURL ends with /api and the request URL starts with /api/, remove duplicate /api
api.interceptors.request.use(
  (config) => {
    if (config.url) {
      if (normalizedBaseUrl.endsWith("/api") && config.url.startsWith("/api/")) {
        config.url = config.url.substring(4); // e.g. "/api/users/me" -> "/users/me"
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor to manage session state changes cleanly
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || "";

    // If 401 on an authenticated endpoint (exclude login/register and user me probe)
    if (status === 401) {
      const isAuthProbe = url.includes("/users/me") || url.includes("/auth/me");
      const isLoginOrRegister =
        url.includes("/login") ||
        url.includes("/mylogin") ||
        url.includes("/register") ||
        url.includes("/myregister");

      if (!isAuthProbe && !isLoginOrRegister) {
        // Notify components that session expired, without forcing hard window reloads
        window.dispatchEvent(new CustomEvent("auth:session-expired"));
      }
    }

    return Promise.reject(error);
  }
);

export default api;