import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

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

  const isStaff = [
    "INSTITUTION_ADMINISTRATOR",
    "DEPARTMENT_HEAD",
    "LAB_MANAGER",
    "LAB_TECHNICIAN",
  ].includes(user?.role);

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [updatingId, setUpdatingId] = useState(null);

  const loadColleges = useCallback(async () => {
    // Only System Admin needs full list of colleges to filter
    if (!isStaff) {
      try {
        const res = await api.get("/api/colleges");
        setColleges(res.data || []);
      } catch (err) {
        console.error("Could not load colleges", err);
      }
    }
  }, [isStaff]);

  const loadBookings = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/bookings");
      setBookings(res.data || []);
    } catch (err) {
      toast.error("Could not load bookings list. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadColleges();
    loadBookings();

    // Poll periodically every 15 seconds to reflect auto-scheduler updates (e.g., auto cancellation / auto completion)
    const interval = setInterval(() => {
      api.get("/api/bookings").then((res) => {
        if (Array.isArray(res.data)) {
          setBookings(res.data);
        }
      }).catch(() => {});
    }, 15000);

    return () => clearInterval(interval);
  }, [loadColleges, loadBookings]);

  async function handleDirectStatusChange(booking, newStatus) {
    if (!newStatus || newStatus === booking.status) return;

    let reason = undefined;
    if (newStatus === "CANCELLED" || newStatus === "NO_SHOW") {
      const enteredReason = window.prompt(`Optional reason for setting status to ${STATUS_CONFIG[newStatus]?.label || newStatus}:`);
      if (enteredReason === null) return; // User cancelled prompt
      reason = enteredReason.trim() || undefined;
    }

    setUpdatingId(booking.id);
    try {
      await api.patch(`/api/bookings/${booking.id}/status`, {
        status: newStatus,
        reason: reason,
      });

      setBookings((prev) =>
        prev.map((b) =>
          b.id === booking.id
            ? {
                ...b,
                status: newStatus,
                rejectionReason: newStatus === "CANCELLED" ? reason : b.rejectionReason,
              }
            : b
        )
      );
      toast.success(`Booking #${booking.id} status changed to ${STATUS_CONFIG[newStatus]?.label || newStatus}.`);
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to update booking status.");
    } finally {
      setUpdatingId(null);
    }
  }

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = isStaff
    ? user?.collegeName || "Your College"
    : selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  // Filter bookings by selected college (for System Admin), status, and search query
  const filteredBookings = bookings.filter((b) => {
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
      const matchUser = b.userName?.toLowerCase().includes(q);
      const matchEmail = b.userEmail?.toLowerCase().includes(q);
      const matchPurpose = b.purpose?.toLowerCase().includes(q);
      if (!matchEquip && !matchUser && !matchEmail && !matchPurpose) {
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
          <p className="admin-eyebrow">STUDENT RESERVATIONS &amp; LIFECYCLE</p>
          <h1 className="admin-title">
            {isStaff ? `${currentCollegeName} Bookings Management` : "Bookings Management"}
          </h1>
          <p className="admin-subtitle">
            {isStaff
              ? `Review and manage student reservation requests for ${currentCollegeName}. Requests arrive in Pending Approval by default. Unused confirmed bookings auto-cancel after 1 minute past start time in IST.`
              : "Student reservation requests arrive in Pending Approval by default. System Administrator has oversight across all colleges. Change status directly from the dropdown."}
          </p>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="admin-filter-bar">
        {/* Only System Admin sees the College Filter Dropdown */}
        {!isStaff && (
          <div className="admin-filter-group">
            <label htmlFor="staffCollegeSelect" className="admin-filter-label">
              Filter by College:
            </label>
            <select
              id="staffCollegeSelect"
              className="admin-select admin-college-select"
              value={selectedCollegeId}
              onChange={(e) => setSelectedCollegeId(e.target.value)}
            >
              <option value="">All Colleges ({colleges.length})</option>
              {colleges.map((col) => (
                <option key={col.id} value={col.id}>
                  {col.name} ({col.code || "COL"})
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="admin-filter-group">
          <label htmlFor="staffStatusSelect" className="admin-filter-label">
            Filter by Status:
          </label>
          <select
            id="staffStatusSelect"
            className="admin-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All Statuses</option>
            <option value="PENDING_APPROVAL">Pending Approval (Default)</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="IN_USE">In Use</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="NO_SHOW">No Show</option>
          </select>
        </div>

        <div className="admin-filter-group admin-search-group">
          <input
            type="text"
            className="admin-input"
            placeholder="Search by student, equipment, or purpose..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="admin-clear-btn"
              onClick={() => setSearchTerm("")}
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Active College Indicator */}
      <div className="admin-active-filter-badge">
        Showing student bookings for: <strong>{currentCollegeName}</strong> (
        {filteredBookings.length} {filteredBookings.length === 1 ? "booking" : "bookings"})
      </div>

      {/* Bookings Table */}
      {loading ? (
        <div className="admin-loading-state">Loading bookings data...</div>
      ) : filteredBookings.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No bookings found</h3>
          <p>
            {isStaff || selectedCollegeId
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
                <th>Student / User</th>
                <th>Schedule (IST)</th>
                <th>Status (Change Dropdown)</th>
              </tr>
            </thead>
            <tbody>
              {filteredBookings.map((b) => {
                const statusConf = STATUS_CONFIG[b.status] || {
                  bg: "#f3f4f6",
                  text: "#374151",
                  label: b.status,
                };
                const isUpdating = updatingId === b.id;

                return (
                  <tr key={b.id}>
                    <td>#{b.id}</td>
                    <td>
                      <strong style={{ display: "block", color: "#111827" }}>
                        {b.equipmentName || `Equipment #${b.equipmentId}`}
                      </strong>
                      {b.purpose && (
                        <span style={{ fontSize: "0.8rem", color: "#6b7280" }}>
                          Purpose: {b.purpose}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className="admin-badge-college">
                        {b.collegeName || "General"}
                      </span>
                    </td>
                    <td>
                      <div style={{ lineHeight: 1.3 }}>
                        <strong>{b.userName || "Unknown Student"}</strong>
                        <div style={{ fontSize: "0.78rem", color: "#6b7280" }}>
                          {b.userEmail}
                        </div>
                      </div>
                    </td>
                    <td>
                      <div style={{ fontSize: "0.85rem", lineHeight: 1.4 }}>
                        <div>
                          <strong>From:</strong>{" "}
                          {b.startTime ? new Date(b.startTime).toLocaleString("en-IN") : "N/A"}
                        </div>
                        <div>
                          <strong>To:</strong>{" "}
                          {b.endTime ? new Date(b.endTime).toLocaleString("en-IN") : "N/A"}
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="booking-status-dropdown-wrapper">
                        <select
                          className="booking-status-inline-select"
                          style={{
                            backgroundColor: statusConf.bg,
                            color: statusConf.text,
                            borderColor: statusConf.text,
                          }}
                          value={b.status}
                          disabled={isUpdating}
                          onChange={(e) => handleDirectStatusChange(b, e.target.value)}
                        >
                          {ALL_STATUSES.map((st) => (
                            <option key={st} value={st}>
                              {STATUS_CONFIG[st]?.label || st}
                            </option>
                          ))}
                        </select>
                        {isUpdating && <span className="status-saving-spinner">⏳</span>}
                        {b.rejectionReason && (
                          <div
                            style={{
                              fontSize: "0.74rem",
                              color: "#dc2626",
                              marginTop: 4,
                              maxWidth: 180,
                              lineHeight: 1.25,
                            }}
                          >
                            Note: {b.rejectionReason}
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
