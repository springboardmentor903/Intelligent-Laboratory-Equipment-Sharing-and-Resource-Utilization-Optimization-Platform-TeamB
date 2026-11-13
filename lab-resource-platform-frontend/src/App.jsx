import { useCallback, useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AuthContext, useAuth } from "./context/AuthContext";
import { ToastProvider } from "./context/ToastContext";
import { SearchProvider } from "./context/SearchContext";
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

import StudentEquipmentsPage from "./pages/student/StudentEquipmentsPage";
import StudentBookingPage from "./pages/student/StudentBookingPage";
import StudentBookingHistoryPage from "./pages/student/StudentBookingHistoryPage";

import StaffEquipmentsPage from "./pages/staff/StaffEquipmentsPage";
import StaffBookingsPage from "./pages/staff/StaffBookingsPage";
import CreateEquipmentPage from "./pages/staff/CreateEquipmentPage";

import AdminEquipmentsPage from "./pages/admin/AdminEquipmentsPage";
import AdminRoleRequestsPage from "./pages/admin/AdminRoleRequestsPage";
import AdminBookingsPage from "./pages/admin/AdminBookingsPage";
import AdminAccountsPage from "./pages/admin/AdminAccountsPage";
import LiveUtilizationPage from "./pages/utilization/LiveUtilizationPage";
import UtilizationAnalyticsPage from "./pages/analytics/UtilizationAnalyticsPage";
import CrossInstitutionDiscoveryPage from "./pages/sharing/CrossInstitutionDiscoveryPage";
import SharingAgreementsPage from "./pages/sharing/SharingAgreementsPage";
import SharingAnalyticsPage from "./pages/sharing/SharingAnalyticsPage";

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
          localStorage.removeItem("token");
          setUser(null);
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    const handleUnauthorized = () => {
      setUser(null);
    };
    window.addEventListener("auth:unauthorized", handleUnauthorized);

    return () => {
      ignore = true;
      window.removeEventListener("auth:unauthorized", handleUnauthorized);
    };
  }, []);

  async function login(credentials) {
    const response = await api.post("/api/auth/mylogin", credentials);
    if (response.data?.token) {
      localStorage.setItem("token", response.data.token);
    }
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
      localStorage.removeItem("token");
      setUser(null);
    }
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        login,
        register,
        logout,
        refreshUser,
        isAuthenticated: Boolean(user),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

function RoleDefaultRedirect() {
  const { user } = useAuth();

  switch (user?.role) {
    case "RESEARCHER_STUDENT":
      return <Navigate to="/dashboard/student/equipments" replace />;
    case "LAB_TECHNICIAN":
      return <Navigate to="/dashboard/technician/equipments" replace />;
    case "LAB_MANAGER":
      return <Navigate to="/dashboard/lab-manager/equipments" replace />;
    case "DEPARTMENT_HEAD":
      return <Navigate to="/dashboard/department-head/equipments" replace />;
    case "INSTITUTION_ADMINISTRATOR":
      return <Navigate to="/dashboard/institution-admin/equipments" replace />;
    case "SYSTEM_ADMINISTRATOR":
      return <Navigate to="/dashboard/admin/equipments" replace />;
    default:
      return <Navigate to="/login" replace />;
  }
}

function AppRoutes() {
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
        <Route index element={<RoleDefaultRedirect />} />

        {/* Dedicated standalone equipment creation page */}
        <Route
          path="equipment/create"
          element={
            <ProtectedRoute
              allowedRoles={[
                "LAB_TECHNICIAN",
                "LAB_MANAGER",
                "DEPARTMENT_HEAD",
                "INSTITUTION_ADMINISTRATOR",
                "SYSTEM_ADMINISTRATOR",
              ]}
            >
              <CreateEquipmentPage />
            </ProtectedRoute>
          }
        />

        {/* Live Utilization Telemetry & Monitoring */}
        <Route
          path="utilization"
          element={
            <ProtectedRoute>
              <LiveUtilizationPage />
            </ProtectedRoute>
          }
        />

        {/* Analytics Engine */}
        <Route
          path="analytics"
          element={
            <ProtectedRoute
              allowedRoles={[
                "LAB_MANAGER",
                "DEPARTMENT_HEAD",
                "INSTITUTION_ADMINISTRATOR",
                "SYSTEM_ADMINISTRATOR",
              ]}
            >
              <UtilizationAnalyticsPage />
            </ProtectedRoute>
          }
        />

        {/* Cross-Institution Equipment Sharing & External Booking */}
        <Route
          path="sharing/discovery"
          element={
            <ProtectedRoute>
              <CrossInstitutionDiscoveryPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="sharing/agreements"
          element={
            <ProtectedRoute>
              <SharingAgreementsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="sharing/analytics"
          element={
            <ProtectedRoute
              allowedRoles={[
                "LAB_MANAGER",
                "DEPARTMENT_HEAD",
                "INSTITUTION_ADMINISTRATOR",
                "SYSTEM_ADMINISTRATOR",
              ]}
            >
              <SharingAnalyticsPage />
            </ProtectedRoute>
          }
        />

        {/* Student Routes - Default is Equipments */}
        <Route
          path="student"
          element={<Navigate to="/dashboard/student/equipments" replace />}
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
          path="book/:id"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT", "LAB_TECHNICIAN", "LAB_MANAGER", "DEPARTMENT_HEAD", "INSTITUTION_ADMINISTRATOR", "SYSTEM_ADMINISTRATOR"]}>
              <StudentBookingPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="student/book/:id"
          element={
            <ProtectedRoute allowedRoles={["RESEARCHER_STUDENT", "LAB_TECHNICIAN", "LAB_MANAGER", "DEPARTMENT_HEAD", "INSTITUTION_ADMINISTRATOR", "SYSTEM_ADMINISTRATOR"]}>
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

        {/* Lab Technician Routes - Default is Equipments */}
        <Route
          path="technician"
          element={<Navigate to="/dashboard/technician/equipments" replace />}
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

        {/* Lab Manager Routes - Default is Equipments */}
        <Route
          path="lab-manager"
          element={<Navigate to="/dashboard/lab-manager/equipments" replace />}
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

        {/* Department Head Routes - Default is Equipments */}
        <Route
          path="department-head"
          element={<Navigate to="/dashboard/department-head/equipments" replace />}
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

        {/* Institution Administrator Routes - Default is Equipments */}
        <Route
          path="institution-admin"
          element={<Navigate to="/dashboard/institution-admin/equipments" replace />}
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

        {/* System Administrator Routes - Default is Equipments */}
        <Route
          path="system-admin"
          element={<Navigate to="/dashboard/admin/equipments" replace />}
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

        {/* Profile & Account Settings Routes */}
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
        <SearchProvider>
          <AppRoutes />
        </SearchProvider>
      </ToastProvider>
    </AuthProvider>
  );
}
