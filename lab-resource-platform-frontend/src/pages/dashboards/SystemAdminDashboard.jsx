import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";
import DashboardHeader from "../../components/dashboard/DashboardHeader";
import DashboardKpiCard from "../../components/dashboard/DashboardKpiCard";
import DashboardSection from "../../components/dashboard/DashboardSection";
import {
  FiHome,
  FiLayers,
  FiClipboard,
  FiCalendar,
  FiUsers,
  FiShield,
  FiActivity,
  FiCheckCircle,
  FiArrowRight,
  FiAlertCircle,
} from "react-icons/fi";

const ROLE_LABELS = {
  LAB_TECHNICIAN: "Lab Technician",
  LAB_MANAGER: "Lab Manager",
  DEPARTMENT_HEAD: "Department Head",
  INSTITUTION_ADMINISTRATOR: "Institution Admin",
  RESEARCHER_STUDENT: "Student Researcher",
};

export default function SystemAdminDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [pendingRequests, setPendingRequests] = useState([]);
  const [stats, setStats] = useState({
    collegesCount: 0,
    equipmentsCount: 0,
    pendingRequestsCount: 0,
    bookingsCount: 0,
    usersCount: 0,
  });

  const loadStats = useCallback(async () => {
    setLoading(true);
    try {
      const [collegesRes, equipRes, reqRes, bookingsRes, usersRes] = await Promise.allSettled([
        api.get("/api/colleges"),
        api.get("/api/equipment"),
        api.get("/api/admin/technician-requests/pending"),
        api.get("/api/admin/bookings"),
        api.get("/api/admin/users"),
      ]);

      const colleges = collegesRes.status === "fulfilled" && Array.isArray(collegesRes.value?.data) ? collegesRes.value.data : [];
      const equips = equipRes.status === "fulfilled" && Array.isArray(equipRes.value?.data) ? equipRes.value.data : [];
      const requests = reqRes.status === "fulfilled" && Array.isArray(reqRes.value?.data) ? reqRes.value.data : [];
      const bookings = bookingsRes.status === "fulfilled" && Array.isArray(bookingsRes.value?.data) ? bookingsRes.value.data : [];
      const users = usersRes.status === "fulfilled" && Array.isArray(usersRes.value?.data) ? usersRes.value.data : [];

      setPendingRequests(requests);
      setStats({
        collegesCount: colleges.length,
        equipmentsCount: equips.length,
        pendingRequestsCount: requests.length,
        bookingsCount: bookings.length,
        usersCount: users.length,
      });
    } catch (err) {
      console.error("Failed to load platform admin metrics", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const formatRoleLabel = (role) => ROLE_LABELS[role] || (role ? role.replaceAll("_", " ") : "Technician");

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="System Administrator"
        institutionName="Platform Multi-Tenant Governance"
        title="Central Platform Administration"
        subtitle="Manage cross-institutional equipment, role elevation applications, college bookings, and user accounts."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link
              to="/dashboard/admin/role-requests"
              className="btn-primary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiClipboard size={15} />
              <span>Review Role Requests</span>
            </Link>
            <Link
              to="/dashboard/admin/accounts"
              className="btn-secondary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiUsers size={15} />
              <span>User Accounts</span>
            </Link>
          </div>
        }
      />

      {/* Pending Role Elevation Alert Banner */}
      {stats.pendingRequestsCount > 0 && (
        <div className="dashboard-alert-banner warning">
          <div className="dashboard-alert-left">
            <span className="dashboard-alert-icon">
              <FiAlertCircle size={20} color="var(--status-warning-dot)" />
            </span>
            <div>
              <strong>Action Required: {stats.pendingRequestsCount} Pending Role Access Request{stats.pendingRequestsCount > 1 ? "s" : ""}</strong>
              <div style={{ fontSize: "12.5px", marginTop: "2px" }}>
                Faculty and laboratory technicians are awaiting System Administrator verification and role assignment.
              </div>
            </div>
          </div>
          <Link to="/dashboard/admin/role-requests" className="btn-secondary" style={{ whiteSpace: "nowrap" }}>
            Review Applications
          </Link>
        </div>
      )}

      {/* KPI Cards */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Affiliated Colleges"
          value={stats.collegesCount}
          subtitle="Onboarded institutions"
          icon={FiHome}
          variant="primary"
          to="/dashboard/admin/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Global Instruments"
          value={stats.equipmentsCount}
          subtitle="Cross-college assets"
          icon={FiLayers}
          variant="teal"
          to="/dashboard/admin/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Pending Role Requests"
          value={stats.pendingRequestsCount}
          subtitle="Technician &amp; staff applications"
          icon={FiClipboard}
          variant={stats.pendingRequestsCount > 0 ? "warning" : "neutral"}
          to="/dashboard/admin/role-requests"
          loading={loading}
        />
        <DashboardKpiCard
          title="Platform Bookings"
          value={stats.bookingsCount}
          subtitle="Cross-campus reservations"
          icon={FiCalendar}
          variant="neutral"
          to="/dashboard/admin/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="User Accounts"
          value={stats.usersCount}
          subtitle="Registered platform accounts"
          icon={FiUsers}
          variant="neutral"
          to="/dashboard/admin/accounts"
          loading={loading}
        />
      </div>

      {/* Main Grid: Role Requests Table & Quick Actions */}
      <div className="dashboard-main-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Pending Role Elevation Queue */}
          <DashboardSection
            title="Pending Role Elevation Applications"
            subtitle="Faculty and staff requesting elevated permissions across colleges"
            actionText="View All Requests"
            actionTo="/dashboard/admin/role-requests"
            icon={FiClipboard}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading pending role requests...</p>
              </div>
            ) : pendingRequests.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCheckCircle size={30} className="dashboard-empty-icon" style={{ color: "var(--brand-teal)" }} />
                <h4>No Pending Role Applications</h4>
                <p>All laboratory staff and technician access requests have been addressed.</p>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Applicant</th>
                      <th>Requested Role</th>
                      <th>College / Institution</th>
                      <th>Department</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pendingRequests.slice(0, 5).map((req) => (
                      <tr key={req.id}>
                        <td>
                          <strong>{req.fullName || req.email}</strong>
                          <span style={{ display: "block", fontSize: "11px", color: "var(--text-muted)" }}>
                            {req.email}
                          </span>
                        </td>
                        <td>
                          <span className="status-badge status-pending">
                            {formatRoleLabel(req.requestedRole)}
                          </span>
                        </td>
                        <td>{req.institution || "Unspecified"}</td>
                        <td>{req.department || "—"}</td>
                        <td style={{ textAlign: "right" }}>
                          <Link
                            to="/dashboard/admin/role-requests"
                            className="dashboard-section-action-link"
                          >
                            Review
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </DashboardSection>

          {/* Platform Multi-Tenant Infrastructure Summary */}
          <DashboardSection
            title="Platform System Health &amp; Infrastructure"
            subtitle="Multi-tenant status across onboarded universities"
            icon={FiHome}
          >
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "16px" }}>
              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Active Institutions</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-primary)", marginTop: "4px" }}>
                  {stats.collegesCount}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Colleges actively sharing</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Catalog Depth</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-teal)", marginTop: "4px" }}>
                  {stats.equipmentsCount}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Equipment units indexed</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>System Users</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--text-primary)", marginTop: "4px" }}>
                  {stats.usersCount}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Registered research accounts</span>
              </div>
            </div>
          </DashboardSection>
        </div>

        {/* Right Column: Platform Administration Shortcuts */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          <DashboardSection
            title="Administrator Quick Actions"
            subtitle="Platform configuration tools"
            icon={FiShield}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/admin/role-requests" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-warning-bg)", color: "var(--status-warning-text)" }}>
                  <FiClipboard size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Role Elevation Requests</strong>
                  <p>Approve technician &amp; faculty permission upgrades</p>
                </div>
              </Link>

              <Link to="/dashboard/admin/accounts" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiUsers size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Accounts Management</strong>
                  <p>Manage users, reset credentials, assign roles</p>
                </div>
              </Link>

              <Link to="/dashboard/admin/equipments" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiLayers size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>College Equipment Catalog</strong>
                  <p>Filter, monitor and inspect equipment by college</p>
                </div>
              </Link>

              <Link to="/dashboard/admin/bookings" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--surface-subtle)", color: "var(--text-secondary)" }}>
                  <FiCalendar size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>All College Bookings</strong>
                  <p>Monitor reservation volume &amp; check-in audit</p>
                </div>
              </Link>

              <Link to="/dashboard/utilization" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-info-bg)", color: "var(--status-info-text)" }}>
                  <FiActivity size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Live Telemetry Monitoring</strong>
                  <p>Real-time physical sensors &amp; occupancy status</p>
                </div>
              </Link>
            </div>
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
