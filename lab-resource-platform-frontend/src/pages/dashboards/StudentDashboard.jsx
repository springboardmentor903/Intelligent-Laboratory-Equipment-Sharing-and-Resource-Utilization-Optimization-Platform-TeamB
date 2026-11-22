import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";
import DashboardHeader from "../../components/dashboard/DashboardHeader";
import DashboardKpiCard from "../../components/dashboard/DashboardKpiCard";
import DashboardSection from "../../components/dashboard/DashboardSection";
import {
  FiCalendar,
  FiClock,
  FiCheckCircle,
  FiAlertCircle,
  FiLayers,
  FiArrowRight,
  FiActivity,
  FiList,
  FiPlay,
} from "react-icons/fi";

export default function StudentDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [bookings, setBookings] = useState([]);
  const [waitlist, setWaitlist] = useState([]);
  const [stats, setStats] = useState({
    upcoming: 0,
    active: 0,
    pending: 0,
    waitlistCount: 0,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [bookingsRes, waitlistRes] = await Promise.allSettled([
        api.get("/api/bookings/my?page=0&size=50&sortBy=createdAt&direction=desc"),
        api.get("/api/waitlist/my"),
      ]);

      let bookingList = [];
      if (bookingsRes.status === "fulfilled" && bookingsRes.value?.data) {
        const d = bookingsRes.value.data;
        bookingList = Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : [];
        setBookings(bookingList);
      }

      let waitlistList = [];
      if (waitlistRes.status === "fulfilled" && waitlistRes.value?.data) {
        const w = waitlistRes.value.data;
        waitlistList = Array.isArray(w) ? w : Array.isArray(w?.content) ? w.content : [];
        setWaitlist(waitlistList);
      }

      const activeCount = bookingList.filter((b) => b.status === "IN_USE").length;
      const confirmedCount = bookingList.filter((b) => b.status === "CONFIRMED").length;
      const pendingCount = bookingList.filter((b) => b.status === "PENDING_APPROVAL").length;

      setStats({
        upcoming: confirmedCount,
        active: activeCount,
        pending: pendingCount,
        waitlistCount: waitlistList.length,
      });
    } catch (err) {
      console.error("Failed to load student dashboard data", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Find currently active session or next upcoming booking
  const activeBooking = bookings.find((b) => b.status === "IN_USE");
  const nextBooking = bookings.find((b) => b.status === "CONFIRMED");

  // Helper to format date/time in IST
  const formatDateTime = (dateStr, timeStr) => {
    if (!dateStr) return "—";
    try {
      const date = new Date(dateStr);
      const formattedDate = date.toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
      });
      return timeStr ? `${formattedDate} • ${timeStr}` : formattedDate;
    } catch {
      return dateStr;
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "CONFIRMED":
        return <span className="status-badge status-confirmed">Confirmed</span>;
      case "IN_USE":
        return <span className="status-badge status-in_use">In Use</span>;
      case "PENDING_APPROVAL":
        return <span className="status-badge status-pending">Pending</span>;
      case "COMPLETED":
        return <span className="status-badge status-completed">Completed</span>;
      case "CANCELLED":
      case "REJECTED":
        return <span className="status-badge status-cancelled">Cancelled</span>;
      default:
        return <span className="status-badge status-idle">{status || "Unknown"}</span>;
    }
  };

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="Researcher / Student"
        institutionName={user?.collegeName || user?.institutionName || "Academic Laboratory"}
        title={`Welcome back, ${user?.fullName || user?.name || "Researcher"}`}
        subtitle="Manage your equipment reservations, monitor active sessions, and check waitlist queues."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link to="/dashboard/student/equipments" className="btn-primary" style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
              <FiLayers size={15} />
              <span>Browse Catalog</span>
            </Link>
          </div>
        }
      />

      {/* Active Session High-Priority Banner */}
      {activeBooking && (
        <div className="dashboard-alert-banner warning">
          <div className="dashboard-alert-left">
            <span className="dashboard-alert-icon">
              <FiPlay size={20} color="var(--status-warning-dot)" />
            </span>
            <div>
              <strong>Active Session in Progress: {activeBooking.equipmentName || "Lab Instrument"}</strong>
              <div style={{ fontSize: "12.5px", marginTop: "2px" }}>
                Session started at {activeBooking.startTime || "active slot"}. Remember to check out once your research is completed.
              </div>
            </div>
          </div>
          <Link to="/dashboard/student/bookings" className="btn-secondary" style={{ whiteSpace: "nowrap" }}>
            View Booking
          </Link>
        </div>
      )}

      {/* KPI Summary Cards */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Upcoming Reservations"
          value={stats.upcoming}
          subtitle="Confirmed & scheduled slots"
          icon={FiCalendar}
          variant="primary"
          to="/dashboard/student/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Active In-Use Sessions"
          value={stats.active}
          subtitle="Currently running instruments"
          icon={FiActivity}
          variant={stats.active > 0 ? "warning" : "teal"}
          to="/dashboard/student/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Pending Approvals"
          value={stats.pending}
          subtitle="Under technician/manager review"
          icon={FiClock}
          variant={stats.pending > 0 ? "warning" : "neutral"}
          to="/dashboard/student/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Waitlist Position"
          value={stats.waitlistCount}
          subtitle="Active queue notifications"
          icon={FiList}
          variant="neutral"
          to="/dashboard/student/bookings"
          loading={loading}
        />
      </div>

      {/* Main Grid: Next Upcoming & Recent Bookings */}
      <div className="dashboard-main-grid">
        {/* Left Column: Recent Bookings Table */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          <DashboardSection
            title="Recent Reservations"
            subtitle="Latest bookings across research departments"
            actionText="View All Bookings"
            actionTo="/dashboard/student/bookings"
            icon={FiCalendar}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading your reservations...</p>
              </div>
            ) : bookings.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCalendar size={32} className="dashboard-empty-icon" />
                <h4>No Reservations Found</h4>
                <p>You have not reserved any laboratory instruments yet.</p>
                <div style={{ marginTop: "12px" }}>
                  <Link to="/dashboard/student/equipments" className="btn-primary" style={{ display: "inline-flex" }}>
                    Explore Available Equipment
                  </Link>
                </div>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Instrument</th>
                      <th>Date &amp; Slot</th>
                      <th>Status</th>
                      <th>Purpose</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bookings.slice(0, 5).map((booking) => (
                      <tr key={booking.id}>
                        <td>
                          <strong>{booking.equipmentName || `Equipment #${booking.equipmentId}`}</strong>
                        </td>
                        <td>
                          {formatDateTime(booking.startDate, booking.startTime)}
                        </td>
                        <td>{getStatusBadge(booking.status)}</td>
                        <td style={{ maxWidth: "200px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {booking.purpose || "Academic Research"}
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <Link
                            to="/dashboard/student/bookings"
                            className="dashboard-section-action-link"
                          >
                            Details
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </DashboardSection>

          {/* Waitlist Queue Overview if any */}
          {waitlist.length > 0 && (
            <DashboardSection
              title="Active Waitlist Requests"
              subtitle="Instruments you are queued for when current bookings end"
              actionText="Manage Queue"
              actionTo="/dashboard/student/bookings"
              icon={FiClock}
              noPadding
            >
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Equipment</th>
                      <th>Queue Position</th>
                      <th>Requested Date</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {waitlist.slice(0, 4).map((item, idx) => (
                      <tr key={item.id || idx}>
                        <td><strong>{item.equipmentName || `Item #${item.equipmentId}`}</strong></td>
                        <td>
                          <span style={{ fontWeight: 600, color: "var(--brand-primary)" }}>
                            #{item.queuePosition || item.position || idx + 1}
                          </span> in queue
                        </td>
                        <td>{formatDateTime(item.requestedDate)}</td>
                        <td>
                          <span className="status-badge status-pending">Queued</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </DashboardSection>
          )}
        </div>

        {/* Right Column: Quick Status & Next Up */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Next Scheduled Reservation Card */}
          <DashboardSection
            title="Next Reservation"
            subtitle="Upcoming confirmed laboratory access"
            icon={FiCheckCircle}
          >
            {nextBooking ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                  <div>
                    <h4 style={{ margin: "0 0 4px 0", fontSize: "15px", color: "var(--text-primary)" }}>
                      {nextBooking.equipmentName || "Laboratory Instrument"}
                    </h4>
                    <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      Ref ID: #{nextBooking.id}
                    </span>
                  </div>
                  {getStatusBadge(nextBooking.status)}
                </div>

                <div style={{ background: "var(--surface-subtle)", padding: "12px", borderRadius: "var(--radius-sm)", fontSize: "13px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "4px" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Date:</span>
                    <strong>{formatDateTime(nextBooking.startDate)}</strong>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "4px" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Time Slot:</span>
                    <strong>{nextBooking.startTime || "—"} to {nextBooking.endTime || "—"}</strong>
                  </div>
                  {nextBooking.totalCost != null && (
                    <div style={{ display: "flex", justifyContent: "space-between" }}>
                      <span style={{ color: "var(--text-secondary)" }}>Estimated Cost:</span>
                      <strong>₹{nextBooking.totalCost}</strong>
                    </div>
                  )}
                </div>

                <Link
                  to="/dashboard/student/bookings"
                  className="btn-secondary"
                  style={{ width: "100%", justifyContent: "center", display: "inline-flex", alignItems: "center", gap: "6px" }}
                >
                  <span>View Reservation Details</span>
                  <FiArrowRight size={14} />
                </Link>
              </div>
            ) : (
              <div className="dashboard-empty-state" style={{ padding: "var(--space-4)" }}>
                <FiClock size={28} className="dashboard-empty-icon" />
                <p>No confirmed reservations scheduled.</p>
                <div style={{ marginTop: "10px" }}>
                  <Link to="/dashboard/student/equipments" className="btn-primary" style={{ fontSize: "12.5px" }}>
                    Reserve an Instrument
                  </Link>
                </div>
              </div>
            )}
          </DashboardSection>

          {/* Quick Shortcuts */}
          <DashboardSection
            title="Fast Actions"
            subtitle="Common researcher tasks"
            icon={FiLayers}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/student/equipments" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiLayers size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Browse Catalog</strong>
                  <p>Search instruments across departments &amp; colleges</p>
                </div>
              </Link>

              <Link to="/dashboard/student/bookings" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-success-bg)", color: "var(--status-success-text)" }}>
                  <FiCalendar size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Booking History</strong>
                  <p>Check approval statuses &amp; waitlist positions</p>
                </div>
              </Link>

              <Link to="/dashboard/utilization" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiActivity size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Live Telemetry</strong>
                  <p>Check real-time instrument occupancy &amp; availability</p>
                </div>
              </Link>
            </div>
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
