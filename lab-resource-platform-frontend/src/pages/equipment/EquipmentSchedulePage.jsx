import { useCallback, useEffect, useState } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import {
  FiArrowLeft,
  FiCalendar,
  FiClock,
  FiDollarSign,
  FiChevronLeft,
  FiChevronRight,
  FiCheckCircle,
  FiAlertCircle,
  FiExternalLink,
  FiUser,
  FiCpu,
  FiMapPin,
  FiLayers,
} from "react-icons/fi";

const IST_ZONE = "Asia/Kolkata";

// Format helper
function formatISTTime(dateObj) {
  return dateObj.toLocaleTimeString("en-IN", {
    timeZone: IST_ZONE,
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function formatDateKey(dateObj) {
  // Returns "YYYY-MM-DD" in IST
  return dateObj.toLocaleDateString("en-CA", { timeZone: IST_ZONE });
}

export default function EquipmentSchedulePage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();

  const [equipment, setEquipment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [availabilitySlots, setAvailabilitySlots] = useState([]);
  const [detailedBookings, setDetailedBookings] = useState([]);

  // Selected date state (defaults to today or param)
  const initialDate = searchParams.get("date") ? new Date(searchParams.get("date")) : new Date();
  const [selectedDate, setSelectedDate] = useState(initialDate);

  // Month navigation in mini calendar
  const [calendarMonth, setCalendarMonth] = useState(
    new Date(initialDate.getFullYear(), initialDate.getMonth(), 1)
  );

  // View toggle on timeline: Day View vs Week View
  const [viewMode, setViewMode] = useState("day"); // "day" | "week"

  // Load equipment details
  const loadEquipment = useCallback(async () => {
    try {
      const res = await api.get(`/api/equipment/${id}`);
      setEquipment(res.data);
    } catch (err) {
      console.warn("Could not load equipment details", err);
      // Fallback preview
      setEquipment({
        id,
        name: "Laboratory Instrument",
        category: "Research Facility",
        model: "Standard Core",
        collegeName: "Affiliated University",
        maxBookingHours: 8,
        hourlyCost: 250,
      });
    }
  }, [id]);

  // Load availability slots for current month
  const loadAvailability = useCallback(async () => {
    try {
      // Start of month to end of month
      const startOfMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);
      const endOfMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 0, 23, 59, 59);

      const res = await api.get(`/api/equipment/${id}/availability`, {
        params: {
          startDate: startOfMonth.toISOString(),
          endDate: endOfMonth.toISOString(),
        },
      });
      setAvailabilitySlots(res.data || []);
    } catch (err) {
      console.warn("Could not load availability slots", err);
      setAvailabilitySlots([]);
    }
  }, [id, calendarMonth]);

  // Load detailed bookings to get user name & purpose
  const loadBookings = useCallback(async () => {
    try {
      const res = await api.get("/api/bookings");
      const list = res.data || [];
      const match = list.filter((b) => String(b.equipmentId) === String(id));
      setDetailedBookings(match);
    } catch {
      setDetailedBookings([]);
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    Promise.all([loadEquipment(), loadAvailability(), loadBookings()]).finally(() => {
      setLoading(false);
    });
  }, [loadEquipment, loadAvailability, loadBookings]);

  // Calendar calculations
  const year = calendarMonth.getFullYear();
  const month = calendarMonth.getMonth(); // 0-indexed
  const monthName = calendarMonth.toLocaleString("en-US", { month: "long" });

  const firstDayOfWeek = new Date(year, month, 1).getDay(); // 0=Sun
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  // Map of date string -> count of slots/bookings
  const bookingsByDate = {};
  availabilitySlots.forEach((slot) => {
    const d = new Date(slot.startTime);
    const key = formatDateKey(d);
    if (!bookingsByDate[key]) bookingsByDate[key] = [];
    bookingsByDate[key].push(slot);
  });

  detailedBookings.forEach((b) => {
    if (b.startTime) {
      const d = new Date(b.startTime);
      const key = formatDateKey(d);
      if (!bookingsByDate[key]) bookingsByDate[key] = [];
      // avoid duplicates
      if (!bookingsByDate[key].some((s) => s.id === b.id)) {
        bookingsByDate[key].push(b);
      }
    }
  });

  const selectedDateKey = formatDateKey(selectedDate);
  const dayBookings = (bookingsByDate[selectedDateKey] || [])
    .filter((b) => b.status !== "CANCELLED")
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));

  // Hourly rate & max hours
  const hourlyRate = equipment?.hourlyCost || equipment?.feePerHour || equipment?.hourlyFee || 250;
  const maxHours = equipment?.maxBookingHours || equipment?.maxBookingDurationHours || 8;
  const collegeName = equipment?.collegeName || equipment?.ownerInstitutionName || "Institution Core Lab";

  // Build Day Schedule Timeline from 8:00 AM to 8:00 PM (12 hrs)
  const dayStartHour = 8;
  const dayEndHour = 20;

  // Compute intervals (Booked vs Free)
  const timelineItems = [];
  let currentCursor = new Date(selectedDate);
  currentCursor.setHours(dayStartHour, 0, 0, 0);

  const dayEndBoundary = new Date(selectedDate);
  dayEndBoundary.setHours(dayEndHour, 0, 0, 0);

  let totalBookedMinutes = 0;

  dayBookings.forEach((b) => {
    const bStart = new Date(b.startTime);
    const bEnd = new Date(b.endTime);

    // If slot starts after currentCursor, there is a Free gap
    if (bStart > currentCursor && bStart <= dayEndBoundary) {
      const freeEnd = bStart > dayEndBoundary ? dayEndBoundary : bStart;
      if (freeEnd > currentCursor) {
        timelineItems.push({
          type: "free",
          startTime: new Date(currentCursor),
          endTime: new Date(freeEnd),
        });
      }
    }

    // Add booked slot if inside day window
    if (bEnd > currentCursor && bStart < dayEndBoundary) {
      const visibleStart = bStart < currentCursor ? currentCursor : bStart;
      const visibleEnd = bEnd > dayEndBoundary ? dayEndBoundary : bEnd;
      const durationMins = (bEnd - bStart) / (1000 * 60);
      totalBookedMinutes += Math.max(0, durationMins);

      // Find user details if available
      const detailed = detailedBookings.find((d) => d.id === b.id || (d.startTime === b.startTime && d.endTime === b.endTime));

      timelineItems.push({
        type: "booked",
        id: b.id,
        status: b.status || "CONFIRMED",
        startTime: bStart,
        endTime: bEnd,
        userName: detailed?.userName || b.userName || "Reserved Session",
        userEmail: detailed?.userEmail || b.userEmail || "",
        purpose: detailed?.purpose || b.purpose || "Laboratory experiment & measurement",
      });
    }

    if (bEnd > currentCursor) {
      currentCursor = new Date(bEnd);
    }
  });

  // Remaining free slot till day end
  if (currentCursor < dayEndBoundary) {
    timelineItems.push({
      type: "free",
      startTime: new Date(currentCursor),
      endTime: new Date(dayEndBoundary),
    });
  }

  const bookedHours = Math.round((totalBookedMinutes / 60) * 10) / 10;
  const totalDayHours = dayEndHour - dayStartHour; // 12 hrs
  const freeHours = Math.max(0, Math.round((totalDayHours - bookedHours) * 10) / 10);
  const revenue = Math.round(bookedHours * hourlyRate);

  // Month navigation handlers
  function prevMonth() {
    setCalendarMonth(new Date(year, month - 1, 1));
  }
  function nextMonth() {
    setCalendarMonth(new Date(year, month + 1, 1));
  }

  function handleSelectDay(dayNum) {
    const newDate = new Date(year, month, dayNum);
    setSelectedDate(newDate);
  }

  function handleBookSlot(slotStartTime) {
    const dateStr = formatDateKey(slotStartTime || selectedDate);
    const timeStr = slotStartTime ? `${String(slotStartTime.getHours()).padStart(2, "0")}:${String(slotStartTime.getMinutes()).padStart(2, "0")}` : "09:00";
    navigate(`/dashboard/student/book/${id}?date=${dateStr}&time=${timeStr}`);
  }

  return (
    <div className="admin-page-container eq-sched-page">
      {/* Top Header & Breadcrumb */}
      <div className="eq-sched-top-nav">
        <button
          type="button"
          className="booking-back-link"
          onClick={() => navigate(-1)}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FiArrowLeft size={16} /> Back to Equipment Catalog
        </button>
      </div>

      <div className="admin-header-row" style={{ marginBottom: "20px" }}>
        <div>
          <p className="admin-eyebrow">EQUIPMENT AVAILABILITY &amp; SCHEDULE</p>
          <h1 className="admin-title">{equipment?.name || "Equipment Schedule"}</h1>
          <p className="admin-subtitle">
            Explore live reservation slots, detect peak utilization, and schedule conflict-free research sessions.
          </p>
        </div>
      </div>

      {/* Main Two-Column Calendar Grid */}
      <div className="eq-sched-layout-grid">
        {/* ── Left Column: Mini Month Calendar Card ── */}
        <aside className="eq-sched-cal-sidebar">
          <div className="eq-sched-cal-box">
            {/* Month & Year Navigation Header */}
            <div className="eq-sched-cal-nav">
              <span className="eq-sched-cal-month-title">
                {monthName} {year}
              </span>
              <div className="eq-sched-cal-nav-buttons">
                <button
                  type="button"
                  className="eq-sched-nav-btn"
                  onClick={prevMonth}
                  title="Previous Month"
                >
                  <FiChevronLeft size={16} />
                </button>
                <button
                  type="button"
                  className="eq-sched-nav-btn"
                  onClick={nextMonth}
                  title="Next Month"
                >
                  <FiChevronRight size={16} />
                </button>
              </div>
            </div>

            {/* Days of Week Row */}
            <div className="eq-sched-weekdays-row">
              {["S", "M", "T", "W", "T", "F", "S"].map((d, i) => (
                <div key={i} className="eq-sched-weekday-label">
                  {d}
                </div>
              ))}
            </div>

            {/* Day Cells Grid */}
            <div className="eq-sched-days-grid">
              {/* Empty leading offset days */}
              {Array.from({ length: firstDayOfWeek }).map((_, i) => (
                <div key={`empty-${i}`} className="eq-sched-day-cell empty" />
              ))}

              {/* Month days */}
              {Array.from({ length: daysInMonth }).map((_, i) => {
                const dayNum = i + 1;
                const cellDate = new Date(year, month, dayNum);
                const cellKey = formatDateKey(cellDate);
                const isSelected = selectedDateKey === cellKey;
                const daySlots = bookingsByDate[cellKey] || [];
                const hasConfirmed = daySlots.some((s) => s.status === "CONFIRMED");
                const hasPending = daySlots.some((s) => s.status === "PENDING_APPROVAL");

                return (
                  <button
                    key={dayNum}
                    type="button"
                    className={`eq-sched-day-cell ${isSelected ? "selected" : ""}`}
                    onClick={() => handleSelectDay(dayNum)}
                  >
                    <span className="eq-sched-day-number">{dayNum}</span>
                    <div className="eq-sched-dots-row">
                      {hasConfirmed && <span className="eq-sched-dot confirmed" />}
                      {hasPending && <span className="eq-sched-dot pending" />}
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Calendar Legend */}
            <div className="eq-sched-legend-box">
              <div className="eq-sched-legend-item">
                <span className="eq-sched-legend-icon confirmed" />
                <span>Confirmed</span>
              </div>
              <div className="eq-sched-legend-item">
                <span className="eq-sched-legend-icon pending" />
                <span>Pending approval</span>
              </div>
              <div className="eq-sched-legend-item">
                <span className="eq-sched-legend-icon free" />
                <span>Free to book</span>
              </div>
            </div>
          </div>
        </aside>

        {/* ── Right Column: Day Schedule Timeline & Metrics ── */}
        <section className="eq-sched-timeline-panel">
          {/* Timeline Header */}
          <div className="eq-sched-timeline-header">
            <div className="eq-sched-timeline-title-block">
              <h2 className="eq-sched-inst-name">{equipment?.name}</h2>
              <p className="eq-sched-inst-meta">
                {collegeName} · {equipment?.model || equipment?.category || "Core Lab"} · ₹{hourlyRate}/hr
              </p>
            </div>

            <div className="eq-sched-timeline-date-block">
              <span className="eq-sched-selected-date-str">
                {selectedDate.toLocaleDateString("en-US", {
                  weekday: "long",
                  day: "numeric",
                  month: "short",
                  timeZone: IST_ZONE,
                })}
              </span>
              <span className="eq-sched-max-hrs">Max {maxHours} hrs per session</span>
            </div>
          </div>

          {/* Metric KPI Chips Row */}
          <div className="eq-sched-metrics-row">
            <div className="eq-sched-metric-chip booked">
              <span className="eq-sched-metric-label">Booked</span>
              <span className="eq-sched-metric-val">{bookedHours} hrs</span>
            </div>

            <div className="eq-sched-metric-chip free">
              <span className="eq-sched-metric-label">Free</span>
              <span className="eq-sched-metric-val">{freeHours} hrs</span>
            </div>

            <div className="eq-sched-metric-chip revenue">
              <span className="eq-sched-metric-label">Revenue</span>
              <span className="eq-sched-metric-val">₹{revenue.toLocaleString()}</span>
            </div>
          </div>

          {/* Timeline Grid (8 AM to 8 PM) */}
          <div className="eq-sched-timeline-body">
            {timelineItems.length === 0 ? (
              <div className="eq-sched-empty-timeline">
                <FiCheckCircle size={32} color="#16a34a" style={{ marginBottom: 8 }} />
                <h3>No Bookings Scheduled</h3>
                <p>This equipment is completely free for reservations on this date.</p>
              </div>
            ) : (
              <div className="eq-sched-slot-list">
                {timelineItems.map((item, idx) => {
                  if (item.type === "free") {
                    const startStr = formatISTTime(item.startTime);
                    const endStr = formatISTTime(item.endTime);

                    return (
                      <div key={idx} className="eq-sched-slot-card free-slot">
                        <div className="eq-sched-time-indicator">
                          <span className="eq-sched-hour-tag">{startStr}</span>
                        </div>
                        <div className="eq-sched-free-content">
                          <span className="eq-sched-free-text">
                            Free · {startStr} to {endStr}
                          </span>
                          <button
                            type="button"
                            className="eq-sched-quick-book-btn"
                            onClick={() => handleBookSlot(item.startTime)}
                          >
                            Reserve Slot
                          </button>
                        </div>
                      </div>
                    );
                  }

                  // Booked Slot Card
                  const startStr = formatISTTime(item.startTime);
                  const endStr = formatISTTime(item.endTime);
                  const isConfirmed = item.status === "CONFIRMED";

                  return (
                    <div
                      key={idx}
                      className={`eq-sched-slot-card booked-slot ${isConfirmed ? "confirmed" : "pending"}`}
                    >
                      <div className="eq-sched-time-indicator">
                        <span className="eq-sched-hour-tag">{startStr}</span>
                      </div>
                      <div className="eq-sched-booked-content">
                        <div className="eq-sched-booked-header">
                          <div className="eq-sched-user-name">
                            <FiUser size={14} style={{ marginRight: 5, verticalAlign: "middle" }} />
                            {item.userName}
                          </div>
                          <span className={`eq-sched-status-badge ${isConfirmed ? "confirmed" : "pending"}`}>
                            {isConfirmed ? "Confirmed" : "Pending approval"}
                          </span>
                        </div>
                        <div className="eq-sched-booked-sub">
                          <strong>{startStr} to {endStr}</strong>
                          {item.purpose && <span> · {item.purpose}</span>}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Bottom Actions Bar */}
          <div className="eq-sched-timeline-footer">
            <button
              type="button"
              className="eq-btn-primary"
              onClick={() => handleBookSlot()}
              style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
            >
              Request a slot <FiExternalLink size={14} />
            </button>

            <button
              type="button"
              className="eq-btn-secondary"
              onClick={() => {
                const nextWeekDate = new Date(selectedDate);
                nextWeekDate.setDate(nextWeekDate.getDate() + 7);
                setSelectedDate(nextWeekDate);
                toast.info(`Viewing week of ${nextWeekDate.toLocaleDateString()}`);
              }}
              style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
            >
              Next week <FiChevronRight size={14} />
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
