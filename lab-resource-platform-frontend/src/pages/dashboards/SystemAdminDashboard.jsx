import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../../services/api";

export default function SystemAdminDashboard() {
  const [stats, setStats] = useState({
    collegesCount: 0,
    equipmentsCount: 0,
    pendingRequestsCount: 0,
    bookingsCount: 0,
    usersCount: 0,
  });
  const [loading, setLoading] = useState(true);

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

      setStats({
        collegesCount: collegesRes.status === "fulfilled" ? collegesRes.value.data.length : 0,
        equipmentsCount: equipRes.status === "fulfilled" ? equipRes.value.data.length : 0,
        pendingRequestsCount: reqRes.status === "fulfilled" ? reqRes.value.data.length : 0,
        bookingsCount: bookingsRes.status === "fulfilled" ? bookingsRes.value.data.length : 0,
        usersCount: usersRes.status === "fulfilled" ? usersRes.value.data.length : 0,
      });
    } catch (err) {
      console.error("Failed to load dashboard metrics", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  return (
    <div className="admin-page-container">
      <header className="admin-welcome">
        <p className="admin-eyebrow">PLATFORM ADMINISTRATION</p>
        <h1 className="admin-title">System Administrator Dashboard</h1>
        <p className="admin-subtitle">
          Central platform control hub for managing cross-institutional equipment, role access approvals, bookings, and user accounts.
        </p>
      </header>

      {/* Metrics Cards Grid */}
      <section className="dashboard-cards" style={{ marginTop: "1.5rem" }}>
        <article className="task-card">
          <div className="admin-stat-icon">🏛️</div>
          <h3>Affiliated Colleges</h3>
          <p className="admin-stat-number">{loading ? "..." : stats.collegesCount}</p>
          <p className="admin-stat-sub">Colleges and institutions onboarded</p>
          <Link to="/dashboard/admin/equipments" className="admin-card-link">
            View College Equipment →
          </Link>
        </article>

        <article className="task-card">
          <div className="admin-stat-icon">🔬</div>
          <h3>Equipments</h3>
          <p className="admin-stat-number">{loading ? "..." : stats.equipmentsCount}</p>
          <p className="admin-stat-sub">Total lab instruments across colleges</p>
          <Link to="/dashboard/admin/equipments" className="admin-card-link">
            Manage Equipment →
          </Link>
        </article>

        <article className="task-card">
          <div className="admin-stat-icon">📋</div>
          <h3>Pending Requests</h3>
          <p className="admin-stat-number" style={{ color: stats.pendingRequestsCount > 0 ? "#b45309" : "inherit" }}>
            {loading ? "..." : stats.pendingRequestsCount}
          </p>
          <p className="admin-stat-sub">Role &amp; college access applications</p>
          <Link to="/dashboard/admin/role-requests" className="admin-card-link">
            Review Requests →
          </Link>
        </article>

        <article className="task-card">
          <div className="admin-stat-icon">📅</div>
          <h3>Bookings</h3>
          <p className="admin-stat-number">{loading ? "..." : stats.bookingsCount}</p>
          <p className="admin-stat-sub">Cross-college reservations logged</p>
          <Link to="/dashboard/admin/bookings" className="admin-card-link">
            Manage Bookings →
          </Link>
        </article>

        <article className="task-card">
          <div className="admin-stat-icon">👥</div>
          <h3>User Accounts</h3>
          <p className="admin-stat-number">{loading ? "..." : stats.usersCount}</p>
          <p className="admin-stat-sub">Platform registered accounts</p>
          <Link to="/dashboard/admin/accounts" className="admin-card-link">
            Manage Accounts →
          </Link>
        </article>
      </section>

      {/* Quick Navigation Panel */}
      <section className="admin-quick-links-section" style={{ marginTop: "2rem" }}>
        <h2>Administrator Quick Actions</h2>
        <div className="admin-action-tiles-grid">
          <Link to="/dashboard/admin/equipments" className="admin-action-tile">
            <span className="tile-icon">🔬</span>
            <div className="tile-content">
              <strong>College Equipments</strong>
              <p>Filter, add, and monitor instruments by college dropdown.</p>
            </div>
          </Link>

          <Link to="/dashboard/admin/role-requests" className="admin-action-tile">
            <span className="tile-icon">🛡️</span>
            <div className="tile-content">
              <strong>Role Access Requests &amp; History</strong>
              <p>Approve or reject role requests with college history tracking.</p>
            </div>
          </Link>

          <Link to="/dashboard/admin/bookings" className="admin-action-tile">
            <span className="tile-icon">📆</span>
            <div className="tile-content">
              <strong>All Colleges Bookings</strong>
              <p>View reservations across institutions with college filter.</p>
            </div>
          </Link>

          <Link to="/dashboard/admin/accounts" className="admin-action-tile">
            <span className="tile-icon">👤</span>
            <div className="tile-content">
              <strong>Accounts Management (CRUD)</strong>
              <p>Create, update, toggle status, and delete user accounts.</p>
            </div>
          </Link>
        </div>
      </section>
    </div>
  );
}
