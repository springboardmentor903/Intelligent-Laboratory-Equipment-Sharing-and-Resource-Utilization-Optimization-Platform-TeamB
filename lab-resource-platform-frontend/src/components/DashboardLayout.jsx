import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";
import { useSearch } from "../context/SearchContext";
import {
  FiSearch,
  FiX,
  FiUser,
  FiSettings,
  FiKey,
  FiLogOut,
  FiLayers,
  FiClipboard,
  FiCalendar,
  FiActivity,
  FiBarChart2,
  FiUsers,
  FiShare2,
  FiFileText,
  FiTrendingUp,
} from "react-icons/fi";

function DashboardLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const { searchTerm, setSearchTerm, searchPlaceholder, showSearch } = useSearch();

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
  const canAccessAnalytics = isSystemAdmin || isInstitutionAdmin || isDepartmentHead || isLabManager;

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
        <div className="navbar-left-cluster">
          {/* Platform brand logo with reload capability */}
          <a
            type="button"
            className="navbar-brand-pill navbar-brand-reload-btn"
            onClick={() => window.location.reload()}
            title="Click to reload page"
            aria-label="Reload platform"
          >
            <img src="/logo.png" alt="LabResource Logo" className="brand-logo-image" />
            <span className="brand-title-text">LabResource</span>
          </a>
        </div>

        {/* Dynamic Page-Aware Search Bar in Center Navbar */}
        {showSearch && (
          <div className="navbar-center-search-cluster">
            <div className="navbar-search-bar">
              <span className="navbar-search-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                <FiSearch size={16} />
              </span>
              <input
                type="text"
                className="navbar-search-input"
                placeholder={searchPlaceholder}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                aria-label="Search on current page"
              />
              {searchTerm && (
                <button
                  type="button"
                  className="navbar-search-clear"
                  onClick={() => setSearchTerm("")}
                  title="Clear search query"
                  aria-label="Clear search query"
                  style={{ display: "inline-flex", alignItems: "center", justifyContent: "center" }}
                >
                  <FiX size={14} />
                </button>
              )}
            </div>
          </div>
        )}

        {/* Right side Profile Icon with Dropdown Menu */}
        <div className="navbar-right-cluster" ref={dropdownRef}>
          <button
            type="button"
            className={`myntra-profile-btn ${profileDropdownOpen ? "active" : ""}`}
            onClick={() => setProfileDropdownOpen((prev) => !prev)}
            aria-expanded={profileDropdownOpen}
            aria-haspopup="true"
            aria-label="Open profile menu"
          >
            <svg
              className="myntra-profile-svg"
              width="20"
              height="20"
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
                <span className="dropdown-item-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                  <FiUser size={16} />
                </span>
                <span>Profile</span>
              </Link>

              <Link
                to="/dashboard/account-settings"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                  <FiSettings size={16} />
                </span>
                <span>Account Settings</span>
              </Link>

              <Link
                to="/forgot-password"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                  <FiKey size={16} />
                </span>
                <span>Forgot / Reset Password</span>
              </Link>

              <div className="profile-dropdown-divider" />

              <button
                type="button"
                className="profile-dropdown-item logout"
                role="menuitem"
                onClick={handleLogout}
              >
                <span className="dropdown-item-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                  <FiLogOut size={16} />
                </span>
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
          <div className="workspace-header">
            <p className="sidebar-label">WORKSPACE</p>

            <button
              type="button"
              className="sidebar-toggle-btn"
              onClick={() => setSidebarCollapsed((prev) => !prev)}
              aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
              title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            >
              <svg
                className="toggle-sidebar-svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                <line x1="9" y1="3" x2="9" y2="21" />
              </svg>
            </button>
          </div>

          <nav className="sidebar-nav">
            {isSystemAdmin ? (
              /* System Admin Specific Navigation */
              <>
                <NavLink
                  to="/dashboard/admin/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiLayers size={18} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/discovery"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Inter-Institution Discovery"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiShare2 size={18} />
                  </span>
                  <span className="nav-text">Cross-Institution</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/role-requests"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Role Requests"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiClipboard size={18} />
                  </span>
                  <span className="nav-text">Role Requests</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings & Approvals"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiCalendar size={18} />
                  </span>
                  <span className="nav-text">Bookings</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/agreements"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Sharing Agreements"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiFileText size={18} />
                  </span>
                  <span className="nav-text">Sharing Contracts</span>
                </NavLink>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Utilization"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiActivity size={18} />
                  </span>
                  <span className="nav-text">Live Utilization</span>
                </NavLink>

                <NavLink
                  to="/dashboard/analytics"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Utilization Analytics"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiBarChart2 size={18} />
                  </span>
                  <span className="nav-text">Analytics Engine</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/analytics"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Sharing Analytics"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiTrendingUp size={18} />
                  </span>
                  <span className="nav-text">Sharing Analytics</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/accounts"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Accounts Manage"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiUsers size={18} />
                  </span>
                  <span className="nav-text">Accounts Manage</span>
                </NavLink>
              </>
            ) : isStudent ? (
              /* Student / Researcher Navigation */
              <>
                <NavLink
                  to="/dashboard/student/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiLayers size={18} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/discovery"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Inter-Institution Discovery"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiShare2 size={18} />
                  </span>
                  <span className="nav-text">Cross-Institution</span>
                </NavLink>

                <NavLink
                  to="/dashboard/student/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Booking History"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiCalendar size={18} />
                  </span>
                  <span className="nav-text">Booking History</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/agreements"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="My Access Requests"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiFileText size={18} />
                  </span>
                  <span className="nav-text">Access Requests</span>
                </NavLink>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Telemetry"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiActivity size={18} />
                  </span>
                  <span className="nav-text">Live Telemetry</span>
                </NavLink>
              </>
            ) : isInstitutionAdmin || isDepartmentHead || isLabManager || isTechnician ? (
              /* Institute Admin, Department Head, Lab Manager, Lab Technician Navigation */
              <>
                <NavLink
                  to={getStaffEquipmentsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiLayers size={18} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/discovery"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Inter-Institution Discovery"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiShare2 size={18} />
                  </span>
                  <span className="nav-text">Cross-Institution</span>
                </NavLink>

                <NavLink
                  to={getStaffBookingsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings & Approvals"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiCalendar size={18} />
                  </span>
                  <span className="nav-text">Bookings</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/agreements"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Sharing Agreements & Requests"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiFileText size={18} />
                  </span>
                  <span className="nav-text">Sharing Contracts</span>
                </NavLink>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Utilization"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiActivity size={18} />
                  </span>
                  <span className="nav-text">Live Utilization</span>
                </NavLink>

                {canAccessAnalytics && (
                  <>
                    <NavLink
                      to="/dashboard/analytics"
                      className={({ isActive }) =>
                        `sidebar-nav-item ${isActive ? "active" : ""}`
                      }
                      onClick={() => setMobileOpen(false)}
                      title="Utilization Analytics"
                    >
                      <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                        <FiBarChart2 size={18} />
                      </span>
                      <span className="nav-text">Analytics Engine</span>
                    </NavLink>

                    <NavLink
                      to="/dashboard/sharing/analytics"
                      className={({ isActive }) =>
                        `sidebar-nav-item ${isActive ? "active" : ""}`
                      }
                      onClick={() => setMobileOpen(false)}
                      title="Sharing Analytics"
                    >
                      <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                        <FiTrendingUp size={18} />
                      </span>
                      <span className="nav-text">Sharing Analytics</span>
                    </NavLink>
                  </>
                )}
              </>
            ) : (
              /* Fallback Generic Roles */
              <>
                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Utilization"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiActivity size={18} />
                  </span>
                  <span className="nav-text">Live Utilization</span>
                </NavLink>

                <NavLink
                  to="/dashboard/profile"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Profile"
                >
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiUser size={18} />
                  </span>
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
                  <span className="nav-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                    <FiSettings size={18} />
                  </span>
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
