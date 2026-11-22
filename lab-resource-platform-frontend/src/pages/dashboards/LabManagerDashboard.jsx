import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";
import DashboardHeader from "../../components/dashboard/DashboardHeader";
import DashboardKpiCard from "../../components/dashboard/DashboardKpiCard";
import DashboardSection from "../../components/dashboard/DashboardSection";
import {
  FiLayers,
  FiCalendar,
  FiClock,
  FiBarChart2,
  FiTrendingUp,
  FiActivity,
  FiFileText,
  FiPlus,
  FiCheckCircle,
  FiArrowRight,
  FiShield,
} from "react-icons/fi";

export default function LabManagerDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [equipments, setEquipments] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [utilizationSummary, setUtilizationSummary] = useState(null);
  const [agreements, setAgreements] = useState([]);

  const [stats, setStats] = useState({
    totalEquipments: 0,
    utilizationRate: 0,
    pendingApprovalsCount: 0,
    activeAgreementsCount: 0,
    activeSessions: 0,
    usageMinutesToday: 0,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [equipRes, bookingsRes, utilRes, agreeRes] = await Promise.allSettled([
        api.get("/api/equipment"),
        api.get("/api/bookings"),
        api.get("/api/utilization/summary"),
        api.get("/api/sharing/agreements"),
      ]);

      let equipList = [];
      if (equipRes.status === "fulfilled" && equipRes.value?.data) {
        const d = equipRes.value.data;
        equipList = Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : [];
        setEquipments(equipList);
      }

      let bookList = [];
      if (bookingsRes.status === "fulfilled" && bookingsRes.value?.data) {
        const b = bookingsRes.value.data;
        bookList = Array.isArray(b) ? b : Array.isArray(b?.content) ? b.content : [];
        setBookings(bookList);
      }

      let utilData = null;
      if (utilRes.status === "fulfilled" && utilRes.value?.data) {
        utilData = utilRes.value.data;
        setUtilizationSummary(utilData);
      }

      let agreeList = [];
      if (agreeRes.status === "fulfilled" && agreeRes.value?.data) {
        const a = agreeRes.value.data;
        agreeList = Array.isArray(a) ? a : Array.isArray(a?.content) ? a.content : [];
        setAgreements(agreeList);
      }

      const pending = bookList.filter((b) => b.status === "PENDING_APPROVAL").length;
      const rate = utilData?.overallUtilizationRatePercent ?? 
        (equipList.length > 0 ? ((equipList.filter(e => e.status === 'IN_USE').length / equipList.length) * 100) : 0);

      setStats({
        totalEquipments: equipList.length,
        utilizationRate: Math.round(rate),
        pendingApprovalsCount: pending,
        activeAgreementsCount: agreeList.filter(a => a.status === 'ACTIVE').length || agreeList.length,
        activeSessions: utilData?.activeSessionsCount ?? equipList.filter(e => e.status === 'IN_USE').length,
        usageMinutesToday: utilData?.totalUsageMinutesToday ?? 0,
      });
    } catch (err) {
      console.error("Failed to load lab manager dashboard data", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const pendingApprovals = bookings.filter((b) => b.status === "PENDING_APPROVAL");

  const formatDateTime = (dateStr, timeStr) => {
    if (!dateStr) return "—";
    try {
      const date = new Date(dateStr);
      const formatted = date.toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
      });
      return timeStr ? `${formatted} (${timeStr})` : formatted;
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="Lab Manager"
        institutionName={user?.collegeName || user?.institutionName || "Laboratory Management Division"}
        title="Facility Management &amp; Resource Optimization"
        subtitle="Manage equipment lifecycle, monitor facility utilization metrics, and coordinate laboratory reservations."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link to="/dashboard/equipment/create" className="btn-primary" style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
              <FiPlus size={15} />
              <span>Add Equipment</span>
            </Link>
            <Link to="/dashboard/analytics" className="btn-secondary" style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
              <FiBarChart2 size={15} />
              <span>Analytics Engine</span>
            </Link>
          </div>
        }
      />

      {/* KPI Row */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Facility Instruments"
          value={stats.totalEquipments}
          subtitle="Managed laboratory equipment"
          icon={FiLayers}
          variant="primary"
          to="/dashboard/lab-manager/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Utilization Rate"
          value={stats.utilizationRate}
          unit="%"
          subtitle="Fleet operating capacity"
          icon={FiBarChart2}
          variant="teal"
          to="/dashboard/analytics"
          loading={loading}
        />
        <DashboardKpiCard
          title="Pending Approvals"
          value={stats.pendingApprovalsCount}
          subtitle="Queue requiring decision"
          icon={FiClock}
          variant={stats.pendingApprovalsCount > 0 ? "warning" : "neutral"}
          to="/dashboard/lab-manager/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Sharing Agreements"
          value={stats.activeAgreementsCount}
          subtitle="Inter-institutional contracts"
          icon={FiFileText}
          variant="neutral"
          to="/dashboard/sharing/agreements"
          loading={loading}
        />
      </div>

      {/* Main Grid: Approvals Queue & Facility Performance */}
      <div className="dashboard-main-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Pending Approvals Table */}
          <DashboardSection
            title="Reservation Approval Queue"
            subtitle="Student &amp; researcher bookings awaiting approval"
            actionText="View All Bookings"
            actionTo="/dashboard/lab-manager/bookings"
            icon={FiClock}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading approval queue...</p>
              </div>
            ) : pendingApprovals.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCheckCircle size={30} className="dashboard-empty-icon" style={{ color: "var(--brand-teal)" }} />
                <h4>No Pending Approvals</h4>
                <p>All laboratory reservations are up to date.</p>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Researcher</th>
                      <th>Instrument</th>
                      <th>Requested Slot</th>
                      <th>Purpose</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pendingApprovals.slice(0, 5).map((booking) => (
                      <tr key={booking.id}>
                        <td>
                          <strong>{booking.userName || booking.userEmail || `User #${booking.userId}`}</strong>
                        </td>
                        <td>{booking.equipmentName || `Item #${booking.equipmentId}`}</td>
                        <td>{formatDateTime(booking.startDate, booking.startTime)}</td>
                        <td style={{ maxWidth: "160px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {booking.purpose || "Academic Research"}
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <Link
                            to="/dashboard/lab-manager/bookings"
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

          {/* Utilization Overview Card */}
          <DashboardSection
            title="Facility Operational Performance"
            subtitle="Real-time telemetry and throughput summary"
            actionText="Detailed Analytics"
            actionTo="/dashboard/analytics"
            icon={FiActivity}
          >
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "16px" }}>
              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Active Sessions</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-teal)", marginTop: "4px" }}>
                  {stats.activeSessions}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Running right now</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Usage Today</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-primary)", marginTop: "4px" }}>
                  {Math.round(stats.usageMinutesToday / 60)} hrs
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>{stats.usageMinutesToday} total minutes logged</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Fleet Available</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--status-success-text)", marginTop: "4px" }}>
                  {equipments.filter(e => e.status === "AVAILABLE").length}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Ready for reservations</span>
              </div>
            </div>
          </DashboardSection>
        </div>

        {/* Right Column: Manager Tools & Sharing Contracts */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Quick Management Tools */}
          <DashboardSection
            title="Management Shortcuts"
            subtitle="Administrative actions"
            icon={FiShield}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/equipment/create" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiPlus size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Register Equipment</strong>
                  <p>Add new instrument, set rates &amp; specs</p>
                </div>
              </Link>

              <Link to="/dashboard/lab-manager/bookings" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-warning-bg)", color: "var(--status-warning-text)" }}>
                  <FiCalendar size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Manage Bookings</strong>
                  <p>Approve or reschedule reservations</p>
                </div>
              </Link>

              <Link to="/dashboard/analytics" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiBarChart2 size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Analytics Engine</strong>
                  <p>Peak utilization hours &amp; downtime reports</p>
                </div>
              </Link>

              <Link to="/dashboard/sharing/agreements" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--surface-subtle)", color: "var(--text-secondary)" }}>
                  <FiFileText size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Sharing Contracts</strong>
                  <p>Inter-institution partner agreements</p>
                </div>
              </Link>
            </div>
          </DashboardSection>

          {/* Active Sharing Agreements Preview */}
          <DashboardSection
            title="Inter-Institution Contracts"
            subtitle="Active resource exchange agreements"
            actionText="View Contracts"
            actionTo="/dashboard/sharing/agreements"
            icon={FiFileText}
          >
            {agreements.length === 0 ? (
              <div className="dashboard-empty-state" style={{ padding: "16px" }}>
                <p>No active inter-institution agreements registered.</p>
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                {agreements.slice(0, 3).map((ag) => (
                  <div
                    key={ag.id}
                    style={{
                      padding: "10px 12px",
                      background: "var(--surface-subtle)",
                      borderRadius: "var(--radius-sm)",
                      fontSize: "12.5px",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", fontWeight: 600, color: "var(--text-primary)" }}>
                      <span>{ag.partnerInstitutionName || `Partner #${ag.partnerInstitutionId || ag.id}`}</span>
                      <span className="status-badge status-confirmed">{ag.status || "Active"}</span>
                    </div>
                    <div style={{ color: "var(--text-muted)", fontSize: "11.5px", marginTop: "2px" }}>
                      Agreed Rate: ₹{ag.discountRatePercent ?? ag.hourlyRate ?? "Standard"}/hr
                    </div>
                  </div>
                ))}
              </div>
            )}
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
