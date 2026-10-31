import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import api from "../../services/api";

function EmptyRoleDashboard({ roleTitle, roleSubtitle, equipmentsPath, bookingsPath }) {
  const { user } = useAuth();
  const firstName = user?.fullName?.trim().split(/\s+/)[0] || "User";

  const [stats, setStats] = useState({
    totalEquipments: null,
    totalBookings: null,
    pendingBookings: null,
  });

  useEffect(() => {
    Promise.allSettled([
      api.get("/api/equipment"),
      api.get("/api/bookings"),
    ]).then(([equipRes, bookingsRes]) => {
      const eqCount = equipRes.status === "fulfilled" && Array.isArray(equipRes.value?.data)
        ? equipRes.value.data.length
        : 0;

      const bkList = bookingsRes.status === "fulfilled" && Array.isArray(bookingsRes.value?.data)
        ? bookingsRes.value.data
        : [];

      const pendingCount = bkList.filter((b) => b.status === "PENDING_APPROVAL").length;

      setStats({
        totalEquipments: eqCount,
        totalBookings: bkList.length,
        pendingBookings: pendingCount,
      });
    }).catch(() => {});
  }, []);

  const statNum = (v) => (v === null ? "—" : v);

  return (
    <div className="std-dashboard">
      {/* Real-time Metrics Row */}
      <section className="std-stats-row" aria-label="Overview metrics">
        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-blue">🔬</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.totalEquipments)}</span>
            <span className="std-stat-label">Colleges Equipments</span>
          </div>
        </div>

        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-yellow">⏳</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.pendingBookings)}</span>
            <span className="std-stat-label">Pending Approval (Default)</span>
          </div>
        </div>

        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-green">📅</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.totalBookings)}</span>
            <span className="std-stat-label">Total Student Bookings</span>
          </div>
        </div>
      </section>

      {/* Quick Navigation Panels */}
      <section className="std-actions-section">
        <h2 className="std-section-title">Quick Actions</h2>
        <div className="std-action-grid">
          <Link to={equipmentsPath} className="std-action-card">
            <div className="std-action-icon-wrap std-action-blue">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <path d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </div>
            <div className="std-action-text">
              <h3>Colleges Equipments</h3>
              <p>Filter by college, register new equipment, update specs, and manage catalog.</p>
            </div>
            <span className="std-action-arrow">→</span>
          </Link>

          <Link to={bookingsPath} className="std-action-card">
            <div className="std-action-icon-wrap std-action-green">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2" />
                <path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                <path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
            </div>
            <div className="std-action-text">
              <h3>Student Bookings &amp; Lifecycle</h3>
              <p>Manage student reservations: Confirm, set In Use, Complete, Cancel, or No Show.</p>
            </div>
            <span className="std-action-arrow">→</span>
          </Link>
        </div>
      </section>

      {/* Info Notice Banner */}
      <div className="std-info-banner">
        <span className="std-info-icon">💡</span>
        <p>
          New student booking requests appear as <strong>PENDING_APPROVAL</strong> by default.
          You have full authorization to modify booking statuses and register laboratory instruments.
        </p>
      </div>
    </div>
  );
}

export default EmptyRoleDashboard;

