import axios from "axios";

const api = axios.create({
    baseURL: "http://localhost:8080",
    withCredentials: true,
});

// Request Interceptor: Attach Authorization Bearer token from localStorage if available
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem("token");
        if (token && token !== "null" && token !== "undefined" && token.trim() !== "" && !config.headers.Authorization) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response Interceptor: Gracefully handle 401 Unauthorized (session expired or unauthenticated)
api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            localStorage.removeItem("token");
            const currentPath = window.location.pathname;
            // Only fire unauthorized redirect event if navigating within protected pages
            if (currentPath.startsWith("/dashboard")) {
                window.dispatchEvent(new CustomEvent("auth:unauthorized"));
            }
        }
        return Promise.reject(error);
    }
);

export default api;
