import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../../services/api";

function StudentDashboard() {
  const [stats, setStats] = useState({
    total: null,
    pending: null,
    confirmed: null,
    completed: null,
  });

  useEffect(() => {
    api
      .get("/api/bookings/my?page=0&size=100&sortBy=createdAt&direction=desc")
      .then((res) => {
        const data = res.data;
        const list = Array.isArray(data)
          ? data
          : Array.isArray(data?.content)
          ? data.content
          : [];
        setStats({
          total: list.length,
          pending: list.filter((b) => b.status === "PENDING_APPROVAL").length,
          confirmed: list.filter(
            (b) => b.status === "CONFIRMED" || b.status === "IN_USE"
          ).length,
          completed: list.filter((b) => b.status === "COMPLETED").length,
        });
      })
      .catch(() => {
        // silently ignore - stats are decorative
      });
  }, []);

  const statNum = (v) => (v === null ? "—" : v);

  return (
    <div className="std-dashboard">
      {/* Stats Row */}
      <section className="std-stats-row" aria-label="Booking summary">
        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-blue">📋</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.total)}</span>
            <span className="std-stat-label">Total Bookings</span>
          </div>
        </div>
        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-yellow">⏳</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.pending)}</span>
            <span className="std-stat-label">Pending Approval</span>
          </div>
        </div>
        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-green">✅</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.confirmed)}</span>
            <span className="std-stat-label">Confirmed</span>
          </div>
        </div>
        <div className="std-stat-card">
          <span className="std-stat-icon std-icon-gray">🏁</span>
          <div className="std-stat-content">
            <span className="std-stat-num">{statNum(stats.completed)}</span>
            <span className="std-stat-label">Completed</span>
          </div>
        </div>
      </section>

      {/* Quick Actions */}
      <section className="std-actions-section">
        <h2 className="std-section-title">Quick Actions</h2>
        <div className="std-action-grid">
          <Link to="/dashboard/student/equipments" className="std-action-card">
            <div className="std-action-icon-wrap std-action-blue">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <path d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </div>
            <div className="std-action-text">
              <h3>Browse Equipment</h3>
              <p>Explore lab resources across all colleges and place a booking.</p>
            </div>
            <span className="std-action-arrow">→</span>
          </Link>

          <Link to="/dashboard/student/bookings" className="std-action-card">
            <div className="std-action-icon-wrap std-action-green">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2" />
                <path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                <path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
            </div>
            <div className="std-action-text">
              <h3>My Bookings</h3>
              <p>Review all your booking requests and their current statuses.</p>
            </div>
            <span className="std-action-arrow">→</span>
          </Link>
        </div>
      </section>

      {/* Info Banner */}
      <div className="std-info-banner">
        <span className="std-info-icon">💡</span>
        <p>
          Equipment availability is updated in real-time. Some equipment may
          require admin approval before your booking is confirmed.
        </p>
      </div>
    </div>
  );
}

export default StudentDashboard;
