import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";
import DashboardHeader from "../../components/dashboard/DashboardHeader";
import DashboardKpiCard from "../../components/dashboard/DashboardKpiCard";
import DashboardSection from "../../components/dashboard/DashboardSection";
import {
  FiLayers,
  FiTrendingUp,
  FiDollarSign,
  FiFileText,
  FiCalendar,
  FiActivity,
  FiCheckCircle,
  FiArrowRight,
  FiShield,
  FiGlobe,
} from "react-icons/fi";

export default function InstitutionAdminDashboard() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [equipments, setEquipments] = useState([]);
  const [agreements, setAgreements] = useState([]);
  const [accessRequests, setAccessRequests] = useState([]);
  const [sharingSummary, setSharingSummary] = useState({
    totalSharedHours: 0,
    totalFeesEarned: 0,
    activePartnersCount: 0,
  });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [equipRes, agreeRes, reqRes, summaryRes] = await Promise.allSettled([
        api.get("/api/equipment"),
        api.get("/api/sharing/agreements"),
        api.get("/api/sharing/access-requests"),
        api.get("/api/sharing/analytics/summary"),
      ]);

      let equipList = [];
      if (equipRes.status === "fulfilled" && equipRes.value?.data) {
        const d = equipRes.value.data;
        equipList = Array.isArray(d) ? d : Array.isArray(d?.content) ? d.content : [];
        setEquipments(equipList);
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

      if (summaryRes.status === "fulfilled" && summaryRes.value?.data) {
        const s = summaryRes.value.data;
        setSharingSummary({
          totalSharedHours: Number(s.totalHoursProvided ?? s.totalSharedHours ?? 0),
          totalFeesEarned: Number(s.feesEarned ?? s.totalFeesEarned ?? 0),
          activePartnersCount: Number(s.activeAgreements ?? s.totalPartners ?? s.activePartnersCount ?? agreeList.length),
        });
      }
    } catch (err) {
      console.error("Failed to load institution admin dashboard data", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const pendingRequests = accessRequests.filter((r) => r.status === "PENDING");

  const formatDateTime = (dateStr) => {
    if (!dateStr) return "—";
    try {
      return new Date(dateStr).toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="enterprise-dashboard">
      <DashboardHeader
        roleTitle="Institution Administrator"
        institutionName={user?.collegeName || user?.institutionName || "University Administration"}
        title="Institutional Governance &amp; Cross-Campus Sharing"
        subtitle="Manage university laboratory assets, monitor inter-institutional resource sharing contracts, and review access requests."
        actions={
          <div style={{ display: "flex", gap: "8px" }}>
            <Link
              to="/dashboard/sharing/agreements"
              className="btn-primary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiFileText size={15} />
              <span>Review Sharing Contracts</span>
            </Link>
            <Link
              to="/dashboard/sharing/analytics"
              className="btn-secondary"
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <FiTrendingUp size={15} />
              <span>Sharing Analytics</span>
            </Link>
          </div>
        }
      />

      {/* KPI Cards */}
      <div className="dashboard-kpi-grid">
        <DashboardKpiCard
          title="Campus Instruments"
          value={equipments.length}
          subtitle="Total university inventory"
          icon={FiLayers}
          variant="primary"
          to="/dashboard/institution-admin/equipments"
          loading={loading}
        />
        <DashboardKpiCard
          title="Shared Hours Provided"
          value={sharingSummary.totalSharedHours}
          unit="hrs"
          subtitle="To partner institutions"
          icon={FiTrendingUp}
          variant="teal"
          to="/dashboard/sharing/analytics"
          loading={loading}
        />
        <DashboardKpiCard
          title="Cost Recovery Earned"
          value={`₹${sharingSummary.totalFeesEarned.toLocaleString("en-IN")}`}
          subtitle="Inter-institutional usage fees"
          icon={FiDollarSign}
          variant="success"
          to="/dashboard/sharing/analytics"
          loading={loading}
        />
        <DashboardKpiCard
          title="Partner Agreements"
          value={sharingSummary.activePartnersCount || agreements.length}
          subtitle="Active sharing memorandums"
          icon={FiGlobe}
          variant="neutral"
          to="/dashboard/sharing/agreements"
          loading={loading}
        />
      </div>

      {/* Main Grid: Access Requests Queue & Partnership Performance */}
      <div className="dashboard-main-grid">
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Access Requests Table */}
          <DashboardSection
            title="External Institution Access Requests"
            subtitle="Cross-institutional booking applications requiring institutional authorization"
            actionText="Manage Requests"
            actionTo="/dashboard/sharing/agreements"
            icon={FiFileText}
            noPadding
          >
            {loading ? (
              <div className="dashboard-empty-state">
                <p>Loading access requests...</p>
              </div>
            ) : accessRequests.length === 0 ? (
              <div className="dashboard-empty-state">
                <FiCheckCircle size={30} className="dashboard-empty-icon" style={{ color: "var(--brand-teal)" }} />
                <h4>No Pending Access Requests</h4>
                <p>All external partner booking requests have been reviewed.</p>
              </div>
            ) : (
              <div className="dashboard-table-wrap">
                <table className="dashboard-clean-table">
                  <thead>
                    <tr>
                      <th>Requesting Institution</th>
                      <th>Instrument</th>
                      <th>Requested Range</th>
                      <th>Status</th>
                      <th style={{ textAlign: "right" }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {accessRequests.slice(0, 5).map((req) => (
                      <tr key={req.id}>
                        <td>
                          <strong>{req.requestingInstitutionName || req.requestingCollegeName || `Institution #${req.requestingInstitutionId || req.id}`}</strong>
                          <span style={{ display: "block", fontSize: "11px", color: "var(--text-muted)" }}>
                            By: {req.requestorName || req.requestorEmail || "Faculty Researcher"}
                          </span>
                        </td>
                        <td>{req.equipmentName || `Item #${req.equipmentId}`}</td>
                        <td>
                          {formatDateTime(req.requestedStartDate)} - {formatDateTime(req.requestedEndDate)}
                        </td>
                        <td>
                          <span className={`status-badge ${req.status === "APPROVED" ? "status-confirmed" : req.status === "PENDING" ? "status-pending" : "status-idle"}`}>
                            {req.status || "Pending"}
                          </span>
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <Link
                            to="/dashboard/sharing/agreements"
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

          {/* Inter-Institution Partnership Summary Box */}
          <DashboardSection
            title="Resource Sharing Revenue &amp; Optimization"
            subtitle="Financial recovery and utilization breakdown across partner universities"
            actionText="Open Full Analytics"
            actionTo="/dashboard/sharing/analytics"
            icon={FiTrendingUp}
          >
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "16px" }}>
              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Total Shared Volume</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-primary)", marginTop: "4px" }}>
                  {sharingSummary.totalSharedHours} hrs
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Across accredited partners</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Fee Recovery Balance</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--status-success-text)", marginTop: "4px" }}>
                  ₹{sharingSummary.totalFeesEarned.toLocaleString("en-IN")}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Subsidies &amp; equipment revenue</span>
              </div>

              <div style={{ background: "var(--surface-subtle)", padding: "16px", borderRadius: "var(--radius-sm)" }}>
                <span style={{ fontSize: "12px", color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>Active Agreements</span>
                <div style={{ fontSize: "24px", fontWeight: 700, color: "var(--brand-teal)", marginTop: "4px" }}>
                  {agreements.length}
                </div>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>Bilateral MOUs in effect</span>
              </div>
            </div>
          </DashboardSection>
        </div>

        {/* Right Column: Governance Shortcuts */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          {/* Institutional Governance Actions */}
          <DashboardSection
            title="Governance Actions"
            subtitle="Campus administrative controls"
            icon={FiShield}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              <Link to="/dashboard/sharing/agreements" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-primary-subtle)", color: "var(--brand-primary)" }}>
                  <FiFileText size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Sharing Contracts &amp; Requests</strong>
                  <p>Authorize external access &amp; manage partner terms</p>
                </div>
              </Link>

              <Link to="/dashboard/sharing/analytics" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--brand-teal-subtle)", color: "var(--brand-teal)" }}>
                  <FiTrendingUp size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Sharing Analytics</strong>
                  <p>Exchange balances, trends &amp; partner reports</p>
                </div>
              </Link>

              <Link to="/dashboard/institution-admin/equipments" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--surface-subtle)", color: "var(--text-secondary)" }}>
                  <FiLayers size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>Campus Equipments</strong>
                  <p>Monitor assets across all academic departments</p>
                </div>
              </Link>

              <Link to="/dashboard/institution-admin/bookings" className="dashboard-action-tile">
                <div className="action-tile-icon" style={{ background: "var(--status-info-bg)", color: "var(--status-info-text)" }}>
                  <FiCalendar size={18} />
                </div>
                <div className="action-tile-content">
                  <strong>All Campus Reservations</strong>
                  <p>Institutional schedule log &amp; booking audit</p>
                </div>
              </Link>
            </div>
          </DashboardSection>
        </div>
      </div>
    </div>
  );
}
