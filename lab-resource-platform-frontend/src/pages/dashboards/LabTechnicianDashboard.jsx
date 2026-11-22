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
  FiTool,
  FiActivity,
  FiPlus,
  FiCheckCircle,
  FiAlertTriangle,
  FiArrowRight,
  FiFilter,
} from "react-icons/fi";

export default function LabTechnicianDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [equipments, setEquipments] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [utilizationSummary, setUtilizationSummary] = useState(null);

  const [stats, setStats] = useState({
    totalEquipments: 0,
    availableCount: 0,
    inUseCount: 0,
    maintenanceCount: 0,
    pendingApprovalsCount: 0,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [equipRes, bookingsRes, utilRes] = await Promise.allSettled([
        api.get("/api/equipment"),
        api.get("/api/bookings"),
        api.get("/api/utilization/summary"),
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

      if (utilRes.status === "fulfilled" && utilRes.value?.data) {
        setUtilizationSummary(utilRes.value.data);
      }

      const available = equipList.filter((e) => e.status === "AVAILABLE").length;
      const inUse = equipList.filter((e) => e.status === "IN_USE").length;
      const maintenance = equipList.filter((e) => e.status === "UNDER_MAINTENANCE" || e.status === "MAINTENANCE").length;
      const pending = bookList.filter((b) => b.status === "PENDING_APPROVAL").length;

      setStats({
        totalEquipments: equipList.length,
        availableCount: available,
        inUseCount: inUse,
        maintenanceCount: maintenance,
        pendingApprovalsCount: pending,
      });
    } catch (err) {
      console.error("Failed to load technician dashboard metrics", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Pending reservations requiring technician approval
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

  const getStatusBadge = (status) => {
    switch (status) {
      case "AVAILABLE":
        return <span className="status-badge status-available">Available</span>;
      case "IN_USE":
        return <span className="status-badge status-in_use">In Use</span>;
      case "UNDER_MAINTENANCE":
      case "MAINTENANCE":
        return <span className="status-badge status-maintenance">Maintenance</span>;
      default:
        return <span className="status-badge status-idle">{status || "Unknown"}</span>;
    }
  };

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="Lab Technician"
        institutionName={user?.collegeName || user?.institutionName || "Laboratory Facility"}
        title="Laboratory Operations Hub"
        subtitle="Manage equipment operational readiness, fulfill student reservation approvals, and monitor live status."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link
              to="/dashboard/equipment/create"
              className="btn-primary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiPlus size={15} />
              <span>Register Instrument</span>
            </Link>
          </div>
        }
      />

      {/* Attention banner if pending approvals exist */}
      {stats.pendingApprovalsCount > 0 && (
        <div className="dashboard-alert-banner warning">
          <div className="dashboard-alert-left">
            <span className="dashboard-alert-icon">
              <FiClock size={20} color="var(--status-warning-dot)" />
            </span>
            <div>
              <strong>Action Required: {stats.pendingApprovalsCount} Booking Request{stats.pendingApprovalsCount > 1 ? "s" : ""} Awaiting Review</strong>
              <div style={{ fontSize: "12.5px", marginTop: "2px" }}>
                Students have requested equipment slots that require technical sign-off before usage can begin.
              </div>
            </div>
          </div>
          <Link to="/dashboard/technician/bookings" className="btn-secondary" style={{ whiteSpace: "nowrap" }}>
            Review Approvals
          </Link>
        </div>
      )}

      {/* KPI Row */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Managed Instruments"
          value={stats.totalEquipments}
          subtitle="Assigned lab inventory"
          icon={FiLayers}
          variant="primary"
          to="/dashboard/technician/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Pending Approvals"
          value={stats.pendingApprovalsCount}
          subtitle="Reservations to review"
          icon={FiClock}
          variant={stats.pendingApprovalsCount > 0 ? "warning" : "neutral"}
          to="/dashboard/technician/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Active Sessions"
          value={stats.inUseCount}
          subtitle="Currently utilized units"
          icon={FiActivity}
          variant={stats.inUseCount > 0 ? "teal" : "neutral"}
          to="/dashboard/utilization"
          loading={loading}
        />
        <DashboardKpiCard
          title="Under Maintenance"
          value={stats.maintenanceCount}
          subtitle="Down for service/calibration"
          icon={FiTool}
          variant={stats.maintenanceCount > 0 ? "danger" : "success"}
          to="/dashboard/technician/equipments"
          loading={loading}
        />
      </div>

      {/* Main Grid: Approvals Queue & Inventory Health */}
      <div className="dashboard-main-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Pending Approvals Table */}
          <DashboardSection
            title="Pending Reservation Queue"
            subtitle="Recent requests awaiting technician verification"
            actionText="All Bookings"
            actionTo="/dashboard/technician/bookings"
            icon={FiClock}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading pending reservations...</p>
              </div>
            ) : pendingApprovals.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCheckCircle size={30} className="dashboard-empty-icon" style={{ color: "var(--brand-teal)" }} />
                <h4>All Clear — No Pending Approvals</h4>
                <p>All student reservation requests have been processed.</p>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Requested By</th>
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
                            to="/dashboard/technician/bookings"
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

          {/* Laboratory Equipment Live Inventory Status */}
          <DashboardSection
            title="Assigned Laboratory Inventory"
            subtitle="Operational readiness status of registered instruments"
            actionText="Manage Equipments"
            actionTo="/dashboard/technician/equipments"
            icon={FiLayers}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading equipment catalog...</p>
              </div>
            ) : equipments.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiLayers size={30} className="dashboard-empty-icon" />
                <h4>No Equipment Registered</h4>
                <p>Add your first laboratory instrument to start tracking utilization.</p>
                <div style={{ marginTop: "12px" }}>
                  <Link to="/dashboard/equipment/create" className="btn-primary">
                    Register New Instrument
                  </Link>
                </div>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Instrument Name</th>
                      <th>Category</th>
                      <th>Location / Room</th>
                      <th>Status</th>
                      <th style={{ textAlign: "right" }}>Hourly Rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {equipments.slice(0, 6).map((eq) => (
                      <tr key={eq.id}>
                        <td>
                          <strong>{eq.name}</strong>
                          {eq.modelNumber && (
                            <span style={{ display: "block", fontSize: "11px", color: "var(--text-muted)" }}>
                              Mod: {eq.modelNumber}
                            </span>
                          )}
                        </td>
                        <td>{eq.category || "General Lab"}</td>
                        <td>{eq.labLocation || eq.location || "Main Lab"}</td>
                        <td>{getStatusBadge(eq.status)}</td>
                        <td style={{ textAlign: "right", fontWeight: 600 }}>
                          ₹{eq.hourlyRate ?? 0}/hr
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </DashboardSection>
        </div>

        {/* Right Column: Status Summary & Quick Actions */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Operational Status Breakdown */}
          <DashboardSection
            title="Inventory Health"
            subtitle="Live status distribution"
            icon={FiActivity}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 12px", background: "var(--surface-subtle)", borderRadius: "var(--radius-sm)" }}>
                <span style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px" }}>
                  <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--status-success-dot)" }} />
                  Available for Booking
                </span>
                <strong>{stats.availableCount}</strong>
              </div>

              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 12px", background: "var(--surface-subtle)", borderRadius: "var(--radius-sm)" }}>
                <span style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px" }}>
                  <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--brand-teal)" }} />
                  In Active Use
                </span>
                <strong>{stats.inUseCount}</strong>
              </div>

              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 12px", background: "var(--surface-subtle)", borderRadius: "var(--radius-sm)" }}>
                <span style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "13px" }}>
                  <span style={{ width: "8px", height: "8px", borderRadius: "50%", background: "var(--status-error-dot)" }} />
                  Under Maintenance
                </span>
                <strong>{stats.maintenanceCount}</strong>
              </div>

              <Link
                to="/dashboard/utilization"
                className="btn-secondary"
                style={{ width: "100%", justifyContent: "center", marginTop: "4px", display: "inline-flex", alignItems: "center", gap: "6px" }}
              >
                <span>Open Live Telemetry</span>
                <FiArrowRight size={14} />
              </Link>
            </div>
          </DashboardSection>

          {/* Quick Technician Tools */}
          <DashboardSection
            title="Technician Tools"
            subtitle="Operational workflows"
            icon={FiTool}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/equipment/create" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiPlus size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Add New Instrument</strong>
                  <p>Register equipment with specs, manual &amp; hourly rate</p>
                </div>
              </Link>

              <Link to="/dashboard/technician/bookings" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-warning-bg)", color: "var(--status-warning-text)" }}>
                  <FiCalendar size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Bookings &amp; Schedules</strong>
                  <p>Process approvals and manage time slots</p>
                </div>
              </Link>

              <Link to="/dashboard/utilization" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiActivity size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Check-In / Check-Out</strong>
                  <p>Monitor physical sensor logs and active timers</p>
                </div>
              </Link>
            </div>
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
