import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { useToast } from "../../context/ToastContext";
import {
  getBookingById,
  getBookingHistory,
  cancelBooking,
} from "../../services/bookingService";
import BookingStatusBadge from "../../components/booking/BookingStatusBadge";
import CancelBookingModal from "../../components/booking/CancelBookingModal";
import "./booking.css";

export default function BookingDetailsPage() {
  const { id } = useParams();
  const toast = useToast();

  const [booking, setBooking] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isNotFound, setIsNotFound] = useState(false);
  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [now] = useState(() => Date.now());

  function reloadDetails() {
    setLoading(true);
    setError(null);
    setIsNotFound(false);

    Promise.all([
      getBookingById(id),
      getBookingHistory(id).catch(() => []),
    ])
      .then(([bookingData, historyData]) => {
        setBooking(bookingData);
        setHistory(Array.isArray(historyData) ? historyData : []);
      })
      .catch((err) => {
        console.error("Failed to load booking details:", err);
        if (err.response?.status === 404) {
          setIsNotFound(true);
        } else {
          setError(
            err.response?.data?.message ||
            err.response?.data?.error ||
            "Unable to retrieve reservation details."
          );
        }
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    let ignore = false;

    Promise.all([
      getBookingById(id),
      getBookingHistory(id).catch(() => []),
    ])
      .then(([bookingData, historyData]) => {
        if (!ignore) {
          setBooking(bookingData);
          setHistory(Array.isArray(historyData) ? historyData : []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to load booking details:", err);
          if (err.response?.status === 404) {
            setIsNotFound(true);
          } else {
            setError(
              err.response?.data?.message ||
              err.response?.data?.error ||
              "Unable to retrieve reservation details."
            );
          }
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id]);

  async function handleConfirmCancel(bookingId, reason) {
    setIsCancelling(true);
    try {
      await cancelBooking(bookingId, reason);
      toast.success("Reservation cancelled.");
      setIsCancelModalOpen(false);
      reloadDetails();
    } catch (err) {
      console.error("Failed to cancel booking:", err);
      toast.error(err.response?.data?.message || "Failed to cancel reservation.");
    } finally {
      setIsCancelling(false);
    }
  }

  function formatDateTime(isoString) {
    if (!isoString) return "Not recorded";
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

  const canCancel =
    booking &&
    booking.status !== "CANCELLED" &&
    booking.status !== "COMPLETED" &&
    booking.status !== "NO_SHOW" &&
    new Date(booking.startTime).getTime() > now;

  return (
    <div className="booking-page-container">
      {/* Breadcrumb */}
      <nav className="booking-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <Link to="/dashboard/bookings/my">My Reservations</Link>
        <span className="separator">/</span>
        <span className="current">{loading ? "Loading..." : `Booking #${id}`}</span>
      </nav>

      {/* Loading & Error States */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading reservation details...</p>
        </div>
      ) : isNotFound ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">🔍</span>
          <h3>Reservation Not Found</h3>
          <p>The requested booking ID #{id} was not found or has expired.</p>
          <Link to="/dashboard/bookings/my" className="btn-primary">
            Back to My Bookings
          </Link>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Details</h3>
          <p>{error}</p>
          <div style={{ display: "flex", gap: "10px", justifyContent: "center" }}>
            <button type="button" className="btn-primary" onClick={reloadDetails}>
              Retry
            </button>
            <Link to="/dashboard/bookings/my" className="btn-secondary">
              Back to My Bookings
            </Link>
          </div>
        </div>
      ) : booking ? (
        <>
          {/* Hero Header */}
          <section className="equipment-detail-hero" style={{ marginBottom: "24px" }}>
            <div className="detail-hero-top">
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "8px" }}>
                  <span className="summary-pill">Reservation #{booking.id}</span>
                  <BookingStatusBadge status={booking.status} />
                </div>

                <h1 className="detail-hero-title">
                  {booking.equipmentName || `Equipment #${booking.equipmentId}`}
                </h1>

                <p className="detail-hero-subtitle">
                  Reserved by {booking.userName || "User"} ({booking.userEmail})
                </p>
              </div>

              <div style={{ display: "flex", gap: "10px" }}>
                <Link to="/dashboard/bookings/my" className="btn-secondary">
                  ← Back to My Reservations
                </Link>

                {canCancel && (
                  <button
                    type="button"
                    className="btn-primary"
                    style={{ background: "#DC2626", borderColor: "#DC2626" }}
                    onClick={() => setIsCancelModalOpen(true)}
                  >
                    Cancel Booking
                  </button>
                )}
              </div>
            </div>
          </section>

          {/* Details Specifications Grid */}
          <div className="equipment-detail-grid" style={{ marginBottom: "24px" }}>
            {/* Booking Timing */}
            <article className="detail-section-card">
              <h2 className="detail-section-title">
                <span aria-hidden="true">⏱️</span>
                <span>Schedule & Duration</span>
              </h2>

              <div className="detail-item-list">
                <div className="detail-item">
                  <span className="detail-item-label">Start Time</span>
                  <span className="detail-item-value">{formatDateTime(booking.startTime)}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">End Time</span>
                  <span className="detail-item-value">{formatDateTime(booking.endTime)}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Status</span>
                  <span className="detail-item-value">
                    <BookingStatusBadge status={booking.status} />
                  </span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Created At</span>
                  <span className="detail-item-value">{formatDateTime(booking.createdAt)}</span>
                </div>
              </div>
            </article>

            {/* Approval & Notes */}
            <article className="detail-section-card">
              <h2 className="detail-section-title">
                <span aria-hidden="true">🛡️</span>
                <span>Approval & Purpose</span>
              </h2>

              <div className="detail-item-list">
                <div className="detail-item">
                  <span className="detail-item-label">Purpose</span>
                  <span className="detail-item-value">{booking.purpose || "No notes provided"}</span>
                </div>

                {booking.approverName && (
                  <div className="detail-item">
                    <span className="detail-item-label">Adjudicated By</span>
                    <span className="detail-item-value">{booking.approverName}</span>
                  </div>
                )}

                {booking.approvedAt && (
                  <div className="detail-item">
                    <span className="detail-item-label">Decision Timestamp</span>
                    <span className="detail-item-value">{formatDateTime(booking.approvedAt)}</span>
                  </div>
                )}

                {booking.rejectionReason && (
                  <div className="detail-item">
                    <span className="detail-item-label" style={{ color: "#DC2626" }}>Rejection Reason</span>
                    <span className="detail-item-value" style={{ color: "#DC2626" }}>
                      {booking.rejectionReason}
                    </span>
                  </div>
                )}

                {booking.cancellationReason && (
                  <div className="detail-item">
                    <span className="detail-item-label">Cancellation Reason</span>
                    <span className="detail-item-value">{booking.cancellationReason}</span>
                  </div>
                )}
              </div>
            </article>
          </div>

          {/* Audit History Timeline */}
          {history.length > 0 && (
            <article className="detail-section-card">
              <h2 className="detail-section-title">
                <span aria-hidden="true">📜</span>
                <span>Status Change Audit Trail</span>
              </h2>

              <div style={{ display: "flex", flexDirection: "column", gap: "12px", marginTop: "12px" }}>
                {history.map((h, i) => (
                  <div
                    key={h.id || i}
                    style={{
                      padding: "12px 16px",
                      background: "var(--bg-subtle)",
                      border: "1px solid var(--border-color)",
                      borderRadius: "var(--radius-md)",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      flexWrap: "wrap",
                      gap: "8px",
                    }}
                  >
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <BookingStatusBadge status={h.newStatus} />
                        <span style={{ fontSize: "13px", fontWeight: 500 }}>
                          {h.remarks || "Status updated"}
                        </span>
                      </div>
                      <div style={{ fontSize: "12px", color: "var(--text-muted)", marginTop: "4px" }}>
                        Action by: {h.changedByName || "System"}
                      </div>
                    </div>

                    <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      {formatDateTime(h.timestamp)}
                    </span>
                  </div>
                ))}
              </div>
            </article>
          )}

          {/* Cancellation Modal */}
          <CancelBookingModal
            isOpen={isCancelModalOpen}
            booking={booking}
            onClose={() => setIsCancelModalOpen(false)}
            onConfirm={handleConfirmCancel}
            isSubmitting={isCancelling}
          />
        </>
      ) : null}
    </div>
  );
}
