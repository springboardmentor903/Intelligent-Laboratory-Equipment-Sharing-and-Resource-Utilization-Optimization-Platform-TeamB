import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useToast } from "../../context/ToastContext";
import { getMyBookings, cancelBooking, BOOKING_STATUSES } from "../../services/bookingService";
import BookingStatusBadge from "../../components/booking/BookingStatusBadge";
import CancelBookingModal from "../../components/booking/CancelBookingModal";
import "./booking.css";

export default function MyBookingsPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [cancellingBooking, setCancellingBooking] = useState(null);
  const [isCancelling, setIsCancelling] = useState(false);
  const [now] = useState(() => Date.now());

  function reloadBookings() {
    setLoading(true);
    setError(null);
    getMyBookings({ status: statusFilter !== "ALL" ? statusFilter : null })
      .then((data) => {
        setBookings(data?.content || (Array.isArray(data) ? data : []));
      })
      .catch((err) => {
        console.error("Failed to load user bookings:", err);
        setError("Unable to retrieve your reservations. Please try again.");
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    let ignore = false;

    getMyBookings({ status: statusFilter !== "ALL" ? statusFilter : null })
      .then((data) => {
        if (!ignore) {
          setBookings(data?.content || (Array.isArray(data) ? data : []));
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to load user bookings:", err);
          setError("Unable to retrieve your reservations. Please try again.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [statusFilter]);

  async function handleConfirmCancel(bookingId, reason) {
    setIsCancelling(true);
    try {
      await cancelBooking(bookingId, reason);
      toast.success("Reservation cancelled successfully.");
      setCancellingBooking(null);
      reloadBookings();
    } catch (err) {
      console.error("Failed to cancel booking:", err);
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to cancel reservation.";
      toast.error(msg);
    } finally {
      setIsCancelling(false);
    }
  }

  function formatDateTime(isoString) {
    if (!isoString) return "—";
    try {
      return new Date(isoString).toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return isoString;
    }
  }

  function canCancel(b) {
    if (!b) return false;
    if (b.status === "CANCELLED" || b.status === "COMPLETED" || b.status === "NO_SHOW") {
      return false;
    }
    // Cannot cancel if start time is in the past
    const startMs = new Date(b.startTime).getTime();
    return !isNaN(startMs) && startMs > now;
  }

  return (
    <div className="booking-page-container">
      {/* Breadcrumb */}
      <nav className="booking-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <span className="current">My Reservations</span>
      </nav>

      {/* Header */}
      <header className="booking-header">
        <div>
          <h1>My Laboratory Reservations</h1>
          <p>Track, manage, and review your equipment bookings and scheduled time slots.</p>
        </div>

        <div className="booking-header-actions">
          <Link to="/dashboard/bookings/calendar" className="btn-primary">
            <span>📅 Availability Calendar</span>
          </Link>
          <Link to="/dashboard/bookings/new" className="btn-secondary">
            <span>➕ New Booking</span>
          </Link>
        </div>
      </header>

      {/* Toolbar & Status Filters */}
      <section className="bookings-toolbar">
        <div className="bookings-filter-tabs" role="tablist" aria-label="Filter bookings by status">
          <button
            type="button"
            className={`filter-tab-btn ${statusFilter === "ALL" ? "active" : ""}`}
            onClick={() => setStatusFilter("ALL")}
          >
            All Bookings
          </button>
          {BOOKING_STATUSES.map((st) => (
            <button
              key={st.value}
              type="button"
              className={`filter-tab-btn ${statusFilter === st.value ? "active" : ""}`}
              onClick={() => setStatusFilter(st.value)}
            >
              {st.label}
            </button>
          ))}
        </div>

        <div>
          <button type="button" className="btn-secondary" onClick={reloadBookings} style={{ padding: "6px 12px", fontSize: "13px" }}>
            🔄 Refresh
          </button>
        </div>
      </section>

      {/* Main Content Area */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading your reservations...</p>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Bookings</h3>
          <p>{error}</p>
          <button type="button" className="btn-primary" onClick={reloadBookings}>
            Retry
          </button>
        </div>
      ) : bookings.length === 0 ? (
        <div className="equipment-empty-state">
          <span className="state-icon" aria-hidden="true">📋</span>
          <h3>No Reservations Found</h3>
          <p>
            {statusFilter !== "ALL"
              ? `You have no bookings matching the "${statusFilter.replaceAll("_", " ")}" filter.`
              : "You haven't reserved any laboratory equipment yet."}
          </p>
          <Link to="/dashboard/bookings/calendar" className="btn-primary">
            Reserve Equipment Now
          </Link>
        </div>
      ) : (
        <div className="bookings-table-container">
          <table className="bookings-table">
            <thead>
              <tr>
                <th>Equipment</th>
                <th>Reserved Interval</th>
                <th>Status</th>
                <th>Purpose</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b.id}>
                  <td>
                    <Link
                      to={`/dashboard/bookings/${b.id}`}
                      style={{ fontWeight: 600, color: "var(--primary-dark)", textDecoration: "none" }}
                    >
                      {b.equipmentName || `Equipment #${b.equipmentId}`}
                    </Link>
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{formatDateTime(b.startTime)}</div>
                    <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      to {formatDateTime(b.endTime)}
                    </div>
                  </td>
                  <td>
                    <BookingStatusBadge status={b.status} />
                  </td>
                  <td>
                    <span style={{ fontSize: "13px", color: b.purpose ? "var(--text-main)" : "var(--text-muted)" }}>
                      {b.purpose || "No notes"}
                    </span>
                  </td>
                  <td>
                    <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      {formatDateTime(b.createdAt)}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "8px" }}>
                      <button
                        type="button"
                        className="btn-secondary"
                        style={{ padding: "4px 10px", fontSize: "12px" }}
                        onClick={() => navigate(`/dashboard/bookings/${b.id}`)}
                      >
                        Details
                      </button>
                      {canCancel(b) && (
                        <button
                          type="button"
                          className="btn-secondary"
                          style={{
                            padding: "4px 10px",
                            fontSize: "12px",
                            color: "var(--color-error)",
                            borderColor: "var(--color-error-border)",
                          }}
                          onClick={() => setCancellingBooking(b)}
                        >
                          Cancel
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Cancellation Modal */}
      <CancelBookingModal
        isOpen={Boolean(cancellingBooking)}
        booking={cancellingBooking}
        onClose={() => setCancellingBooking(null)}
        onConfirm={handleConfirmCancel}
        isSubmitting={isCancelling}
      />
    </div>
  );
}
