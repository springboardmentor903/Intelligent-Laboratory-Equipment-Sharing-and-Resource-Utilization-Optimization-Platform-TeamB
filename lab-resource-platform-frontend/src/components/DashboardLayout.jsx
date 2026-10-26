import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";

function DashboardLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [mobileOpen, setMobileOpen] = useState(false);

  const getDashboardPath = () => {
    switch (user?.role) {
      case "RESEARCHER_STUDENT":
        return "/dashboard/student";
      case "LAB_TECHNICIAN":
        return "/dashboard/technician";
      case "LAB_MANAGER":
        return "/dashboard/lab-manager";
      case "DEPARTMENT_HEAD":
        return "/dashboard/department-head";
      case "INSTITUTION_ADMINISTRATOR":
        return "/dashboard/institution-admin";
      case "SYSTEM_ADMINISTRATOR":
        return "/dashboard/system-admin";
      default:
        return "/dashboard";
    }
  };

  async function handleLogout() {
    try {
      await logout();
      toast.success("Signed out successfully.");
      navigate("/login", { replace: true });
    } catch {
      toast.error("Logout failed. Please try again.");
    }
  }

  return (
    <div className="dashboard-shell">
      {/* Mobile top bar */}
      <header className="mobile-header">
        <div className="brand sidebar-brand">
          <span className="brand-mark">L</span>
          <span>LabResource</span>
        </div>
        <button
          type="button"
          className="mobile-toggle"
          onClick={() => setMobileOpen((prev) => !prev)}
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
        >
          {mobileOpen ? "✕" : "☰"}
        </button>
      </header>

      {/* Backdrop for mobile drawer */}
      {mobileOpen && (
        <div
          className="sidebar-backdrop"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside className={`sidebar ${mobileOpen ? "mobile-open" : ""}`}>
        <div className="brand sidebar-brand">
          <span className="brand-mark">L</span>
          <span>LabResource</span>
        </div>

        <p className="sidebar-label">WORKSPACE</p>

        <nav className="sidebar-nav">
          <NavLink
              to={getDashboardPath()}
              end
              className={({ isActive }) =>
                  `sidebar-nav-item ${isActive ? "active" : ""}`
              }
              onClick={() => setMobileOpen(false)}
          >
            <span className="nav-text">Overview</span>
          </NavLink>

          <NavLink
              to="/dashboard/profile"
              className={({ isActive }) =>
                  `sidebar-nav-item ${isActive ? "active" : ""}`
              }
              onClick={() => setMobileOpen(false)}
          >
            <span className="nav-text">Profile</span>
          </NavLink>

          <NavLink
              to="/dashboard/account-settings"
              className={({ isActive }) =>
                  `sidebar-nav-item ${isActive ? "active" : ""}`
              }
              onClick={() => setMobileOpen(false)}
          >
            <span className="nav-text">Account Settings</span>
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-user">
            <div className="user-avatar" aria-hidden="true">
              {user?.fullName ? user.fullName.charAt(0).toUpperCase() : "U"}
            </div>
            <div className="user-info">
              <strong>{user?.fullName}</strong>
              <span>{user?.role?.replaceAll("_", " ")}</span>
            </div>
          </div>

          <button
            className="logout-button"
            type="button"
            onClick={handleLogout}
          >
            <span>Log out</span>
          </button>
        </div>
      </aside>

      <main className="dashboard-main">
        <Outlet />
      </main>
    </div>
  );
}

export default DashboardLayout;