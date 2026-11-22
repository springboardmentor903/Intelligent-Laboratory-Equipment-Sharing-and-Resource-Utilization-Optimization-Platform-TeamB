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
  FiShare2,
  FiFileText,
  FiBarChart2,
  FiCheckCircle,
  FiArrowRight,
  FiTrendingUp,
  FiCompass,
} from "react-icons/fi";

export default function DepartmentHeadDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [equipments, setEquipments] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [agreements, setAgreements] = useState([]);
  const [accessRequests, setAccessRequests] = useState([]);

  const [stats, setStats] = useState({
    totalEquipments: 0,
    activeBookingsCount: 0,
    interInstitutionRequestsCount: 0,
    activeAgreementsCount: 0,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [equipRes, bookingsRes, agreeRes, reqRes] = await Promise.allSettled([
        api.get("/api/equipment"),
        api.get("/api/bookings"),
        api.get("/api/sharing/agreements"),
        api.get("/api/sharing/access-requests"),
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

      let agreeList = [];
      if (agreeRes.status === "fulfilled" && agreeRes.value?.data) {
        const a = agreeRes.value.data;
        agreeList = Array.isArray(a) ? a : Array.isArray(a?.content) ? a.content : [];
        setAgreements(agreeList);
      }

      let reqList = [];
      if (reqRes.status === "fulfilled" && reqRes.value?.data) {
        const r = reqRes.value.data;
        reqList = Array.isArray(r) ? r : Array.isArray(r?.content) ? r.content : [];
        setAccessRequests(reqList);
      }

      const activeBookings = bookList.filter(
        (b) => b.status === "CONFIRMED" || b.status === "IN_USE"
      ).length;

      setStats({
        totalEquipments: equipList.length,
        activeBookingsCount: activeBookings,
        interInstitutionRequestsCount: reqList.filter((r) => r.status === "PENDING").length || reqList.length,
        activeAgreementsCount: agreeList.filter((a) => a.status === "ACTIVE").length || agreeList.length,
      });
    } catch (err) {
      console.error("Failed to load department head dashboard data", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

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
      case "CONFIRMED":
        return <span className="status-badge status-confirmed">Confirmed</span>;
      case "IN_USE":
        return <span className="status-badge status-in_use">In Use</span>;
      case "PENDING_APPROVAL":
      case "PENDING":
        return <span className="status-badge status-pending">Pending</span>;
      case "COMPLETED":
        return <span className="status-badge status-completed">Completed</span>;
      default:
        return <span className="status-badge status-idle">{status || "Unknown"}</span>;
    }
  };

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="Department Head"
        institutionName={user?.collegeName || user?.institutionName || "Academic Department"}
        title="Department Resource &amp; Research Oversight"
        subtitle="Coordinate departmental laboratory infrastructure, request inter-institution equipment, and manage agreements."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link
              to="/dashboard/sharing/discovery"
              className="btn-primary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiCompass size={15} />
              <span>Explore Partner Equipment</span>
            </Link>
            <Link
              to="/dashboard/sharing/agreements"
              className="btn-secondary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiFileText size={15} />
              <span>Sharing Agreements</span>
            </Link>
          </div>
        }
      />

      {/* KPI Cards */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Department Instruments"
          value={stats.totalEquipments}
          subtitle="Internal departmental assets"
          icon={FiLayers}
          variant="primary"
          to="/dashboard/department-head/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Active Research Bookings"
          value={stats.activeBookingsCount}
          subtitle="Currently running &amp; scheduled"
          icon={FiCalendar}
          variant="teal"
          to="/dashboard/department-head/bookings"
          loading={loading}
        />
        <DashboardKpiCard
          title="Inter-Institution Requests"
          value={stats.interInstitutionRequestsCount}
          subtitle="External access requests"
          icon={FiShare2}
          variant={stats.interInstitutionRequestsCount > 0 ? "warning" : "neutral"}
          to="/dashboard/sharing/agreements"
          loading={loading}
        />
        <DashboardKpiCard
          title="Sharing Agreements"
          value={stats.activeAgreementsCount}
          subtitle="Institutional partnerships"
          icon={FiFileText}
          variant="neutral"
          to="/dashboard/sharing/agreements"
          loading={loading}
        />
      </div>

      {/* Main Grid: Inter-Institution Discovery & Recent Bookings */}
      <div className="dashboard-main-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Inter-Institution Sharing Banner / Callout */}
          <div
            style={{
              background: "linear-gradient(135deg, #0F172A 0%, #1E293B 100%)",
              color: "#F8FAFC",
              borderRadius: "var(--radius-md)",
              padding: "24px",
              boxShadow: "var(--shadow-card)",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "16px",
            }}
          >
            <div style={{ maxWidth: "560px" }}>
              <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "#60A5FA" }}>
                INTER-INSTITUTION RESOURCE ACCESS
              </span>
              <h3 style={{ margin: "4px 0 8px 0", fontSize: "18px", color: "#FFFFFF" }}>
                Need Specialized Instruments Outside Your Campus?
              </h3>
              <p style={{ margin: 0, fontSize: "13px", color: "#94A3B8", lineHeight: 1.5 }}>
                Department Heads have direct authority to browse high-end equipment (TEM, NMR, X-Ray) listed by accredited partner colleges and initiate inter-institutional access requests.
              </p>
            </div>
            <Link
              to="/dashboard/sharing/discovery"
              className="btn-primary"
              style={{
                background: "#2563EB",
                border: "none",
                color: "#FFFFFF",
                whiteSpace: "nowrap",
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
              }}
            >
              <FiCompass size={16} />
              <span>Request Partner Equipment</span>
            </Link>
          </div>

          {/* Department Bookings Table */}
          <DashboardSection
            title="Department Research Bookings"
            subtitle="Overview of laboratory usage across researchers"
            actionText="View All Bookings"
            actionTo="/dashboard/department-head/bookings"
            icon={FiCalendar}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading bookings...</p>
              </div>
            ) : bookings.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCalendar size={30} className="dashboard-empty-icon" />
                <h4>No Department Bookings Recorded</h4>
                <p>Reservations placed by department researchers will appear here.</p>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Researcher</th>
                      <th>Instrument</th>
                      <th>Scheduled Slot</th>
                      <th>Status</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bookings.slice(0, 5).map((booking) => (
                      <tr key={booking.id}>
                        <td>
                          <strong>{booking.userName || booking.userEmail || `User #${booking.userId}`}</strong>
                        </td>
                        <td>{booking.equipmentName || `Equipment #${booking.equipmentId}`}</td>
                        <td>{formatDateTime(booking.startDate, booking.startTime)}</td>
                        <td>{getStatusBadge(booking.status)}</td>
                        <td style={{ textAlign: "right" }}>
                          <Link
                            to="/dashboard/department-head/bookings"
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
        </div>

        {/* Right Column: Fast Tools & Sharing Agreements */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Quick Shortcuts */}
          <DashboardSection
            title="Strategic Actions"
            subtitle="Departmental governance"
            icon={FiTrendingUp}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/sharing/discovery" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiCompass size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Inter-Institution Discovery</strong>
                  <p>Request equipment from partner institutions</p>
                </div>
              </Link>

              <Link to="/dashboard/sharing/agreements" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiFileText size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Sharing Contracts</strong>
                  <p>Review agreements &amp; institutional approvals</p>
                </div>
              </Link>

              <Link to="/dashboard/department-head/equipments" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--surface-subtle)", color: "var(--text-secondary)" }}>
                  <FiLayers size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Department Inventory</strong>
                  <p>View instrument specifications &amp; health</p>
                </div>
              </Link>

              <Link to="/dashboard/analytics" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-info-bg)", color: "var(--status-info-text)" }}>
                  <FiBarChart2 size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Utilization Reports</strong>
                  <p>Analyze research activity &amp; hours utilized</p>
                </div>
              </Link>
            </div>
          </DashboardSection>

          {/* Active Sharing Agreements */}
          <DashboardSection
            title="Active Partnerships"
            subtitle="External collaborative agreements"
            actionText="All Agreements"
            actionTo="/dashboard/sharing/agreements"
            icon={FiShare2}
          >
            {agreements.length === 0 ? (
              <div className="dashboard-empty-state" style={{ padding: "16px" }}>
                <p>No active cross-institution agreements established.</p>
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
                      <span className="status-badge status-confirmed">Active</span>
                    </div>
                    <div style={{ color: "var(--text-muted)", fontSize: "11.5px", marginTop: "2px" }}>
                      Inter-University Shared Research Policy
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
