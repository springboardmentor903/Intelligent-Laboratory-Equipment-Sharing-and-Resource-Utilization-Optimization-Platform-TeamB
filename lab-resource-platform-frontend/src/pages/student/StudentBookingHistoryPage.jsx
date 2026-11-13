import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";
import {
  FiCalendar,
  FiClock,
  FiRepeat,
  FiAlertCircle,
  FiX,
  FiInbox,
  FiPlay,
  FiSquare,
  FiBell,
  FiArrowRight,
} from "react-icons/fi";

const STATUS_META = {
  PENDING_APPROVAL: { label: "Pending Approval", color: "status-pending" },
  CONFIRMED: { label: "Confirmed", color: "status-confirmed" },
  IN_USE: { label: "In Use", color: "status-in-use" },
  COMPLETED: { label: "Completed", color: "status-completed" },
  CANCELLED: { label: "Cancelled", color: "status-cancelled" },
  NO_SHOW: { label: "No Show", color: "status-no-show" },
};

const ALL_STATUS_KEYS = [
  "PENDING_APPROVAL",
  "CONFIRMED",
  "IN_USE",
  "COMPLETED",
  "CANCELLED",
];

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
  const toast = useToast();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const [activeTab, setActiveTab] = useState("bookings"); // "bookings" or "waitlist"

  const [bookings, setBookings] = useState([]);
  const [waitlist, setWaitlist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [sessionActionId, setSessionActionId] = useState(null);
  const [cancelError, setCancelError] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [leavingWaitlistId, setLeavingWaitlistId] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search your reservations by equipment, purpose, status...");
  }, [setSearchPlaceholder]);

  const fetchBookingsQuietly = useCallback(async () => {
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
      // background sync
    }
  }, []);

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

    const interval = setInterval(fetchBookingsQuietly, 10000);
    let ws = null;
    try {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      ws = new WebSocket(`${protocol}//${window.location.hostname}:8080/ws/utilization`);
      ws.onmessage = () => {
        fetchBookingsQuietly();
      };
    } catch (e) {}

    return () => {
      clearInterval(interval);
      if (ws) ws.close();
    };
  }, [fetchBookings, fetchWaitlist, fetchBookingsQuietly]);

  async function handleCancel(bookingId) {
    if (!window.confirm("Are you sure you want to cancel this booking?"))
      return;
    setCancellingId(bookingId);
    setCancelError(null);
    try {
      await api.post(`/api/bookings/${bookingId}/cancel`);
      await fetchBookings();
      toast.success("Booking cancelled successfully.");
    } catch (err) {
      const msg = err?.response?.data?.message || "Failed to cancel booking. Please try again.";
      setCancelError(msg);
      toast.error(msg);
    } finally {
      setCancellingId(null);
    }
  }

  // Student Check-In (Start session)
  async function handleStudentCheckIn(booking) {
    setSessionActionId(booking.id);
    try {
      await api.post("/api/utilization/check-in", {
        bookingId: booking.id,
        equipmentId: booking.equipmentId,
        notes: "Student started laboratory session",
      });
      toast.success("Session started! Equipment is now IN USE.");
      fetchBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Check-in failed.");
    } finally {
      setSessionActionId(null);
    }
  }

  // Student Check-Out (Stop session)
  async function handleStudentCheckOut(booking) {
    setSessionActionId(booking.id);
    try {
      await api.post(`/api/utilization/booking/${booking.id}/check-out`, {
        notes: "Student finished session",
      });
      toast.success("Session completed! Usage duration logged.");
      fetchBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Check-out failed.");
    } finally {
      setSessionActionId(null);
    }
  }

  async function handleLeaveWaitlist(id) {
    if (!window.confirm("Are you sure you want to leave this waitlist?")) return;
    setLeavingWaitlistId(id);
    try {
      await api.delete(`/api/waitlist/${id}`);
      setWaitlist((prev) => prev.filter((w) => w.id !== id));
      toast.success("Removed from waitlist.");
    } catch {
      toast.error("Failed to leave waitlist.");
    } finally {
      setLeavingWaitlistId(null);
    }
  }

  const activeWaitlists = waitlist.filter(
    (w) => w.status === "WAITING"
  );

  // Compute stats
  const stats = {
    total: bookings.length,
    pending: bookings.filter((b) => b.status === "PENDING_APPROVAL").length,
    confirmed: bookings.filter((b) => b.status === "CONFIRMED").length,
    inUse: bookings.filter((b) => b.status === "IN_USE").length,
    completed: bookings.filter((b) => b.status === "COMPLETED").length,
    cancelled: bookings.filter((b) => b.status === "CANCELLED").length,
    waitlistCount: activeWaitlists.length,
  };

  const filtered = bookings.filter((b) => {
    if (statusFilter && statusFilter !== "ALL" && b.status !== statusFilter) {
      return false;
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchName = b.equipmentName?.toLowerCase().includes(q);
      const matchPurpose = b.purpose?.toLowerCase().includes(q);
      const matchCollege = b.collegeName?.toLowerCase().includes(q);
      const matchStatus = b.status?.toLowerCase().includes(q);
      if (!matchName && !matchPurpose && !matchCollege && !matchStatus) {
        return false;
      }
    }
    return true;
  });

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
      <div className="bh-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "16px" }}>
        <div>
          <h1 className="bh-title">My Bookings &amp; Waitlists</h1>
          <p className="bh-subtitle">
            Track all your reservations, check-in to active sessions, and monitor waitlist queue positions.
          </p>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {activeTab === "bookings" && (
            <FilterDropdown
              statuses={ALL_STATUS_KEYS}
              selectedStatus={statusFilter}
              onStatusChange={(st) => setStatusFilter(st || "ALL")}
              statusLabels={STATUS_META}
              showCollege={false}
              showCategory={false}
              showStatus={true}
            />
          )}

          <button
            type="button"
            className="bh-browse-btn"
            onClick={() => navigate("/dashboard/student/equipments")}
          >
            + New Booking
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="bh-nav-tabs">
        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "bookings" ? "active" : ""}`}
          onClick={() => setActiveTab("bookings")}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiCalendar size={15} /> Reservations ({stats.total})
        </button>
        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "waitlist" ? "active" : ""}`}
          onClick={() => setActiveTab("waitlist")}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiClock size={15} /> Active Waitlists ({stats.waitlistCount})
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
              <span className="bh-stat-label">Confirmed</span>
            </div>
            <div className="bh-stat-card bh-stat-inuse" style={{ background: "#e0f2fe", borderColor: "#bae6fd" }}>
              <span className="bh-stat-num" style={{ color: "#0284c7" }}>{stats.inUse}</span>
              <span className="bh-stat-label">In Use (Active)</span>
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

          {cancelError && (
            <div className="toast-item toast-error" role="alert" style={{ margin: "16px 0" }}>
              <div className="toast-icon" style={{ display: "inline-flex", alignItems: "center" }}><FiX size={16} /></div>
              <div className="toast-message">{cancelError}</div>
            </div>
          )}

          {error && (
            <div className="toast-item toast-error" role="alert" style={{ margin: "16px 0" }}>
              <div className="toast-icon" style={{ display: "inline-flex", alignItems: "center" }}><FiX size={16} /></div>
              <div className="toast-message" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span>{error}</span>
                <button
                  type="button"
                  className="bh-retry-btn"
                  onClick={fetchBookings}
                >
                  Retry
                </button>
              </div>
            </div>
          )}

          {/* Bookings List */}
          {filtered.length === 0 ? (
            <div className="bh-empty">
              <div className="bh-empty-icon" style={{ display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
                <FiInbox size={36} color="#9ca3af" />
              </div>
              <h3>
                {statusFilter === "ALL"
                  ? "No bookings found"
                  : `No ${STATUS_META[statusFilter]?.label || statusFilter} bookings`}
              </h3>
              <p>
                {statusFilter === "ALL"
                  ? "You have not made any bookings yet, or none match your search."
                  : `No bookings match your current filter and search query.`}
              </p>
            </div>
          ) : (
            <div className="bh-list">
              {filtered.map((booking) => {
                const meta = STATUS_META[booking.status] || {
                  label: booking.status,
                  color: "status-default",
                };
                const isCancelling = cancellingId === booking.id;
                const isActioning = sessionActionId === booking.id;
                const canCancel =
                  booking.status === "PENDING_APPROVAL" ||
                  booking.status === "CONFIRMED";

                const now = new Date();
                const bookingStart = booking.startTime ? new Date(booking.startTime) : null;
                const bookingEnd = booking.endTime ? new Date(booking.endTime) : null;
                // Check-in is only unlocked when the reserved start time has arrived and slot has not expired
                const isTimeArrived = bookingStart ? now >= bookingStart : false;
                const isExpired = bookingEnd ? now > bookingEnd : false;
                const canCheckIn = booking.status === "CONFIRMED" && isTimeArrived && !isExpired;
                const isWaitingForStartTime = booking.status === "CONFIRMED" && !isTimeArrived;
                const canCheckOut = booking.status === "IN_USE";

                return (
                  <div key={booking.id} className="bh-card">
                    <div className="bh-card-header">
                      <div className="bh-card-title-group">
                        <h3 className="bh-equipment-name">
                          {booking.equipmentName ||
                            `Equipment #${booking.equipmentId}`}
                        </h3>
                        {booking.collegeName && (
                          <span className="bh-college-badge">
                            {booking.collegeName}
                          </span>
                        )}
                        {booking.isRecurring && (
                          <span className="bh-recurring-badge" style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                            <FiRepeat size={12} /> Recurring ({booking.recurrenceType || "Weekly"})
                          </span>
                        )}
                      </div>
                      <span className={`bh-status-pill ${meta.color}`}>
                        {meta.label}
                      </span>
                    </div>

                    <div className="bh-card-body">
                      <div className="bh-time-row">
                        <div className="bh-time-col">
                          <span className="bh-time-label">START (IST)</span>
                          <span className="bh-time-val">
                            {formatDateTime(booking.startTime)}
                          </span>
                        </div>
                        <span className="bh-time-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
                          <FiArrowRight size={14} />
                        </span>
                        <div className="bh-time-col">
                          <span className="bh-time-label">END (IST)</span>
                          <span className="bh-time-val">
                            {formatDateTime(booking.endTime)}
                          </span>
                        </div>
                        {booking.startTime && booking.endTime && (
                          <span className="bh-duration-pill">
                            {formatDuration(
                              booking.startTime,
                              booking.endTime
                            )}
                          </span>
                        )}
                      </div>

                      {booking.purpose && (
                        <p className="bh-purpose">
                          <strong>Purpose:</strong> {booking.purpose}
                        </p>
                      )}

                      {booking.cancellationReason && (
                        <p className="bh-cancel-reason">
                          <strong>Cancellation Reason:</strong>{" "}
                          {booking.cancellationReason}
                        </p>
                      )}

                      {booking.rejectionReason && (
                        <p className="bh-reject-reason">
                          <strong>Rejection Reason:</strong>{" "}
                          {booking.rejectionReason}
                        </p>
                      )}
                    </div>

                    <div className="bh-card-footer">
                      <div className="bh-session-action-group">
                        {canCheckIn && (
                          <button
                            type="button"
                            className="bh-checkin-btn"
                            disabled={isActioning}
                            onClick={() => handleStudentCheckIn(booking)}
                            title="Start laboratory session and mark equipment IN USE"
                          >
                            <FiPlay size={13} style={{ marginRight: 6 }} />
                            {isActioning ? "Starting..." : "Check-In (Start Session)"}
                          </button>
                        )}

                        {isWaitingForStartTime && (
                          <div className="bh-session-locked-pill" title="Check-in unlocks when your reserved time slot arrives">
                            <FiClock size={13} style={{ marginRight: 6 }} />
                            <span>Check-in available at {formatDateTime(booking.startTime)}</span>
                          </div>
                        )}

                        {canCheckOut && (
                          <button
                            type="button"
                            className="bh-checkout-btn"
                            disabled={isActioning}
                            onClick={() => handleStudentCheckOut(booking)}
                            title="Complete laboratory session and log usage duration"
                          >
                            <FiSquare size={13} style={{ marginRight: 6 }} />
                            {isActioning ? "Completing..." : "Check-Out (Complete Session)"}
                          </button>
                        )}
                      </div>

                      {canCancel && (
                        <button
                          type="button"
                          className="bh-cancel-btn"
                          disabled={isCancelling}
                          onClick={() => handleCancel(booking.id)}
                        >
                          {isCancelling ? "Cancelling…" : "Cancel Reservation"}
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      ) : (
        /* Waitlist Tab */
        <div className="bh-waitlist-section">
          {activeWaitlists.length === 0 ? (
            <div className="bh-empty">
              <div className="bh-empty-icon">⏳</div>
              <h3>No active waitlist entries</h3>
              <p>
                When equipment you want to use is fully booked, you can join
                its waitlist to be queued for next availability.
              </p>
            </div>
          ) : (
            <div className="bh-list">
              {activeWaitlists.map((w) => (
                <div key={w.id} className="bh-card waitlist-card">
                  <div className="bh-card-header">
                    <div>
                      <h3 className="bh-equipment-name">
                        {w.equipmentName || `Equipment #${w.equipmentId}`}
                      </h3>
                      {w.collegeName && (
                        <span className="bh-college-badge">
                          {w.collegeName}
                        </span>
                      )}
                    </div>
                    <span className="bh-status-pill status-waitlist">
                      Position #{w.queuePosition}
                    </span>
                  </div>

                  <div className="bh-card-body">
                    <div className="bh-time-row">
                      <div className="bh-time-col">
                        <span className="bh-time-label">DESIRED START</span>
                        <span className="bh-time-val">
                          {formatDateTime(w.desiredStartTime)}
                        </span>
                      </div>
                      <span className="bh-time-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
                        <FiArrowRight size={14} />
                      </span>
                      <div className="bh-time-col">
                        <span className="bh-time-label">DESIRED END</span>
                        <span className="bh-time-val">
                          {formatDateTime(w.desiredEndTime)}
                        </span>
                      </div>
                    </div>
                    {w.purpose && (
                      <p className="bh-purpose">
                        <strong>Purpose:</strong> {w.purpose}
                      </p>
                    )}
                  </div>

                  <div className="bh-card-footer">
                    <button
                      type="button"
                      className="bh-cancel-btn"
                      disabled={leavingWaitlistId === w.id}
                      onClick={() => handleLeaveWaitlist(w.id)}
                    >
                      {leavingWaitlistId === w.id
                        ? "Leaving..."
                        : "Leave Waitlist"}
                    </button>
                  </div>
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
