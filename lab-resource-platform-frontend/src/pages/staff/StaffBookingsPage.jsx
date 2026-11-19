import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";
import {
  FiCalendar,
  FiClock,
  FiZap,
  FiTrash2,
  FiRepeat,
  FiCheck,
  FiX,
  FiCheckCircle,
  FiPlay,
  FiSquare,
  FiArrowUp,
  FiList,
} from "react-icons/fi";

const STATUS_CONFIG = {
  PENDING_APPROVAL: { bg: "#fef3c7", text: "#b45309", label: "Pending Approval" },
  CONFIRMED: { bg: "#dcfce7", text: "#15803d", label: "Confirmed" },
  IN_USE: { bg: "#e0f2fe", text: "#0369a1", label: "In Use" },
  COMPLETED: { bg: "#f3f4f6", text: "#374151", label: "Completed" },
  CANCELLED: { bg: "#fee2e2", text: "#b91c1c", label: "Cancelled" },
  NO_SHOW: { bg: "#ede9fe", text: "#6d28d9", label: "No Show" },
};

const ALL_STATUSES = [
  "PENDING_APPROVAL",
  "CONFIRMED",
  "IN_USE",
  "COMPLETED",
  "CANCELLED",
  "NO_SHOW",
];

export default function StaffBookingsPage() {
  const toast = useToast();
  const { user } = useAuth();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const isStaff = [
    "INSTITUTION_ADMINISTRATOR",
    "DEPARTMENT_HEAD",
    "LAB_MANAGER",
    "LAB_TECHNICIAN",
  ].includes(user?.role);

  const [activeTab, setActiveTab] = useState("bookings"); // "bookings" | "approvals" | "waitlist"
  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [bookings, setBookings] = useState([]);
  const [waitlist, setWaitlist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingWaitlist, setLoadingWaitlist] = useState(false);
  const [statusFilter, setStatusFilter] = useState("");
  const [updatingId, setUpdatingId] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search by student, equipment, or purpose...");
  }, [setSearchPlaceholder]);

  const loadColleges = useCallback(async () => {
    if (!isStaff) {
      try {
        const res = await api.get("/api/colleges");
        setColleges(res.data || []);
      } catch (err) {
        console.error("Could not load colleges", err);
      }
    }
  }, [isStaff]);

  const refreshBookingsQuietly = useCallback(async () => {
    try {
      const res = await api.get("/api/bookings");
      setBookings(res.data || []);
    } catch {
      // background sync
    }
  }, []);

  const loadBookings = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/bookings");
      setBookings(res.data || []);
    } catch (err) {
      toast.error("Could not load bookings list.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  const loadWaitlist = useCallback(async () => {
    setLoadingWaitlist(true);
    try {
      const res = await api.get("/api/waitlist/all");
      setWaitlist(res.data || []);
    } catch (err) {
      console.error("Could not load waitlist", err);
    } finally {
      setLoadingWaitlist(false);
    }
  }, []);

  useEffect(() => {
    loadColleges();
    loadBookings();
    loadWaitlist();

    const interval = setInterval(() => {
      refreshBookingsQuietly();
      loadWaitlist();
    }, 10000);

    let ws = null;
    try {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      ws = new WebSocket(`${protocol}//${window.location.hostname}:8080/ws/utilization`);
      ws.onmessage = () => {
        refreshBookingsQuietly();
        loadWaitlist();
      };
    } catch (e) {}

    return () => {
      clearInterval(interval);
      if (ws) ws.close();
    };
  }, [loadColleges, loadBookings, loadWaitlist, refreshBookingsQuietly]);

  async function handleStatusChange(booking, newStatus) {
    if (!newStatus || newStatus === booking.status) return;
    setUpdatingId(booking.id);
    try {
      await api.patch(`/api/bookings/${booking.id}/status`, { status: newStatus });
      setBookings((prev) =>
        prev.map((b) => (b.id === booking.id ? { ...b, status: newStatus } : b))
      );
      toast.success(`Booking #${booking.id} status updated to ${STATUS_CONFIG[newStatus]?.label || newStatus}.`);
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to update booking status.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Manager Approval workflow: Approve
  async function handleApproveBooking(b) {
    setUpdatingId(b.id);
    try {
      await api.post(`/api/bookings/${b.id}/approve`);
      toast.success(`Booking #${b.id} for "${b.equipmentName}" approved successfully!`);
      setBookings((prev) =>
        prev.map((item) => (item.id === b.id ? { ...item, status: "CONFIRMED" } : item))
      );
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to approve booking.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Manager Approval workflow: Reject
  async function handleRejectBooking(b) {
    const reason = window.prompt(
      `Enter rejection reason for Booking #${b.id} (${b.equipmentName}):`,
      "Scheduling conflict or operational limitation"
    );
    if (reason === null) return;

    setUpdatingId(b.id);
    try {
      await api.post(`/api/bookings/${b.id}/reject`, {
        reason: reason.trim() || "Rejected by laboratory administrator",
      });
      toast.success(`Booking #${b.id} was rejected.`);
      setBookings((prev) =>
        prev.map((item) => (item.id === b.id ? { ...item, status: "CANCELLED" } : item))
      );
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to reject booking.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Waitlist Promotion
  async function handlePromoteWaitlist(w) {
    if (!window.confirm(`Promote waitlist candidate #${w.id} ("${w.userName}") to a confirmed booking?`)) {
      return;
    }
    setUpdatingId(w.id);
    try {
      await api.post(`/api/waitlist/${w.id}/promote`);
      toast.success(`Waitlist candidate #${w.id} promoted to confirmed booking!`);
      loadWaitlist();
      loadBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to promote waitlist candidate.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Remove from Waitlist
  async function handleRemoveWaitlist(w) {
    if (!window.confirm(`Remove waitlist entry #${w.id}?`)) return;
    setUpdatingId(w.id);
    try {
      await api.delete(`/api/waitlist/${w.id}`);
      toast.success(`Waitlist entry #${w.id} removed.`);
      setWaitlist((prev) => prev.filter((item) => item.id !== w.id));
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to remove waitlist entry.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Check-in / Start Session directly from booking
  async function handleBookingCheckIn(b) {
    setUpdatingId(b.id);
    try {
      await api.post("/api/utilization/check-in", {
        bookingId: b.id,
        equipmentId: b.equipmentId,
        notes: "Started session via Bookings Management",
      });
      toast.success(`Session started on Booking #${b.id}. Equipment is now marked IN USE.`);
      loadBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to check in session.");
    } finally {
      setUpdatingId(null);
    }
  }

  // Check-out / Stop Session directly from booking
  async function handleBookingCheckOut(b) {
    setUpdatingId(b.id);
    try {
      await api.post(`/api/utilization/booking/${b.id}/check-out`, {
        notes: "Session stopped via Bookings Management",
      });
      toast.success(`Session completed on Booking #${b.id}. Usage duration recorded.`);
      loadBookings();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to check out session.");
    } finally {
      setUpdatingId(null);
    }
  }

  function formatSchedule(start, end) {
    if (!start) return "—";
    const opts = {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      timeZone: "Asia/Kolkata",
    };
    const s = new Date(start).toLocaleString("en-IN", opts);
    const e = end ? new Date(end).toLocaleString("en-IN", opts) : "";
    return (
      <div style={{ fontSize: "0.85rem", lineHeight: 1.45 }}>
        <div>
          <strong>Start:</strong> {s}
        </div>
        {e && (
          <div>
            <strong>End:</strong> {e}
          </div>
        )}
      </div>
    );
  }

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = isStaff
    ? user?.collegeName || "Your College"
    : selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  const pendingBookings = bookings.filter((b) => b.status === "PENDING_APPROVAL");
  const activeWaitlists = waitlist.filter((w) => w.status === "WAITING");

  const displayedBookings = bookings.filter((b) => {
    if (activeTab === "approvals" && b.status !== "PENDING_APPROVAL") {
      return false;
    }
    if (!isStaff && selectedCollegeId) {
      if (String(b.collegeId) !== String(selectedCollegeId)) {
        return false;
      }
    }
    if (statusFilter && b.status !== statusFilter) {
      return false;
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchEquip = b.equipmentName?.toLowerCase().includes(q);
      const matchUser = b.userName?.toLowerCase().includes(q) || b.userEmail?.toLowerCase().includes(q);
      const matchPurpose = b.purpose?.toLowerCase().includes(q);
      if (!matchEquip && !matchUser && !matchPurpose) {
        return false;
      }
    }
    return true;
  });

  const filteredWaitlist = activeWaitlists.filter((w) => {
    if (!isStaff && selectedCollegeId) {
      if (String(w.collegeId) !== String(selectedCollegeId)) {
        return false;
      }
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchEquip = w.equipmentName?.toLowerCase().includes(q);
      const matchUser = w.userName?.toLowerCase().includes(q) || w.userEmail?.toLowerCase().includes(q);
      const matchPurpose = w.purpose?.toLowerCase().includes(q);
      if (!matchEquip && !matchUser && !matchPurpose) {
        return false;
      }
    }
    return true;
  });

  return (
    <div className="admin-page-container">
      {/* Page Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">STUDENT RESERVATIONS &amp; WORKFLOWS</p>
          <h1 className="admin-title">
            {isStaff ? `${currentCollegeName} Bookings &amp; Approvals` : "Bookings &amp; Waitlist Management"}
          </h1>
          <p className="admin-subtitle">
            {isStaff
              ? `Review, approve, or reject student equipment reservations for ${currentCollegeName}. Monitor the active waitlist queue and dispatch live sessions.`
              : "Review and manage laboratory equipment bookings across all colleges. Perform approval workflows, waitlist queue monitoring, and telemetry check-ins."}
          </p>
        </div>

        <div className="admin-header-actions">
          {activeTab !== "waitlist" && (
            <FilterDropdown
              colleges={colleges}
              selectedCollegeId={selectedCollegeId}
              onCollegeChange={setSelectedCollegeId}
              statuses={ALL_STATUSES}
              selectedStatus={statusFilter}
              onStatusChange={setStatusFilter}
              statusLabels={STATUS_CONFIG}
              showCollege={!isStaff && colleges.length > 0}
              showCategory={false}
              showStatus={activeTab === "bookings"}
            />
          )}
        </div>
      </div>

      {/* Tabs Switcher: All Bookings, Pending Approvals, Waitlist Queue */}
      <div className="bh-nav-tabs" style={{ marginBottom: "20px" }}>
        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "bookings" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("bookings");
            setStatusFilter("");
          }}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiCalendar size={15} /> All Bookings ({bookings.length})
        </button>

        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "approvals" ? "active" : ""}`}
          onClick={() => {
            setActiveTab("approvals");
            setStatusFilter("");
          }}
          style={{ position: "relative", display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiClock size={15} /> Pending Approvals ({pendingBookings.length})
          {pendingBookings.length > 0 && (
            <span
              style={{
                display: "inline-block",
                width: "8px",
                height: "8px",
                borderRadius: "50%",
                backgroundColor: "#f59e0b",
                marginLeft: "8px",
              }}
            />
          )}
        </button>

        <button
          type="button"
          className={`bh-nav-tab ${activeTab === "waitlist" ? "active" : ""}`}
          onClick={() => setActiveTab("waitlist")}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiZap size={15} /> Waitlist Queue ({activeWaitlists.length} Active)
        </button>
      </div>

      {/* Main Tab Content */}
      {activeTab === "waitlist" ? (
        /* Waitlist Queue Tab */
        loadingWaitlist ? (
          <div className="admin-loading-state">Loading waitlist entries...</div>
        ) : filteredWaitlist.length === 0 ? (
          <div className="admin-empty-card">
            <FiList size={36} style={{ color: "#9ca3af", marginBottom: "12px" }} />
            <h3>No waitlist entries</h3>
            <p>
              There are currently no active waitlist requests for {currentCollegeName}. When high-demand slots are contested, students queuing will appear here.
            </p>
          </div>
        ) : (
          <div className="admin-table-wrapper">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Queue Pos</th>
                  <th>Equipment</th>
                  <th>College</th>
                  <th>Student</th>
                  <th>Desired Slot (IST)</th>
                  <th>Purpose</th>
                  <th>Status</th>
                  <th>Manager Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredWaitlist.map((w) => {
                  const isUpdating = updatingId === w.id;
                  const isWaiting = w.status === "WAITING";

                  return (
                    <tr key={w.id}>
                      <td>
                        <span
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            width: "32px",
                            height: "32px",
                            borderRadius: "50%",
                            backgroundColor: isWaiting ? "#e0f2fe" : "#f3f4f6",
                            color: isWaiting ? "#0369a1" : "#6b7280",
                            fontWeight: 700,
                            fontSize: "0.85rem",
                          }}
                        >
                          #{w.queuePosition || 1}
                        </span>
                      </td>
                      <td>
                        <strong style={{ color: "#111827", display: "block" }}>
                          {w.equipmentName}
                        </strong>
                        <span style={{ fontSize: "0.78rem", color: "#6b7280" }}>
                          ID: #{w.equipmentId}
                        </span>
                      </td>
                      <td>
                        <span className="admin-badge-college">{w.collegeName || "General"}</span>
                      </td>
                      <td>
                        <div style={{ lineHeight: 1.3 }}>
                          <strong>{w.userName || "Student"}</strong>
                          <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{w.userEmail}</div>
                        </div>
                      </td>
                      <td>{formatSchedule(w.desiredStartTime, w.desiredEndTime)}</td>
                      <td>
                        <span style={{ fontSize: "0.82rem", color: "#4b5563" }}>
                          {w.purpose || "—"}
                        </span>
                      </td>
                      <td>
                        <span
                          className="admin-status-pill"
                          style={{
                            backgroundColor:
                              w.status === "WAITING"
                                ? "#fef3c7"
                                : w.status === "PROMOTED"
                                ? "#dcfce7"
                                : "#f3f4f6",
                            color:
                              w.status === "WAITING"
                                ? "#b45309"
                                : w.status === "PROMOTED"
                                ? "#15803d"
                                : "#6b7280",
                          }}
                        >
                          {w.status}
                        </span>
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                          {isWaiting && (
                            <button
                              type="button"
                              className="staff-promote-btn"
                              disabled={isUpdating}
                              onClick={() => handlePromoteWaitlist(w)}
                              title="Promote directly into a confirmed reservation"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                            >
                              <FiArrowUp size={14} /> Promote
                            </button>
                          )}
                          <button
                            type="button"
                            className="staff-reject-btn"
                            disabled={isUpdating}
                            onClick={() => handleRemoveWaitlist(w)}
                            title="Remove from waitlist"
                            style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                          >
                            <FiTrash2 size={14} /> Remove
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )
      ) : (
        /* Bookings & Approvals Tab */
        loading ? (
          <div className="admin-loading-state">Loading bookings data...</div>
        ) : displayedBookings.length === 0 ? (
          <div className="admin-empty-card">
            <h3>No bookings found</h3>
            <p>
              {activeTab === "approvals"
                ? `No pending approvals found for ${currentCollegeName}. All student requests are currently processed.`
                : isStaff || selectedCollegeId
                ? `There are no student bookings for ${currentCollegeName} matching the selected filters.`
                : "No student bookings match the selected filters."}
            </p>
          </div>
        ) : (
          <div className="admin-table-wrapper">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Equipment</th>
                  <th>College</th>
                  <th>Student / Researcher</th>
                  <th>Schedule (IST)</th>
                  <th>Approval / Status Action</th>
                  <th>Live Telemetry</th>
                </tr>
              </thead>
              <tbody>
                {displayedBookings.map((b) => {
                  const statusConf = STATUS_CONFIG[b.status] || {
                    bg: "#f3f4f6",
                    text: "#374151",
                    label: b.status,
                  };
                  const isUpdating = updatingId === b.id;
                  const isPending = b.status === "PENDING_APPROVAL";

                  return (
                    <tr key={b.id} style={isPending ? { backgroundColor: "#fffbeb" } : {}}>
                      <td>#{b.id}</td>
                      <td>
                        <strong style={{ display: "block", color: "#111827" }}>
                          {b.equipmentName || `Equipment #${b.equipmentId}`}
                        </strong>
                        {b.purpose && (
                          <span style={{ fontSize: "0.8rem", color: "#6b7280", display: "block" }}>
                            Purpose: {b.purpose}
                          </span>
                        )}
                        {b.isRecurring && (
                          <span className="staff-recurring-badge" style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                            <FiRepeat size={13} /> Recurring ({b.recurrenceType || "Weekly"})
                          </span>
                        )}
                      </td>
                      <td>
                        <span className="admin-badge-college">{b.collegeName || "General"}</span>
                      </td>
                      <td>
                        <div style={{ lineHeight: 1.3 }}>
                          <strong>{b.userName || "Unknown Student"}</strong>
                          <div style={{ fontSize: "0.8rem", color: "#6b7280" }}>{b.userEmail}</div>
                        </div>
                      </td>
                      <td>{formatSchedule(b.startTime, b.endTime)}</td>
                      <td>
                        {/* Approval / Rejection Workflow for Pending Requests */}
                        {isPending ? (
                          <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                            <button
                              type="button"
                              className="staff-approve-btn"
                              disabled={isUpdating}
                              onClick={() => handleApproveBooking(b)}
                              title="Approve booking request"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                            >
                              <FiCheck size={14} /> Approve
                            </button>

                            <button
                              type="button"
                              className="staff-reject-btn"
                              disabled={isUpdating}
                              onClick={() => handleRejectBooking(b)}
                              title="Reject booking request with reason"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                            >
                              <FiX size={14} /> Reject
                            </button>
                          </div>
                        ) : (
                          <div className="booking-status-inline-container">
                            <select
                              className="booking-status-inline-select"
                              value={b.status}
                              disabled={isUpdating}
                              style={{
                                backgroundColor: statusConf.bg,
                                color: statusConf.text,
                                borderColor: statusConf.text,
                              }}
                              onChange={(e) => handleStatusChange(b, e.target.value)}
                            >
                              {ALL_STATUSES.map((st) => (
                                <option key={st} value={st}>
                                  {STATUS_CONFIG[st]?.label || st}
                                </option>
                              ))}
                            </select>
                          </div>
                        )}
                      </td>
                      <td>
                        {/* Check-in / Check-out session buttons */}
                        {b.status === "CONFIRMED" ? (
                          new Date() >= new Date(b.startTime) ? (
                            <button
                              type="button"
                              className="staff-checkin-btn"
                              disabled={isUpdating}
                              onClick={() => handleBookingCheckIn(b)}
                              title="Start live session and mark equipment IN USE"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                            >
                              <FiPlay size={13} /> Check-In
                            </button>
                          ) : (
                            <span style={{ fontSize: "0.75rem", color: "#64748b", display: "inline-flex", alignItems: "center", gap: "4px" }} title="Session time has not arrived yet">
                              <FiClock size={12} /> Starts {new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </span>
                          )
                        ) : b.status === "IN_USE" ? (
                          <button
                            type="button"
                            className="staff-checkout-btn"
                            disabled={isUpdating}
                            onClick={() => handleBookingCheckOut(b)}
                            title="Stop session and record duration"
                            style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                          >
                            <FiSquare size={13} /> Check-Out
                          </button>
                        ) : (
                          <span style={{ fontSize: "0.78rem", color: "#9ca3af", display: "inline-flex", alignItems: "center", gap: "3px" }}>
                            {b.status === "COMPLETED" ? (
                              <>
                                Logged <FiCheckCircle size={12} color="#16a34a" />
                              </>
                            ) : (
                              "—"
                            )}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )
      )}
    </div>
  );
}
