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

import ProfilePage from "./pages/profile/ProfilePage";
import AccountSettings from "./pages/profile/AccountSettings";
import RoleBasedAccess from "./components/RoleBasedAccess";

import StudentDashboard from "./pages/dashboards/StudentDashboard";
import StudentEquipmentsPage from "./pages/student/StudentEquipmentsPage";
import StudentBookingPage from "./pages/student/StudentBookingPage";
import StudentBookingHistoryPage from "./pages/student/StudentBookingHistoryPage";

import LabTechnicianDashboard from "./pages/dashboards/LabTechnicianDashboard";
import LabManagerDashboard from "./pages/dashboards/LabManagerDashboard";
import DepartmentHeadDashboard from "./pages/dashboards/DepartmentHeadDashboard";
import InstitutionAdminDashboard from "./pages/dashboards/InstitutionAdminDashboard";
import SystemAdminDashboard from "./pages/dashboards/SystemAdminDashboard";

import StaffEquipmentsPage from "./pages/staff/StaffEquipmentsPage";
import StaffBookingsPage from "./pages/staff/StaffBookingsPage";
import CreateEquipmentPage from "./pages/staff/CreateEquipmentPage";

import AdminEquipmentsPage from "./pages/admin/AdminEquipmentsPage";
import AdminRoleRequestsPage from "./pages/admin/AdminRoleRequestsPage";
import AdminBookingsPage from "./pages/admin/AdminBookingsPage";
import AdminAccountsPage from "./pages/admin/AdminAccountsPage";

import "./App.css";
import TechnicianAccessRequest from "./pages/auth/TechnicianAccessRequest";

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    try {
      const response = await api.get("/api/users/me");
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
      .get("/api/users/me")
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
    await api.post("/api/auth/mylogin", credentials);
    return refreshUser();
  }

  async function register(details) {
    return api.post("/api/auth/myregister", details);
  }

  async function logout() {
    try {
      await api.post("/api/auth/logout");
    } catch (error) {
      console.error("Logout request failed:", error);
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
  const { user } = useAuth();
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        path="/technician-access-request"
        element={<TechnicianAccessRequest />}
      />
      <Route
        path="/request-access"
        element={<TechnicianAccessRequest />}
      />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardRedirect />} />
        
        {/* Student / Researcher Routes */}
        <Route
          path="student"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT"]}>
              <StudentDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/equipments"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT"]}>
              <StudentEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/book/:id"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT"]}>
              <StudentBookingPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/bookings"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT"]}>
              <StudentBookingHistoryPage />
            </ProtectedRoute>
          }
        />

        {/* Create Equipment Page - For staff roles */}
        <Route
          path="equipment/create"
          element={
            <ProtectedRoute allowedRoles={["LAB_TECHNICIAN", "LAB_MANAGER", "DEPARTMENT_HEAD", "INSTITUTION_ADMINISTRATOR", "SYSTEM_ADMINISTRATOR"]}>
              <CreateEquipmentPage />
            </ProtectedRoute>
          }
        />

        {/* Lab Technician Routes */}
        <Route
          path="technician"
          element={
            <ProtectedRoute allowedRoles={["LAB_TECHNICIAN"]}>
              <LabTechnicianDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="technician/equipments"
          element={
            <ProtectedRoute allowedRoles={["LAB_TECHNICIAN"]}>
              <StaffEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="technician/bookings"
          element={
            <ProtectedRoute allowedRoles={["LAB_TECHNICIAN"]}>
              <StaffBookingsPage />
            </ProtectedRoute>
          }
        />

        {/* Lab Manager Routes */}
        <Route
          path="lab-manager"
          element={
            <ProtectedRoute allowedRoles={["LAB_MANAGER"]}>
              <LabManagerDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="lab-manager/equipments"
          element={
            <ProtectedRoute allowedRoles={["LAB_MANAGER"]}>
              <StaffEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="lab-manager/bookings"
          element={
            <ProtectedRoute allowedRoles={["LAB_MANAGER"]}>
              <StaffBookingsPage />
            </ProtectedRoute>
          }
        />

        {/* Department Head Routes */}
        <Route
          path="department-head"
          element={
            <ProtectedRoute allowedRoles={["DEPARTMENT_HEAD"]}>
              <DepartmentHeadDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="department-head/equipments"
          element={
            <ProtectedRoute allowedRoles={["DEPARTMENT_HEAD"]}>
              <StaffEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="department-head/bookings"
          element={
            <ProtectedRoute allowedRoles={["DEPARTMENT_HEAD"]}>
              <StaffBookingsPage />
            </ProtectedRoute>
          }
        />

        {/* Institution Administrator Routes */}
        <Route
          path="institution-admin"
          element={
            <ProtectedRoute allowedRoles={["INSTITUTION_ADMINISTRATOR"]}>
              <InstitutionAdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="institution-admin/equipments"
          element={
            <ProtectedRoute allowedRoles={["INSTITUTION_ADMINISTRATOR"]}>
              <StaffEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="institution-admin/bookings"
          element={
            <ProtectedRoute allowedRoles={["INSTITUTION_ADMINISTRATOR"]}>
              <StaffBookingsPage />
            </ProtectedRoute>
          }
        />

        {/* System Administrator Routes */}
        <Route
          path="system-admin"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <SystemAdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/equipments"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <AdminEquipmentsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/role-requests"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <AdminRoleRequestsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/bookings"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <AdminBookingsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="admin/accounts"
          element={
            <ProtectedRoute allowedRoles={["SYSTEM_ADMINISTRATOR"]}>
              <AdminAccountsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="profile"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="account-settings"
          element={
            <ProtectedRoute>
              <AccountSettings />
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
