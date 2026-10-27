import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import {
  getBookingsForApproval,
  approveBooking,
  rejectBooking,
  canApproveBookings,
} from "../../services/bookingService";
import BookingStatusBadge from "../../components/booking/BookingStatusBadge";
import "./booking.css";

export default function BookingApprovalsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("PENDING_APPROVAL");

  const [rejectingBooking, setRejectingBooking] = useState(null);
  const [rejectReason, setRejectReason] = useState("");
  const [isProcessing, setIsProcessing] = useState(false);

  const isAuthorized = canApproveBookings(user?.role);

  function reloadApprovals() {
    setLoading(true);
    setError(null);
    getBookingsForApproval({ status: statusFilter !== "ALL" ? statusFilter : null })
      .then((data) => {
        setBookings(data?.content || (Array.isArray(data) ? data : []));
      })
      .catch((err) => {
        console.error("Failed to load bookings for approval:", err);
        setError("Unable to retrieve booking requests for approval.");
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    if (!isAuthorized) return;
    let ignore = false;

    getBookingsForApproval({ status: statusFilter !== "ALL" ? statusFilter : null })
      .then((data) => {
        if (!ignore) {
          setBookings(data?.content || (Array.isArray(data) ? data : []));
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to load bookings for approval:", err);
          setError("Unable to retrieve booking requests for approval.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [statusFilter, isAuthorized]);

  if (!isAuthorized) {
    return (
      <div className="booking-page-container">
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">🚫</span>
          <h3>Access Restricted</h3>
          <p>
            Your account role does not have authorization to approve or manage laboratory equipment reservations.
          </p>
          <Link to="/dashboard" className="btn-primary">
            Return to Dashboard
          </Link>
        </div>
      </div>
    );
  }

  async function handleApprove(id, equipmentName) {
    setIsProcessing(true);
    try {
      await approveBooking(id);
      toast.success(`Booking #${id} for "${equipmentName}" approved successfully.`);
      reloadApprovals();
    } catch (err) {
      console.error("Failed to approve booking:", err);
      toast.error(err.response?.data?.message || "Failed to approve booking.");
    } finally {
      setIsProcessing(false);
    }
  }

  async function handleConfirmReject(e) {
    e.preventDefault();
    if (!rejectReason.trim()) {
      toast.error("Please provide a reason for rejecting the booking.");
      return;
    }

    setIsProcessing(true);
    try {
      await rejectBooking(rejectingBooking.id, rejectReason.trim());
      toast.success(`Booking #${rejectingBooking.id} rejected.`);
      setRejectingBooking(null);
      setRejectReason("");
      reloadApprovals();
    } catch (err) {
      console.error("Failed to reject booking:", err);
      toast.error(err.response?.data?.message || "Failed to reject booking.");
    } finally {
      setIsProcessing(false);
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

  return (
    <div className="booking-page-container">
      {/* Breadcrumb */}
      <nav className="booking-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <span className="current">Reservation Approvals</span>
      </nav>

      {/* Header */}
      <header className="booking-header">
        <div>
          <h1>Laboratory Reservation Approvals</h1>
          <p>
            Review and adjudicate pending equipment booking requests from researchers and students.
          </p>
        </div>

        <div className="booking-header-actions">
          <Link to="/dashboard/bookings/calendar" className="btn-secondary">
            <span>📅 Equipment Calendar</span>
          </Link>
        </div>
      </header>

      {/* Filter Toolbar */}
      <section className="bookings-toolbar">
        <div className="bookings-filter-tabs">
          <button
            type="button"
            className={`filter-tab-btn ${statusFilter === "PENDING_APPROVAL" ? "active" : ""}`}
            onClick={() => setStatusFilter("PENDING_APPROVAL")}
          >
            Pending Approval Only
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${statusFilter === "ALL" ? "active" : ""}`}
            onClick={() => setStatusFilter("ALL")}
          >
            All Managed Bookings
          </button>
        </div>

        <div>
          <button type="button" className="btn-secondary" onClick={reloadApprovals} style={{ padding: "6px 12px", fontSize: "13px" }}>
            🔄 Refresh Requests
          </button>
        </div>
      </section>

      {/* Approvals Table */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading approval requests...</p>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Approvals</h3>
          <p>{error}</p>
          <button type="button" className="btn-primary" onClick={reloadApprovals}>
            Retry
          </button>
        </div>
      ) : bookings.length === 0 ? (
        <div className="equipment-empty-state">
          <span className="state-icon" aria-hidden="true">✅</span>
          <h3>No Pending Approvals</h3>
          <p>All reservation requests for your assigned equipment have been processed.</p>
        </div>
      ) : (
        <div className="bookings-table-container">
          <table className="bookings-table">
            <thead>
              <tr>
                <th>Booking #</th>
                <th>Equipment</th>
                <th>Requested By</th>
                <th>Reserved Interval</th>
                <th>Purpose</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b.id}>
                  <td>#{b.id}</td>
                  <td>
                    <strong>{b.equipmentName || `Equipment #${b.equipmentId}`}</strong>
                  </td>
                  <td>
                    <div>{b.userName || "Researcher"}</div>
                    <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      {b.userEmail}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{formatDateTime(b.startTime)}</div>
                    <div style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                      to {formatDateTime(b.endTime)}
                    </div>
                  </td>
                  <td>
                    <span style={{ fontSize: "13px" }}>{b.purpose || "No remarks"}</span>
                  </td>
                  <td>
                    <BookingStatusBadge status={b.status} />
                  </td>
                  <td>
                    {b.status === "PENDING_APPROVAL" ? (
                      <div style={{ display: "flex", gap: "8px" }}>
                        <button
                          type="button"
                          className="btn-primary"
                          style={{ padding: "6px 12px", fontSize: "12px", background: "#059669", borderColor: "#059669" }}
                          onClick={() => handleApprove(b.id, b.equipmentName)}
                          disabled={isProcessing}
                        >
                          Approve
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          style={{ padding: "6px 12px", fontSize: "12px", color: "#DC2626", borderColor: "#FECACA" }}
                          onClick={() => setRejectingBooking(b)}
                          disabled={isProcessing}
                        >
                          Reject
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="btn-secondary"
                        style={{ padding: "4px 10px", fontSize: "12px" }}
                        onClick={() => navigate(`/dashboard/bookings/${b.id}`)}
                      >
                        Inspect
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Reject Reason Modal */}
      {rejectingBooking && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h3>Reject Reservation Request</h3>
              <p>
                Reject booking #{rejectingBooking.id} for{" "}
                <strong>{rejectingBooking.equipmentName}</strong> requested by{" "}
                {rejectingBooking.userName || rejectingBooking.userEmail}.
              </p>
            </div>

            <form onSubmit={handleConfirmReject}>
              <div className="form-group">
                <label className="form-label" htmlFor="reject-reason">
                  Rejection Reason <span className="required-star">*</span>
                </label>
                <textarea
                  id="reject-reason"
                  className="form-textarea"
                  rows={3}
                  placeholder="e.g. Scheduled instrument maintenance during this window..."
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  disabled={isProcessing}
                  required
                />
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => {
                    setRejectingBooking(null);
                    setRejectReason("");
                  }}
                  disabled={isProcessing}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary"
                  style={{ background: "#DC2626", borderColor: "#DC2626" }}
                  disabled={isProcessing}
                >
                  {isProcessing ? "Rejecting..." : "Confirm Rejection"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
