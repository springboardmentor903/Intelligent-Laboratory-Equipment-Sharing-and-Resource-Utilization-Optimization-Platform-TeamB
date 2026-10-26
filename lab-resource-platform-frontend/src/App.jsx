import { useCallback, useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AuthContext, useAuth } from "./context/AuthContext";
import { ToastProvider } from "./context/ToastContext";
import ProtectedRoute from "./components/ProtectedRoute";
import DashboardLayout from "./components/DashboardLayout";
import api from "./services/api";

import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";
import ForgotPassword from "./pages/auth/ForgotPassword";
import OAuthCallbackPage from "./pages/OAuthCallbackPage";

import StudentDashboard from "./pages/dashboards/StudentDashboard";
import LabTechnicianDashboard from "./pages/dashboards/LabTechnicianDashboard";
import LabManagerDashboard from "./pages/dashboards/LabManagerDashboard";
import DepartmentHeadDashboard from "./pages/dashboards/DepartmentHeadDashboard";
import InstitutionAdminDashboard from "./pages/dashboards/InstitutionAdminDashboard";
import SystemAdminDashboard from "./pages/dashboards/SystemAdminDashboard";

import "./App.css";

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    try {
      const response = await api.get("/auth/me");
      setUser(response.data);
      return response.data;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    let ignore = false;

    api
      .get("/auth/me")
      .then((response) => {
        if (!ignore) {
          setUser(response.data);
        }
      })
      .catch(() => {
        if (!ignore) {
          setUser(null);
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  async function login(credentials) {
    await api.post("/auth/mylogin", credentials);
    return refreshUser();
  }

  async function register(details) {
    return api.post("/auth/myregister", details);
  }

  async function logout() {
    try {
      await api.post("/auth/logout");
    } finally {
      setUser(null);
    }
  }

  return (
    <AuthContext.Provider
      value={{ user, loading, login, register, logout, refreshUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

function DashboardRedirect() {
  const { user, loading } = useAuth();

  if (loading) return <p>Loading workspace...</p>;
  if (!user) return <Navigate to="/login" replace />;

  const routes = {
    RESEARCHER_STUDENT: "/dashboard/student",
    LAB_TECHNICIAN: "/dashboard/technician",
    LAB_MANAGER: "/dashboard/lab-manager",
    DEPARTMENT_HEAD: "/dashboard/department-head",
    INSTITUTION_ADMINISTRATOR: "/dashboard/institution-admin",
    SYSTEM_ADMINISTRATOR: "/dashboard/system-admin",
  };

  return <Navigate to={routes[user.role] || "/login"} replace />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardRedirect />} />
        <Route
          path="student"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT"]}>
              <StudentDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="technician"
          element={
            <ProtectedRoute allowedRoles={["LAB_TECHNICIAN"]}>
              <LabTechnicianDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="lab-manager"
          element={
            <ProtectedRoute allowedRoles={["LAB_MANAGER"]}>
              <LabManagerDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="department-head"
          element={
            <ProtectedRoute allowedRoles={["DEPARTMENT_HEAD"]}>
              <DepartmentHeadDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="institution-admin"
          element={
            <ProtectedRoute allowedRoles={["INSTITUTION_ADMINISTRATOR"]}>
              <InstitutionAdminDashboard />
            </ProtectedRoute>
          }
        />

        <Route
          path="system-admin"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <SystemAdminDashboard />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route
        path="/forgot-password"
        element={<ForgotPassword />}
      />

      <Route
        path="/oauth/callback"
        element={<OAuthCallbackPage />}
      />
      <Route
        path="/oauth2/callback"
        element={<OAuthCallbackPage />}
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <AppRoutes />
      </ToastProvider>
    </AuthProvider>
  );
}