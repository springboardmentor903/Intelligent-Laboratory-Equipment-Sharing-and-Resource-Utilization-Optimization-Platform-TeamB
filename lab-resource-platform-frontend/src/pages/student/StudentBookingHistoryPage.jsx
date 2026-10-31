import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";

const STATUS_META = {
  PENDING_APPROVAL: { label: "Pending Approval", color: "status-pending" },
  CONFIRMED: { label: "Confirmed", color: "status-confirmed" },
  IN_USE: { label: "In Use", color: "status-in-use" },
  COMPLETED: { label: "Completed", color: "status-completed" },
  CANCELLED: { label: "Cancelled", color: "status-cancelled" },
  NO_SHOW: { label: "No Show", color: "status-no-show" },
};

function formatDateTime(isoString) {
  if (!isoString) return "—";
  try {
    return new Date(isoString).toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
    });
  } catch {
    return isoString;
  }
}

function formatDuration(start, end) {
  if (!start || !end) return "";
  const diffMs = new Date(end) - new Date(start);
  const hours = Math.floor(diffMs / (1000 * 60 * 60));
  const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
  if (hours === 0) return `${mins}m`;
  if (mins === 0) return `${hours}h`;
  return `${hours}h ${mins}m`;
}

function StudentBookingHistoryPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("bookings"); // "bookings" or "waitlist"

  const [bookings, setBookings] = useState([]);
  const [waitlist, setWaitlist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [cancelError, setCancelError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [leavingWaitlistId, setLeavingWaitlistId] = useState(null);

  const fetchBookings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get(
        "/api/bookings/my?page=0&size=100&sortBy=createdAt&direction=desc"
      );
      const data = res.data;
      const list = Array.isArray(data)
        ? data
        : Array.isArray(data?.content)
        ? data.content
        : [];
      setBookings(list);
    } catch {
      setError("Failed to load booking history. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchWaitlist = useCallback(async () => {
    try {
      const res = await api.get("/api/waitlist/my");
      setWaitlist(res.data || []);
    } catch {
      console.error("Failed to load waitlist entries.");
    }
  }, []);

  useEffect(() => {
    fetchBookings();
    fetchWaitlist();
  }, [fetchBookings, fetchWaitlist]);

  async function handleCancel(bookingId) {
    if (!window.confirm("Are you sure you want to cancel this booking?"))
      return;
    setCancellingId(bookingId);
    setCancelError(null);
    try {
      await api.post(`/api/bookings/${bookingId}/cancel`);
      await fetchBookings();
    } catch (err) {
      setCancelError(
        err?.response?.data?.message ||
          "Failed to cancel booking. Please try again."
      );
    } finally {
      setCancellingId(null);
    }
  }

  async function handleLeaveWaitlist(id) {
    if (!window.confirm("Are you sure you want to leave this waitlist?")) return;
    setLeavingWaitlistId(id);
    try {
      await api.delete(`/api/waitlist/${id}`);
      setWaitlist((prev) => prev.filter((w) => w.id !== id));
    } catch {
      alert("Failed to leave waitlist.");
    } finally {
      setLeavingWaitlistId(null);
    }
  }

  // Compute stats
  const stats = {
    total: bookings.length,
    pending: bookings.filter((b) => b.status === "PENDING_APPROVAL").length,
    confirmed: bookings.filter(
      (b) => b.status === "CONFIRMED" || b.status === "IN_USE"
    ).length,
    completed: bookings.filter((b) => b.status === "COMPLETED").length,
    cancelled: bookings.filter((b) => b.status === "CANCELLED").length,
    waitlistCount: waitlist.filter((w) => w.status === "WAITING").length,
  };

  const filtered =
    statusFilter === "ALL"
      ? bookings
      : bookings.filter((b) => b.status === statusFilter);

  if (loading) {
    return (
      <div className="bh-container">
        <div className="booking-loading">
          <div className="booking-spinner" />
          <p>Loading booking history…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bh-container">
      <div className="bh-header">
        <div>
          <h1 className="bh-title">My Bookings &amp; Waitlists</h1>
          <p className="bh-subtitle">
            Track all your reservations, recurring sessions, and equipment waitlist queue positions.
          </p>
        </div>
        <button
          type="button"
          className="bh-browse-btn"
          onClick={() => navigate("/dashboard/student/equipments")}
        >
          + New Booking
        </button>
      </div>

      {/* Tabs */}
      <div className="bh-nav-tabs">
        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "bookings" ? "active" : ""}`}
          onClick={() => setActiveTab("bookings")}
        >
          📋 Reservations ({stats.total})
        </button>
        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "waitlist" ? "active" : ""}`}
          onClick={() => setActiveTab("waitlist")}
        >
          ⏳ Active Waitlists ({stats.waitlistCount})
        </button>
      </div>

      {activeTab === "bookings" ? (
        <>
          {/* Stats Grid */}
          <div className="bh-stats-grid">
            <div className="bh-stat-card bh-stat-total">
              <span className="bh-stat-num">{stats.total}</span>
              <span className="bh-stat-label">Total Bookings</span>
            </div>
            <div className="bh-stat-card bh-stat-pending">
              <span className="bh-stat-num">{stats.pending}</span>
              <span className="bh-stat-label">Pending Approval</span>
            </div>
            <div className="bh-stat-card bh-stat-confirmed">
              <span className="bh-stat-num">{stats.confirmed}</span>
              <span className="bh-stat-label">Confirmed / In Use</span>
            </div>
            <div className="bh-stat-card bh-stat-completed">
              <span className="bh-stat-num">{stats.completed}</span>
              <span className="bh-stat-label">Completed</span>
            </div>
            <div className="bh-stat-card bh-stat-cancelled">
              <span className="bh-stat-num">{stats.cancelled}</span>
              <span className="bh-stat-label">Cancelled</span>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="bh-filter-bar">
            <span className="bh-filter-label">Filter by status:</span>
            <div className="bh-filter-pills">
              {["ALL", "PENDING_APPROVAL", "CONFIRMED", "IN_USE", "COMPLETED", "CANCELLED"].map(
                (s) => (
                  <button
                    key={s}
                    type="button"
                    className={`bh-filter-pill ${statusFilter === s ? "active" : ""}`}
                    onClick={() => setStatusFilter(s)}
                  >
                    {s === "ALL" ? "All" : STATUS_META[s]?.label || s}
                  </button>
                )
              )}
            </div>
          </div>

          {cancelError && (
            <div className="booking-form-error" role="alert">
              ⚠️ {cancelError}
            </div>
          )}

          {error && (
            <div className="booking-form-error" role="alert">
              ⚠️ {error}
              <button
                type="button"
                className="bh-retry-btn"
                onClick={fetchBookings}
              >
                Retry
              </button>
            </div>
          )}

          {/* Bookings List */}
          {filtered.length === 0 ? (
            <div className="bh-empty">
              <div className="bh-empty-icon">📋</div>
              <h3>
                {statusFilter === "ALL"
                  ? "No bookings yet"
                  : `No ${STATUS_META[statusFilter]?.label || statusFilter} bookings`}
              </h3>
              <p>
                {statusFilter === "ALL"
                  ? "Start by browsing available equipment and making your first booking."
                  : "Try selecting a different status filter."}
              </p>
              {statusFilter === "ALL" && (
                <button
                  type="button"
                  className="bh-browse-btn"
                  onClick={() => navigate("/dashboard/student/equipments")}
                >
                  Browse Equipment
                </button>
              )}
            </div>
          ) : (
            <div className="bh-list">
              {filtered.map((booking) => {
                const meta = STATUS_META[booking.status] || {
                  label: booking.status,
                  color: "status-pending",
                };
                const canCancel =
                  booking.status === "PENDING_APPROVAL" ||
                  booking.status === "CONFIRMED";
                const duration = formatDuration(booking.startTime, booking.endTime);

                return (
                  <div key={booking.id} className="bh-card">
                    <div className="bh-card-header">
                      <div className="bh-card-title-area">
                        <h3 className="bh-equipment-name">
                          {booking.equipmentName || `Equipment #${booking.equipmentId}`}
                        </h3>
                        {booking.collegeName && (
                          <span className="bh-college-tag">{booking.collegeName}</span>
                        )}
                        {booking.isRecurring && (
                          <span className="bh-recurring-tag">
                            🔁 Recurring ({booking.recurrenceType || "Weekly"})
                          </span>
                        )}
                      </div>
                      <span className={`bh-status-pill ${meta.color}`}>
                        {meta.label}
                      </span>
                    </div>

                    <div className="bh-card-body">
                      <div className="bh-time-block">
                        <div className="bh-time-item">
                          <span className="bh-time-label">Start (IST)</span>
                          <span className="bh-time-value">
                            {formatDateTime(booking.startTime)}
                          </span>
                        </div>
                        <div className="bh-time-arrow">→</div>
                        <div className="bh-time-item">
                          <span className="bh-time-label">End (IST)</span>
                          <span className="bh-time-value">
                            {formatDateTime(booking.endTime)}
                          </span>
                        </div>
                        {duration && (
                          <div className="bh-duration-badge">{duration}</div>
                        )}
                      </div>

                      {booking.purpose && (
                        <p className="bh-purpose">
                          <span className="bh-purpose-label">Purpose:</span>{" "}
                          {booking.purpose}
                        </p>
                      )}

                      {booking.rejectionReason && (
                        <div className="bh-rejection-notice">
                          <span className="bh-rejection-icon">❌</span>
                          <div>
                            <strong>Cancellation/Rejection Reason:</strong>
                            <p>{booking.rejectionReason}</p>
                          </div>
                        </div>
                      )}
                    </div>

                    {canCancel && (
                      <div className="bh-card-footer">
                        <button
                          type="button"
                          className="bh-cancel-btn"
                          disabled={cancellingId === booking.id}
                          onClick={() => handleCancel(booking.id)}
                        >
                          {cancellingId === booking.id
                            ? "Cancelling…"
                            : "Cancel Booking"}
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      ) : (
        /* Waitlist Tab */
        <div className="bh-waitlist-section">
          {waitlist.length === 0 ? (
            <div className="bh-empty">
              <div className="bh-empty-icon">⏳</div>
              <h3>No Active Waitlists</h3>
              <p>
                When a desired equipment slot is busy, you can join the waitlist from the booking page. You will automatically receive the slot if it opens!
              </p>
            </div>
          ) : (
            <div className="bh-list">
              {waitlist.map((item) => (
                <div key={item.id} className="bh-card bh-waitlist-card">
                  <div className="bh-card-header">
                    <div className="bh-card-title-area">
                      <h3 className="bh-equipment-name">{item.equipmentName}</h3>
                      <span className="bh-queue-pill">
                        🎯 Position #{item.queuePosition || 1} in line
                      </span>
                    </div>
                    <span className="bh-status-pill status-pending">
                      {item.status}
                    </span>
                  </div>

                  <div className="bh-card-body">
                    <div className="bh-time-block">
                      <div className="bh-time-item">
                        <span className="bh-time-label">Desired Start</span>
                        <span className="bh-time-value">{formatDateTime(item.desiredStartTime)}</span>
                      </div>
                      <div className="bh-time-arrow">→</div>
                      <div className="bh-time-item">
                        <span className="bh-time-label">Desired End</span>
                        <span className="bh-time-value">{formatDateTime(item.desiredEndTime)}</span>
                      </div>
                    </div>

                    {item.purpose && (
                      <p className="bh-purpose">
                        <span className="bh-purpose-label">Requested for:</span> {item.purpose}
                      </p>
                    )}

                    {item.status === "WAITING" && (
                      <div className="bh-waitlist-status-note">
                        ℹ️ You are in the priority waitlist. If any conflicting reservation is cancelled, you will be automatically promoted!
                      </div>
                    )}
                  </div>

                  {item.status === "WAITING" && (
                    <div className="bh-card-footer">
                      <button
                        type="button"
                        className="bh-cancel-btn"
                        disabled={leavingWaitlistId === item.id}
                        onClick={() => handleLeaveWaitlist(item.id)}
                      >
                        {leavingWaitlistId === item.id ? "Leaving…" : "Leave Waitlist"}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default StudentBookingHistoryPage;
