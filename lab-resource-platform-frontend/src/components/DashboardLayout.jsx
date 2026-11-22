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
  FiHome,
  FiLayers,
  FiClipboard,
  FiCalendar,
  FiActivity,
  FiBarChart2,
  FiUsers,
  FiShare2,
  FiFileText,
  FiTrendingUp,
  FiMenu,
  FiChevronDown,
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
  const searchInputRef = useRef(null);

  const isSystemAdmin = user?.role === "SYSTEM_ADMINISTRATOR";
  const isStudent = user?.role === "RESEARCHER_STUDENT";
  const isInstitutionAdmin = user?.role === "INSTITUTION_ADMINISTRATOR";
  const isDepartmentHead = user?.role === "DEPARTMENT_HEAD";
  const isLabManager = user?.role === "LAB_MANAGER";
  const isTechnician = user?.role === "LAB_TECHNICIAN";
  const canAccessAnalytics = isSystemAdmin || isInstitutionAdmin || isDepartmentHead || isLabManager;

  // Global Ctrl+K (or Cmd+K) shortcut to focus search input
  useEffect(() => {
    function handleKeyDown(e) {
      if ((e.ctrlKey || e.metaKey) && (e.key === "k" || e.key === "K")) {
        if (showSearch && searchInputRef.current) {
          e.preventDefault();
          searchInputRef.current.focus();
          searchInputRef.current.select();
        }
      }
      if (e.key === "Escape") {
        if (profileDropdownOpen) {
          setProfileDropdownOpen(false);
        } else if (searchInputRef.current && document.activeElement === searchInputRef.current) {
          searchInputRef.current.blur();
        }
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [showSearch, profileDropdownOpen]);

  // Close profile dropdown when clicking outside
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

  const getStaffDashboardPath = () => {
    switch (user?.role) {
      case "INSTITUTION_ADMINISTRATOR":
        return "/dashboard/institution-admin";
      case "DEPARTMENT_HEAD":
        return "/dashboard/department-head";
      case "LAB_MANAGER":
        return "/dashboard/lab-manager";
      case "LAB_TECHNICIAN":
        return "/dashboard/technician";
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

  const userInitial = (user?.fullName || user?.name || user?.email || "U")
    .charAt(0)
    .toUpperCase();

  const formattedRole = user?.role
    ? user.role.replaceAll("_", " ")
    : "RESEARCHER";

  return (
    <div className={`dashboard-shell ${sidebarCollapsed ? "sidebar-is-collapsed" : ""}`}>
      {/* Fixed Sticky Top Navbar Header */}
      <header className="fixed-top-navbar" role="banner">
        <div className="navbar-left-cluster">
          {/* Sidebar collapse toggle button */}
          <button
            type="button"
            className="sidebar-toggle-btn"
            onClick={() => {
              if (window.innerWidth <= 768) {
                setMobileOpen((prev) => !prev);
              } else {
                setSidebarCollapsed((prev) => !prev);
              }
            }}
            aria-label={sidebarCollapsed ? "Expand sidebar navigation" : "Collapse sidebar navigation"}
            title={sidebarCollapsed ? "Expand sidebar (Ctrl+B)" : "Collapse sidebar"}
          >
            <FiMenu size={18} />
          </button>

          {/* Platform brand logo with reload capability */}
          <a
            type="button"
            className="navbar-brand-reload-btn"
            onClick={() => window.location.reload()}
            title="Click to reload application"
            aria-label="Reload platform"
          >
            <img src="/logo.png" alt="LabResource" className="brand-logo-image" />
            <span className="brand-title-text">LabResource</span>
          </a>
        </div>

        {/* Dynamic Page-Aware Search Bar in Center Navbar */}
        {showSearch && (
          <div className="navbar-center-search-cluster" role="search">
            <div className="navbar-search-bar">
              <span className="navbar-search-icon" aria-hidden="true">
                <FiSearch size={15} />
              </span>
              <input
                ref={searchInputRef}
                type="text"
                className="navbar-search-input"
                placeholder={searchPlaceholder || "Search resources, equipment, bookings..."}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                aria-label="Search current workspace"
              />
              {searchTerm ? (
                <button
                  type="button"
                  className="navbar-search-clear"
                  onClick={() => {
                    setSearchTerm("");
                    searchInputRef.current?.focus();
                  }}
                  title="Clear search query"
                  aria-label="Clear search query"
                >
                  <FiX size={14} />
                </button>
              ) : (
                <span className="navbar-search-kbd" title="Press Ctrl+K to search">
                  Ctrl+K
                </span>
              )}
            </div>
          </div>
        )}

        {/* Right side Profile Popover Menu */}
        <div className="navbar-right-cluster" ref={dropdownRef}>
          <button
            type="button"
            className={`enterprise-profile-trigger ${profileDropdownOpen ? "active" : ""}`}
            onClick={() => setProfileDropdownOpen((prev) => !prev)}
            aria-expanded={profileDropdownOpen}
            aria-haspopup="true"
            aria-label="Open user account menu"
          >
            <span className="enterprise-avatar-circle" aria-hidden="true">
              {userInitial}
            </span>
            <div className="enterprise-user-meta">
              <span className="enterprise-user-name">
                {user?.fullName || user?.name || "Account"}
              </span>
              <span className="enterprise-user-role">
                {formattedRole}
              </span>
            </div>
            <FiChevronDown size={14} className="enterprise-chevron-icon" aria-hidden="true" />
          </button>

          {/* Profile Dropdown Popover */}
          {profileDropdownOpen && (
            <div className="profile-dropdown-menu" role="menu" aria-label="Account options">
              <div className="profile-dropdown-header">
                <strong>{user?.fullName || user?.name || "User"}</strong>
                <p title={user?.email || ""}>{user?.email || "No email"}</p>
                <span className="profile-dropdown-role">
                  {formattedRole}
                </span>
              </div>

              <div className="profile-dropdown-divider" />

              <Link
                to="/dashboard/profile"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon" aria-hidden="true">
                  <FiUser size={15} />
                </span>
                <span>Profile Details</span>
              </Link>

              <Link
                to="/dashboard/account-settings"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon" aria-hidden="true">
                  <FiSettings size={15} />
                </span>
                <span>Account Settings</span>
              </Link>

              <Link
                to="/forgot-password"
                className="profile-dropdown-item"
                role="menuitem"
                onClick={() => setProfileDropdownOpen(false)}
              >
                <span className="dropdown-item-icon" aria-hidden="true">
                  <FiKey size={15} />
                </span>
                <span>Reset Password</span>
              </Link>

              <div className="profile-dropdown-divider" />

              <button
                type="button"
                className="profile-dropdown-item logout"
                role="menuitem"
                onClick={handleLogout}
              >
                <span className="dropdown-item-icon" aria-hidden="true">
                  <FiLogOut size={15} />
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

        {/* Deep Navy Enterprise Sidebar */}
        <aside
          className={`sidebar ${sidebarCollapsed ? "collapsed" : ""} ${mobileOpen ? "mobile-open" : ""}`}
          aria-label="Workspace Navigation"
        >
          <nav className="sidebar-nav">
            {isSystemAdmin ? (
              /* System Admin Specific Navigation */
              <>
                <p className="sidebar-section-label">OVERVIEW</p>

                <NavLink
                  to="/dashboard/admin"
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="System Admin Dashboard"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiHome size={17} />
                  </span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <p className="sidebar-section-label">OPERATIONS</p>

                <NavLink
                  to="/dashboard/admin/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments Catalog"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiLayers size={17} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings & Approvals"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiCalendar size={17} />
                  </span>
                  <span className="nav-text">Bookings</span>
                </NavLink>

                <p className="sidebar-section-label">COLLABORATION</p>

                <NavLink
                  to="/dashboard/sharing/discovery"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Inter-Institution Resource Discovery"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiShare2 size={17} />
                  </span>
                  <span className="nav-text">Cross-Institution</span>
                </NavLink>

                <NavLink
                  to="/dashboard/admin/role-requests"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Role Elevation Requests"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiClipboard size={17} />
                  </span>
                  <span className="nav-text">Role Requests</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/agreements"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Sharing Agreements & Contracts"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiFileText size={17} />
                  </span>
                  <span className="nav-text">Sharing Contracts</span>
                </NavLink>

                <p className="sidebar-section-label">MONITORING &amp; METRICS</p>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Telemetry & Utilization"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiActivity size={17} />
                  </span>
                  <span className="nav-text">Live Utilization</span>
                </NavLink>

                <NavLink
                  to="/dashboard/analytics"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Utilization Analytics Engine"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiBarChart2 size={17} />
                  </span>
                  <span className="nav-text">Analytics Engine</span>
                </NavLink>

                <NavLink
                  to="/dashboard/sharing/analytics"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Inter-Institution Sharing Analytics"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiTrendingUp size={17} />
                  </span>
                  <span className="nav-text">Sharing Analytics</span>
                </NavLink>

                <p className="sidebar-section-label">ADMINISTRATION</p>

                <NavLink
                  to="/dashboard/admin/accounts"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Manage User Accounts & Roles"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiUsers size={17} />
                  </span>
                  <span className="nav-text">Accounts Manage</span>
                </NavLink>
              </>
            ) : isStudent ? (
              /* Student / Researcher Navigation */
              <>
                <p className="sidebar-section-label">OVERVIEW</p>

                <NavLink
                  to="/dashboard/student"
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Student Dashboard"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiHome size={17} />
                  </span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <p className="sidebar-section-label">RESEARCH WORKSPACE</p>

                <NavLink
                  to="/dashboard/student/equipments"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipment Catalog"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiLayers size={17} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <p className="sidebar-section-label">MY RESERVATIONS</p>

                <NavLink
                  to="/dashboard/student/bookings"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="My Booking History & Waitlist"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiCalendar size={17} />
                  </span>
                  <span className="nav-text">Booking History</span>
                </NavLink>

                <p className="sidebar-section-label">MONITORING</p>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Telemetry Monitoring"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiActivity size={17} />
                  </span>
                  <span className="nav-text">Live Telemetry</span>
                </NavLink>
              </>
            ) : isInstitutionAdmin || isDepartmentHead || isLabManager || isTechnician ? (
              /* Institute Admin, Department Head, Lab Manager, Lab Technician Navigation */
              <>
                <p className="sidebar-section-label">OVERVIEW</p>

                <NavLink
                  to={getStaffDashboardPath()}
                  end
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Dashboard Overview"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiHome size={17} />
                  </span>
                  <span className="nav-text">Dashboard</span>
                </NavLink>

                <p className="sidebar-section-label">LAB INVENTORY</p>

                <NavLink
                  to={getStaffEquipmentsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Equipments Management"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiLayers size={17} />
                  </span>
                  <span className="nav-text">Equipments</span>
                </NavLink>

                <p className="sidebar-section-label">COLLABORATION</p>

                {/* Inter-Equipment requesting page placed strictly for Department Head */}
                {isDepartmentHead && (
                  <NavLink
                    to="/dashboard/sharing/discovery"
                    className={({ isActive }) =>
                      `sidebar-nav-item ${isActive ? "active" : ""}`
                    }
                    onClick={() => setMobileOpen(false)}
                    title="Request Equipment from Partner Institutions"
                  >
                    <span className="nav-icon" aria-hidden="true">
                      <FiShare2 size={17} />
                    </span>
                    <span className="nav-text">Inter-Institution Requests</span>
                  </NavLink>
                )}

                {/* Sharing Contracts & Access Requests management - for Institution Admin & Department Head */}
                <NavLink
                  to="/dashboard/sharing/agreements"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title={isInstitutionAdmin ? "Review & Manage Inter-Institution Access Requests" : "Sharing Agreements & Requests"}
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiFileText size={17} />
                  </span>
                  <span className="nav-text">
                    {isInstitutionAdmin ? "Sharing Requests & Contracts" : "Sharing Contracts"}
                  </span>
                </NavLink>

                <p className="sidebar-section-label">OPERATIONS</p>

                <NavLink
                  to={getStaffBookingsPath()}
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Bookings & Approvals"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiCalendar size={17} />
                  </span>
                  <span className="nav-text">Bookings</span>
                </NavLink>

                <p className="sidebar-section-label">MONITORING</p>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Telemetry & Utilization"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiActivity size={17} />
                  </span>
                  <span className="nav-text">Live Utilization</span>
                </NavLink>

                {canAccessAnalytics && (
                  <>
                    <p className="sidebar-section-label">ANALYTICS</p>

                    <NavLink
                      to="/dashboard/analytics"
                      className={({ isActive }) =>
                        `sidebar-nav-item ${isActive ? "active" : ""}`
                      }
                      onClick={() => setMobileOpen(false)}
                      title="Utilization Analytics Engine"
                    >
                      <span className="nav-icon" aria-hidden="true">
                        <FiBarChart2 size={17} />
                      </span>
                      <span className="nav-text">Analytics Engine</span>
                    </NavLink>

                    <NavLink
                      to="/dashboard/sharing/analytics"
                      className={({ isActive }) =>
                        `sidebar-nav-item ${isActive ? "active" : ""}`
                      }
                      onClick={() => setMobileOpen(false)}
                      title="Inter-Institution Sharing Analytics"
                    >
                      <span className="nav-icon" aria-hidden="true">
                        <FiTrendingUp size={17} />
                      </span>
                      <span className="nav-text">Sharing Analytics</span>
                    </NavLink>
                  </>
                )}
              </>
            ) : (
              /* Fallback Generic Roles */
              <>
                <p className="sidebar-section-label">WORKSPACE</p>

                <NavLink
                  to="/dashboard/utilization"
                  className={({ isActive }) =>
                    `sidebar-nav-item ${isActive ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                  title="Live Utilization"
                >
                  <span className="nav-icon" aria-hidden="true">
                    <FiActivity size={17} />
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
                  <span className="nav-icon" aria-hidden="true">
                    <FiUser size={17} />
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
                  <span className="nav-icon" aria-hidden="true">
                    <FiSettings size={17} />
                  </span>
                  <span className="nav-text">Account Settings</span>
                </NavLink>
              </>
            )}
          </nav>
        </aside>

        {/* Main Content Body */}
        <main className="dashboard-main" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default DashboardLayout;
