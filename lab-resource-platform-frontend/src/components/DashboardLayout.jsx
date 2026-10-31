import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";

function DashboardLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);
  const dropdownRef = useRef(null);

  const isSystemAdmin = user?.role === "SYSTEM_ADMINISTRATOR";
  const isStudent = user?.role === "RESEARCHER_STUDENT";
  const isInstitutionAdmin = user?.role === "INSTITUTION_ADMINISTRATOR";
  const isDepartmentHead = user?.role === "DEPARTMENT_HEAD";
  const isLabManager = user?.role === "LAB_MANAGER";
  const isTechnician = user?.role === "LAB_TECHNICIAN";

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setProfileDropdownOpen(false);
      }
    }
    if (profileDropdownOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [profileDropdownOpen]);

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

  const getStaffEquipmentsPath = () => {
    switch (user?.role) {
      case "INSTITUTION_ADMINISTRATOR":
        return "/dashboard/institution-admin/equipments";
      case "DEPARTMENT_HEAD":
        return "/dashboard/department-head/equipments";
      case "LAB_MANAGER":
        return "/dashboard/lab-manager/equipments";
      case "LAB_TECHNICIAN":
        return "/dashboard/technician/equipments";
      default:
        return "/dashboard";
    }
  };

  const getStaffBookingsPath = () => {
    switch (user?.role) {
      case "INSTITUTION_ADMINISTRATOR":
        return "/dashboard/institution-admin/bookings";
      case "DEPARTMENT_HEAD":
        return "/dashboard/department-head/bookings";
      case "LAB_MANAGER":
        return "/dashboard/lab-manager/bookings";
      case "LAB_TECHNICIAN":
        return "/dashboard/technician/bookings";
      default:
        return "/dashboard";
    }
  };

  async function handleLogout() {
    try {
      setProfileDropdownOpen(false);
      await logout();
      toast.success("Signed out successfully.");
      navigate("/login", { replace: true });
    } catch {
      toast.error("Logout failed. Please try again.");
    }
  }

  return (
    <div className={`dashboard-shell ${sidebarCollapsed ? "sidebar-is-collapsed" : ""}`}>
      {/* Fixed Sticky Top Navbar Header */}
      <header className="fixed-top-navbar">
        <div className="navbar-brand-section">
          <div className="brand header-brand">
            <span className="brand-mark">L</span>
            <span className="brand-text">LabResource</span>
          </div>

          {/* Toggle Sidebar Button (Right next to LabResource logo) */}
          <button
            type="button"
            className="sidebar-toggle-btn"
            onClick={() => setSidebarCollapsed((prev) => !prev)}
            aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <svg
              className="sidebar-toggle-icon"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="3" y="3" width="18" height="18" rx="4" />
              <line x1="9" y1="3" x2="9" y2="21" />
            </svg>
          </button>
        </div>

        {/* Top Navbar Right: Profile Button & Dropdown */}
        <div className="navbar-profile-section" ref={dropdownRef}>
          <button
            type="button"
            className="myntra-profile-btn"
            onClick={() => setProfileDropdownOpen((prev) => !prev)}
            aria-expanded={profileDropdownOpen}
            aria-haspopup="true"
          >
            <svg
              className="myntra-profile-icon"
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <span className="myntra-profile-label">Profile</span>
          </button>

          {/* Profile Dropdown Menu */}
          {profileDropdownOpen && (
            <div className="profile-dropdown-menu" role="menu">
              <div className="profile-dropdown-header">
                <strong>{user?.fullName || "User"}</strong>
                <p>{user?.email || ""}</p>
                <span className="profile-dropdown-role">
                  {user?.role ? user.role.replaceAll("_", " ") : "Member"}
                </span>
              </div>

              <div className="profile-dropdown-divider" />

              <Link
                to="/dashboard/profile"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon">👤</span>
                <span>Profile</span>
              </Link>

              <Link
                to="/dashboard/account-settings"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon">⚙️</span>
                <span>Account Settings</span>
              </Link>

              <div className="profile-dropdown-divider" />

              <button
                type="button"
                className="profile-dropdown-item logout"
                role="menuitem"
                onClick={handleLogout}
              >
                <span className="dropdown-item-icon">🚪</span>
                <span>Log out</span>
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Main Layout Body (Sidebar + Content Outlet) */}
      <div className="dashboard-container">
        {/* Backdrop for mobile drawer */}
        {mobileOpen && (
          <div
            className="sidebar-backdrop"
            onClick={() => setMobileOpen(false)}
            aria-hidden="true"
          />
        )}

        {/* Sidebar */}
        <aside className={`sidebar ${sidebarCollapsed ? "collapsed" : ""} ${mobileOpen ? "mobile-open" : ""}`}>
          <p className="sidebar-label">WORKSPACE</p>

          <nav className="sidebar-nav">
            {isSystemAdmin ? (
              /* System Admin Specific Navigation */
              <>
                <NavLink
                  to="/dashboard/system-admin"
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Dashboard"
                >
                  <span className="nav-icon">📊</span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon">🔬</span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/role-requests"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Role Requests"
                >
                  <span className="nav-icon">📋</span>
                  <span className="nav-text">Role Requests</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings"
                >
                  <span className="nav-icon">📅</span>
                  <span className="nav-text">Bookings</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/accounts"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Accounts Manage"
                >
                  <span className="nav-icon">👥</span>
                  <span className="nav-text">Accounts Manage</span>
                </NavLink>
              </>
            ) : isStudent ? (
              /* Student / Researcher Navigation */
              <>
                <NavLink
                  to="/dashboard/student"
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Dashboard"
                >
                  <span className="nav-icon">📊</span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <NavLink
                  to="/dashboard/student/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon">🔬</span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/student/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Booking History"
                >
                  <span className="nav-icon">📅</span>
                  <span className="nav-text">Booking History</span>
                </NavLink>
              </>
            ) : isInstitutionAdmin || isDepartmentHead || isLabManager || isTechnician ? (
              /* Institute Admin, Department Head, Lab Manager, Lab Technician Navigation */
              <>
                <NavLink
                  to={getDashboardPath()}
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Dashboard"
                >
                  <span className="nav-icon">📊</span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <NavLink
                  to={getStaffEquipmentsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon">🔬</span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to={getStaffBookingsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings"
                >
                  <span className="nav-icon">📅</span>
                  <span className="nav-text">Bookings</span>
                </NavLink>
              </>
            ) : (
              /* Other Generic Roles (Fallback) */
              <>
                <NavLink
                  to={getDashboardPath()}
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Overview"
                >
                  <span className="nav-text">Overview</span>
                </NavLink>

                <NavLink
                  to="/dashboard/profile"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Profile"
                >
                  <span className="nav-text">Profile</span>
                </NavLink>

                <NavLink
                  to="/dashboard/account-settings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Account Settings"
                >
                  <span className="nav-text">Account Settings</span>
                </NavLink>
              </>
            )}
          </nav>
        </aside>

        {/* Main Content Body */}
        <main className="dashboard-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default DashboardLayout;
