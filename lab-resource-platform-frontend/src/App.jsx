import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { ToastProvider } from "./context/ToastContext";
import ProtectedRoute from "./components/ProtectedRoute";
import DashboardLayout from "./components/DashboardLayout";

import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";

import StudentDashboard from "./pages/dashboards/StudentDashboard";
import LabTechnicianDashboard from "./pages/dashboards/LabTechnicianDashboard";
import LabManagerDashboard from "./pages/dashboards/LabManagerDashboard";
import DepartmentHeadDashboard from "./pages/dashboards/DepartmentHeadDashboard";
import InstitutionAdminDashboard from "./pages/dashboards/InstitutionAdminDashboard";
import SystemAdminDashboard from "./pages/dashboards/SystemAdminDashboard";

import "./App.css";

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