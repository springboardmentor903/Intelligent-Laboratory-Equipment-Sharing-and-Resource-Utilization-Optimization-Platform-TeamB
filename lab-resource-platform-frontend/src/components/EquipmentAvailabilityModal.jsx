import { useCallback, useEffect, useRef, useState } from "react";
import api from "../services/api";

// IST formatter helpers
const IST_ZONE = "Asia/Kolkata";

function toIST(instant) {
  return new Date(instant).toLocaleString("en-IN", {
    timeZone: IST_ZONE,
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

function toISTTimeOnly(instant) {
  return new Date(instant).toLocaleString("en-IN", {
    timeZone: IST_ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

function toISTDateLabel(instant) {
  return new Date(instant).toLocaleString("en-IN", {
    timeZone: IST_ZONE,
    weekday: "short",
    day: "2-digit",
    month: "short",
  });
}

const STATUS_COLORS = {
  CONFIRMED:       { bg: "#dbeafe", text: "#1d4ed8", border: "#93c5fd", label: "Confirmed" },
  PENDING_APPROVAL:{ bg: "#fef9c3", text: "#854d0e", border: "#fde047", label: "Pending Approval" },
  IN_USE:          { bg: "#e0f2fe", text: "#0369a1", border: "#7dd3fc", label: "In Use" },
  COMPLETED:       { bg: "#dcfce7", text: "#15803d", border: "#86efac", label: "Completed" },
  CANCELLED:       { bg: "#fee2e2", text: "#b91c1c", border: "#fca5a5", label: "Cancelled" },
};

function getStatusColor(status) {
  return STATUS_COLORS[status] || { bg: "#f3f4f6", text: "#374151", border: "#d1d5db", label: status };
}

// Group slots by IST date string
function groupSlotsByDay(slots) {
  const groups = {};
  slots.forEach((slot) => {
    const dayKey = new Date(slot.startTime).toLocaleDateString("en-IN", {
      timeZone: IST_ZONE,
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
    if (!groups[dayKey]) groups[dayKey] = [];
    groups[dayKey].push(slot);
  });
  return groups;
}

// Week label helper
function getWeekLabel(startDate) {
  const end = new Date(startDate);
  end.setDate(end.getDate() + 6);
  const opts = { day: "2-digit", month: "short", timeZone: IST_ZONE };
  const s = startDate.toLocaleDateString("en-IN", { ...opts, year: "numeric" });
  const e = end.toLocaleDateString("en-IN", opts);
  return `${s} – ${e}`;
}

export default function EquipmentAvailabilityModal({ equipmentId, equipmentName, onClose }) {
  const overlayRef = useRef(null);

  // Week navigation: start of current week in IST
  const [weekStart, setWeekStart] = useState(() => {
    const now = new Date();
    // Align to Monday of the current IST week
    const ist = new Date(now.toLocaleString("en-US", { timeZone: IST_ZONE }));
    const day = ist.getDay(); // 0=Sun
    const diff = day === 0 ? -6 : 1 - day;
    ist.setDate(ist.getDate() + diff);
    ist.setHours(0, 0, 0, 0);
    return ist;
  });

  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchSlots = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // End = weekStart + 7 days
      const endDate = new Date(weekStart);
      endDate.setDate(endDate.getDate() + 7);

      const startISO = weekStart.toISOString();
      const endISO = endDate.toISOString();

      const res = await api.get(
        `/api/equipment/${equipmentId}/availability?startDate=${startISO}&endDate=${endISO}`
      );
      setSlots(res.data || []);
    } catch (err) {
      setError("Could not load availability data. Please try again.");
      setSlots([]);
    } finally {
      setLoading(false);
    }
  }, [equipmentId, weekStart]);

  useEffect(() => {
    fetchSlots();
  }, [fetchSlots]);

  // Close on overlay click
  function handleOverlayClick(e) {
    if (e.target === overlayRef.current) onClose();
  }

  // Close on Escape key
  useEffect(() => {
    function onKey(e) { if (e.key === "Escape") onClose(); }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  function prevWeek() {
    setWeekStart((d) => {
      const n = new Date(d);
      n.setDate(n.getDate() - 7);
      return n;
    });
  }

  function nextWeek() {
    setWeekStart((d) => {
      const n = new Date(d);
      n.setDate(n.getDate() + 7);
      return n;
    });
  }

  function goToCurrentWeek() {
    const now = new Date();
    const ist = new Date(now.toLocaleString("en-US", { timeZone: IST_ZONE }));
    const day = ist.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    ist.setDate(ist.getDate() + diff);
    ist.setHours(0, 0, 0, 0);
    setWeekStart(ist);
  }

  // Active bookings = not cancelled/completed (these block availability)
  const activeSlots = slots.filter(
    (s) => s.status !== "CANCELLED" && s.status !== "COMPLETED"
  );
  const allSlots = slots; // show all including completed/cancelled for context

  const grouped = groupSlotsByDay(allSlots);
  const dayKeys = Object.keys(grouped);
  const weekLabel = getWeekLabel(weekStart);

  // Stats
  const bookedCount = activeSlots.length;
  const cancelledCount = slots.filter((s) => s.status === "CANCELLED").length;
  const completedCount = slots.filter((s) => s.status === "COMPLETED").length;

  return (
    <div
      className="avail-modal-overlay"
      ref={overlayRef}
      onClick={handleOverlayClick}
      role="dialog"
      aria-modal="true"
      aria-label={`Availability for ${equipmentName}`}
    >
      <div className="avail-modal-content">
        {/* Header */}
        <div className="avail-modal-header">
          <div className="avail-modal-title-group">
            <span className="avail-modal-icon">📅</span>
            <div>
              <h2 className="avail-modal-title">Booking Availability</h2>
              <p className="avail-modal-subtitle">{equipmentName}</p>
            </div>
          </div>
          <button
            type="button"
            className="avail-modal-close"
            onClick={onClose}
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Stats Row */}
        <div className="avail-stats-row">
          <div className="avail-stat-card avail-stat-booked">
            <span className="avail-stat-count">{bookedCount}</span>
            <span className="avail-stat-label">Active Bookings</span>
          </div>
          <div className="avail-stat-card avail-stat-completed">
            <span className="avail-stat-count">{completedCount}</span>
            <span className="avail-stat-label">Completed</span>
          </div>
          <div className="avail-stat-card avail-stat-cancelled">
            <span className="avail-stat-count">{cancelledCount}</span>
            <span className="avail-stat-label">Cancelled</span>
          </div>
          <div className="avail-stat-card avail-stat-total">
            <span className="avail-stat-count">{slots.length}</span>
            <span className="avail-stat-label">Total This Week</span>
          </div>
        </div>

        {/* Week Navigation */}
        <div className="avail-week-nav">
          <button type="button" className="avail-week-btn" onClick={prevWeek}>
            ‹ Prev Week
          </button>
          <div className="avail-week-label-group">
            <span className="avail-week-label">{weekLabel}</span>
            <button type="button" className="avail-today-btn" onClick={goToCurrentWeek}>
              This Week
            </button>
          </div>
          <button type="button" className="avail-week-btn" onClick={nextWeek}>
            Next Week ›
          </button>
        </div>

        {/* Body */}
        <div className="avail-modal-body">
          {loading ? (
            <div className="avail-loading">
              <div className="avail-spinner" />
              <span>Loading schedule...</span>
            </div>
          ) : error ? (
            <div className="avail-error">
              <span>⚠️ {error}</span>
              <button type="button" className="avail-retry-btn" onClick={fetchSlots}>
                Retry
              </button>
            </div>
          ) : slots.length === 0 ? (
            <div className="avail-empty">
              <span className="avail-empty-icon">✅</span>
              <h3>Fully Available This Week!</h3>
              <p>No bookings found for <strong>{weekLabel}</strong>. This equipment is free to book during this period.</p>
            </div>
          ) : (
            <div className="avail-days-list">
              {dayKeys.map((dayKey) => (
                <div key={dayKey} className="avail-day-group">
                  <div className="avail-day-header">
                    <span className="avail-day-title">{dayKey}</span>
                    <span className="avail-day-count">
                      {grouped[dayKey].length} booking{grouped[dayKey].length !== 1 ? "s" : ""}
                    </span>
                  </div>
                  <div className="avail-slots-list">
                    {grouped[dayKey].map((slot, i) => {
                      const sc = getStatusColor(slot.status);
                      const isActive = slot.status !== "CANCELLED" && slot.status !== "COMPLETED";
                      return (
                        <div
                          key={i}
                          className={`avail-slot-row ${isActive ? "avail-slot-busy" : "avail-slot-done"}`}
                          style={{
                            borderLeftColor: sc.border,
                            backgroundColor: sc.bg,
                          }}
                        >
                          <div className="avail-slot-time-block">
                            <span className="avail-slot-time-icon">🕐</span>
                            <div className="avail-slot-times">
                              <span className="avail-slot-from">
                                From: <strong>{toISTTimeOnly(slot.startTime)}</strong>
                              </span>
                              <span className="avail-slot-sep">→</span>
                              <span className="avail-slot-to">
                                To: <strong>{toISTTimeOnly(slot.endTime)}</strong>
                              </span>
                            </div>
                          </div>
                          <span
                            className="avail-slot-status-badge"
                            style={{ color: sc.text, backgroundColor: `${sc.border}55` }}
                          >
                            {sc.label}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer hint */}
        <div className="avail-modal-footer">
          <span className="avail-footer-hint">
            ⏰ All times shown in <strong>Indian Standard Time (IST)</strong>.
            Plan your booking around the busy slots above.
          </span>
        </div>
      </div>
    </div>
  );
}

