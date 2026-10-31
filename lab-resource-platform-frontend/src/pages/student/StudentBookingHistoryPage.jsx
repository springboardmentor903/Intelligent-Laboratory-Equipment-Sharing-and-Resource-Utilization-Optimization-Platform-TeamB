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
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
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
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [cancelError, setCancelError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");

  const fetchBookings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get(
        "/api/bookings/my?page=0&size=100&sortBy=createdAt&direction=desc"
      );
      // Support both paginated and array responses
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

  useEffect(() => {
    fetchBookings();
  }, [fetchBookings]);

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

  // Compute stats
  const stats = {
    total: bookings.length,
    pending: bookings.filter((b) => b.status === "PENDING_APPROVAL").length,
    confirmed: bookings.filter(
      (b) => b.status === "CONFIRMED" || b.status === "IN_USE"
    ).length,
    completed: bookings.filter((b) => b.status === "COMPLETED").length,
    cancelled: bookings.filter((b) => b.status === "CANCELLED").length,
    noShow: bookings.filter((b) => b.status === "NO_SHOW").length,
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
          <h1 className="bh-title">My Booking History</h1>
          <p className="bh-subtitle">
            Track all your equipment reservation requests and their statuses.
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
        <div className="bh-stat-card bh-stat-noshow">
          <span className="bh-stat-num">{stats.noShow}</span>
          <span className="bh-stat-label">No Show</span>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bh-filter-bar">
        <span className="bh-filter-label">Filter by status:</span>
        <div className="bh-filter-pills">
          {["ALL", "PENDING_APPROVAL", "CONFIRMED", "IN_USE", "COMPLETED", "CANCELLED", "NO_SHOW"].map(
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
                  </div>
                  <span className={`bh-status-pill ${meta.color}`}>
                    {meta.label}
                  </span>
                </div>

                <div className="bh-card-body">
                  <div className="bh-time-block">
                    <div className="bh-time-item">
                      <span className="bh-time-label">Start</span>
                      <span className="bh-time-value">
                        {formatDateTime(booking.startTime)}
                      </span>
                    </div>
                    <div className="bh-time-arrow">→</div>
                    <div className="bh-time-item">
                      <span className="bh-time-label">End</span>
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
                        <strong>Rejection Reason:</strong>
                        <p>{booking.rejectionReason}</p>
                      </div>
                    </div>
                  )}

                  {booking.adminNotes && (
                    <div className="bh-admin-notes">
                      <span>📝</span>
                      <div>
                        <strong>Admin Note:</strong>
                        <p>{booking.adminNotes}</p>
                      </div>
                    </div>
                  )}
                </div>

                <div className="bh-card-footer">
                  <span className="bh-booking-id">
                    Booking #{booking.id} · Submitted{" "}
                    {formatDateTime(booking.createdAt)}
                  </span>
                  {canCancel && (
                    <button
                      type="button"
                      className="bh-cancel-btn"
                      onClick={() => handleCancel(booking.id)}
                      disabled={cancellingId === booking.id}
                    >
                      {cancellingId === booking.id ? "Cancelling…" : "Cancel"}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default StudentBookingHistoryPage;

