import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

function toISTTime(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("en-IN", {
    timeZone: "Asia/Kolkata",
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

function StudentBookingPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();

  const [equipment, setEquipment] = useState(null);
  const [loadingEquipment, setLoadingEquipment] = useState(true);
  const [loadingEquipmentError, setLoadingEquipmentError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [startDate, setStartDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endDate, setEndDate] = useState("");
  const [endTime, setEndTime] = useState("");
  const [purpose, setPurpose] = useState("");
  const [formError, setFormError] = useState(null);

  // Milestone 2 - T4: Recurring Booking state
  const [isRecurring, setIsRecurring] = useState(false);
  const [recurrenceType, setRecurrenceType] = useState("WEEKLY");
  const [occurrences, setOccurrences] = useState(4);
  const [recurringResult, setRecurringResult] = useState(null);

  // Milestone 2 - T4: Slot Recommendation state
  const [recommendations, setRecommendations] = useState([]);
  const [loadingRecs, setLoadingRecs] = useState(false);
  const [showRecs, setShowRecs] = useState(false);

  // Milestone 2 - T4: Waitlist fallback state
  const [showWaitlistOption, setShowWaitlistOption] = useState(false);
  const [joiningWaitlist, setJoiningWaitlist] = useState(false);

  useEffect(() => {
    setLoadingEquipment(true);
    api
      .get(`/api/equipment/${id}`)
      .then((res) => {
        setEquipment(res.data);
        setLoadingEquipmentError(null);
      })
      .catch(() => {
        setLoadingEquipmentError("Could not load equipment details.");
      })
      .finally(() => setLoadingEquipment(false));
  }, [id]);

  function toISOInstant(date, time) {
    if (!date || !time) return null;
    return new Date(`${date}T${time}:00`).toISOString();
  }

  function validate() {
    if (!startDate || !startTime) return "Please select a start date and time.";
    if (!endDate || !endTime) return "Please select an end date and time.";
    const start = new Date(`${startDate}T${startTime}:00`);
    const end = new Date(`${endDate}T${endTime}:00`);
    if (isNaN(start.getTime()) || isNaN(end.getTime()))
      return "Invalid date/time values.";
    if (end <= start) return "End time must be after start time.";
    if (start < new Date()) return "Start time cannot be in the past.";
    const durationHours = (end - start) / (1000 * 60 * 60);
    if (equipment?.maxBookingHours && durationHours > equipment.maxBookingHours)
      return `Booking duration exceeds maximum allowed ${equipment.maxBookingHours} hours.`;
    if (!purpose.trim()) return "Please provide a purpose for the booking.";
    return null;
  }

  async function handleFetchRecommendations() {
    setLoadingRecs(true);
    try {
      const qDate = startDate || new Date().toISOString().split("T")[0];
      const res = await api.get(`/api/equipment/${id}/recommend-slots`, {
        params: {
          date: qDate,
          durationMinutes: 120,
        },
      });
      setRecommendations(res.data?.recommendations || []);
      setShowRecs(true);
      if ((res.data?.recommendations || []).length === 0) {
        toast.info("No free slots remaining on this date. You can join the waitlist or pick another date.");
      }
    } catch {
      toast.error("Could not fetch slot recommendations.");
    } finally {
      setLoadingRecs(false);
    }
  }

  function applyRecommendedSlot(slot) {
    const sDate = new Date(slot.startTime);
    const eDate = new Date(slot.endTime);

    // Format local values in YYYY-MM-DD and HH:MM
    const pad = (n) => String(n).padStart(2, "0");
    const sDateStr = `${sDate.getFullYear()}-${pad(sDate.getMonth() + 1)}-${pad(sDate.getDate())}`;
    const sTimeStr = `${pad(sDate.getHours())}:${pad(sDate.getMinutes())}`;
    const eDateStr = `${eDate.getFullYear()}-${pad(eDate.getMonth() + 1)}-${pad(eDate.getDate())}`;
    const eTimeStr = `${pad(eDate.getHours())}:${pad(eDate.getMinutes())}`;

    setStartDate(sDateStr);
    setStartTime(sTimeStr);
    setEndDate(eDateStr);
    setEndTime(eTimeStr);
    setShowRecs(false);
    setShowWaitlistOption(false);
    setFormError(null);
    toast.success("Applied recommended conflict-free slot!");
  }

  async function handleJoinWaitlist() {
    setJoiningWaitlist(true);
    try {
      const res = await api.post("/api/waitlist", {
        equipmentId: Number(id),
        desiredStartTime: toISOInstant(startDate, startTime),
        desiredEndTime: toISOInstant(endDate, endTime),
        purpose: purpose.trim() || "Waitlist reservation",
      });
      toast.success(
        `Added to waitlist at position #${res.data?.queuePosition || 1}! You'll be automatically promoted if the slot frees up.`
      );
      navigate("/dashboard/student/bookings");
    } catch (err) {
      toast.error(err?.response?.data?.message || "Failed to join waitlist.");
    } finally {
      setJoiningWaitlist(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError(null);
    setShowWaitlistOption(false);
    setRecurringResult(null);

    const err = validate();
    if (err) {
      setFormError(err);
      return;
    }

    setSubmitting(true);
    try {
      if (isRecurring) {
        // Milestone 2 - T4: Recurring Booking Submission
        const res = await api.post("/api/bookings/recurring", {
          equipmentId: Number(id),
          startTime: toISOInstant(startDate, startTime),
          endTime: toISOInstant(endDate, endTime),
          purpose: purpose.trim(),
          recurrenceType,
          occurrences: Number(occurrences),
        });

        const data = res.data;
        setRecurringResult(data);
        if (data.totalConflicted > 0) {
          toast.info(
            `Booked ${data.totalBooked} session(s). ${data.totalConflicted} session(s) had schedule conflicts.`
          );
        } else {
          toast.success(`All ${data.totalBooked} recurring sessions submitted successfully!`);
          navigate("/dashboard/student/bookings");
        }
      } else {
        // Normal One-off Booking
        await api.post("/api/bookings", {
          equipmentId: Number(id),
          startTime: toISOInstant(startDate, startTime),
          endTime: toISOInstant(endDate, endTime),
          purpose: purpose.trim(),
        });
        toast.success("Booking request submitted in Pending Approval status!");
        navigate("/dashboard/student/bookings");
      }
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data ||
        "Failed to submit booking. Please try again.";
      const errorStr = typeof msg === "string" ? msg : "Booking failed.";
      setFormError(errorStr);

      // Offer waitlist if conflict occurred
      if (
        err?.response?.status === 409 ||
        errorStr.toLowerCase().includes("overlap") ||
        errorStr.toLowerCase().includes("conflict")
      ) {
        setShowWaitlistOption(true);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loadingEquipment) {
    return (
      <div className="booking-page-container">
        <div className="booking-loading">
          <div className="booking-spinner" />
          <p>Loading equipment details…</p>
        </div>
      </div>
    );
  }

  if (loadingEquipmentError) {
    return (
      <div className="booking-page-container">
        <div className="booking-error-state">
          <span className="booking-error-icon">⚠️</span>
          <h2>Unable to Load Equipment</h2>
          <p>{loadingEquipmentError}</p>
          <button
            type="button"
            className="booking-back-btn"
            onClick={() => navigate("/dashboard/student/equipments")}
          >
            ← Back to Equipments
          </button>
        </div>
      </div>
    );
  }

  const statusLabel =
    equipment?.status === "AVAILABLE"
      ? "Available"
      : equipment?.status === "IN_USE"
      ? "In Use"
      : equipment?.status === "UNDER_MAINTENANCE"
      ? "Under Maintenance"
      : equipment?.status || "Unknown";

  const statusClass =
    equipment?.status === "AVAILABLE"
      ? "status-available"
      : equipment?.status === "IN_USE"
      ? "status-in-use"
      : "status-maintenance";

  return (
    <div className="booking-page-container">
      {/* Back navigation */}
      <button
        type="button"
        className="booking-back-link"
        onClick={() => navigate("/dashboard/student/equipments")}
      >
        ← Back to Equipments
      </button>

      <div className="booking-title-row">
        <div>
          <h1 className="booking-page-title">Book Equipment</h1>
          <p className="booking-page-subtitle">
            Reserve time on scientific instruments with smart slot recommendations and waitlist protection.
          </p>
        </div>

        <button
          type="button"
          className="booking-recommend-btn"
          onClick={handleFetchRecommendations}
          disabled={loadingRecs}
          title="Analyze lab usage and suggest best conflict-free slots"
        >
          {loadingRecs ? "Analyzing Slots…" : "💡 Suggest Best Slots"}
        </button>
      </div>

      {/* Recommendations Banner / Modal */}
      {showRecs && (
        <div className="booking-recommendations-box">
          <div className="booking-rec-header">
            <div>
              <h3>💡 Conflict-Free Slot Recommendations (IST)</h3>
              <p>Rule-based optimization to minimize lab idle time and peak contention.</p>
            </div>
            <button
              type="button"
              className="booking-rec-close"
              onClick={() => setShowRecs(false)}
            >
              ✕
            </button>
          </div>

          {recommendations.length === 0 ? (
            <p className="booking-rec-empty">No free slots found on this date. Try another date or join the waitlist.</p>
          ) : (
            <div className="booking-rec-grid">
              {recommendations.map((rec, i) => (
                <div key={i} className="booking-rec-card">
                  <div className="booking-rec-time">
                    <span className="booking-rec-badge">Score {rec.score}/100</span>
                    <strong>{toISTTime(rec.startTime)} → {toISTTime(rec.endTime)}</strong>
                  </div>
                  <p className="booking-rec-reason">{rec.reason}</p>
                  <button
                    type="button"
                    className="booking-apply-slot-btn"
                    onClick={() => applyRecommendedSlot(rec)}
                  >
                    Select Slot
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="booking-page-grid">
        {/* Equipment Summary Card */}
        <aside className="booking-equipment-summary">
          <h2 className="booking-summary-heading">Equipment Details</h2>

          <div className="booking-summary-card">
            <div className="booking-summary-header">
              <div className="booking-category-badge">
                {equipment?.category || "General"}
              </div>
              <span className={`booking-status-pill ${statusClass}`}>
                {statusLabel}
              </span>
            </div>

            <h3 className="booking-equipment-name">{equipment?.name}</h3>

            {equipment?.collegeName && (
              <p className="booking-meta-item">
                <span className="booking-meta-label">🏛️ College:</span>
                <span>{equipment.collegeName}</span>
              </p>
            )}
            {equipment?.model && (
              <p className="booking-meta-item">
                <span className="booking-meta-label">🔧 Model:</span>
                <span>{equipment.model}</span>
              </p>
            )}
            {equipment?.manufacturer && (
              <p className="booking-meta-item">
                <span className="booking-meta-label">🏭 Manufacturer:</span>
                <span>{equipment.manufacturer}</span>
              </p>
            )}
            {equipment?.location && (
              <p className="booking-meta-item">
                <span className="booking-meta-label">📍 Location:</span>
                <span>{equipment.location}</span>
              </p>
            )}
            {equipment?.maxBookingHours && (
              <p className="booking-meta-item">
                <span className="booking-meta-label">⏱ Max Hours:</span>
                <span>{equipment.maxBookingHours}h per booking</span>
              </p>
            )}
            <div className="booking-approval-notice">
              🛡️ Student reservations start in Pending Approval status by default.
            </div>
            {equipment?.description && (
              <p className="booking-equipment-desc">{equipment.description}</p>
            )}
          </div>
        </aside>

        {/* Booking Form */}
        <section className="booking-form-section">
          <h2 className="booking-summary-heading">Reservation Details</h2>

          <form className="booking-form" onSubmit={handleSubmit} noValidate>
            <div className="booking-datetime-row">
              <div className="booking-field">
                <label htmlFor="startDate" className="booking-label">
                  Start Date <span className="booking-required">*</span>
                </label>
                <input
                  id="startDate"
                  type="date"
                  className="booking-input"
                  value={startDate}
                  onChange={(e) => {
                    setStartDate(e.target.value);
                    if (!endDate) setEndDate(e.target.value);
                  }}
                  min={new Date().toISOString().split("T")[0]}
                  required
                />
              </div>
              <div className="booking-field">
                <label htmlFor="startTime" className="booking-label">
                  Start Time <span className="booking-required">*</span>
                </label>
                <input
                  id="startTime"
                  type="time"
                  className="booking-input"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="booking-datetime-row">
              <div className="booking-field">
                <label htmlFor="endDate" className="booking-label">
                  End Date <span className="booking-required">*</span>
                </label>
                <input
                  id="endDate"
                  type="date"
                  className="booking-input"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  min={startDate || new Date().toISOString().split("T")[0]}
                  required
                />
              </div>
              <div className="booking-field">
                <label htmlFor="endTime" className="booking-label">
                  End Time <span className="booking-required">*</span>
                </label>
                <input
                  id="endTime"
                  type="time"
                  className="booking-input"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Milestone 2 - T4: Recurring Booking Options */}
            <div className="booking-recurring-box">
              <label className="booking-checkbox-label">
                <input
                  type="checkbox"
                  checked={isRecurring}
                  onChange={(e) => setIsRecurring(e.target.checked)}
                />
                <span>🔁 Repeat this booking (Recurring Series)</span>
              </label>

              {isRecurring && (
                <div className="booking-recurring-fields">
                  <div className="booking-field">
                    <label className="booking-label">Frequency:</label>
                    <select
                      className="booking-select"
                      value={recurrenceType}
                      onChange={(e) => setRecurrenceType(e.target.value)}
                    >
                      <option value="WEEKLY">Weekly (Same day &amp; time every week)</option>
                      <option value="DAILY">Daily (Consecutive days)</option>
                    </select>
                  </div>

                  <div className="booking-field">
                    <label className="booking-label">Number of Sessions:</label>
                    <select
                      className="booking-select"
                      value={occurrences}
                      onChange={(e) => setOccurrences(Number(e.target.value))}
                    >
                      <option value="2">2 sessions</option>
                      <option value="3">3 sessions</option>
                      <option value="4">4 sessions (Recommended)</option>
                      <option value="6">6 sessions</option>
                      <option value="8">8 sessions</option>
                      <option value="12">12 sessions</option>
                    </select>
                  </div>
                </div>
              )}
            </div>

            <div className="booking-field">
              <label htmlFor="purpose" className="booking-label">
                Purpose / Description{" "}
                <span className="booking-required">*</span>
              </label>
              <textarea
                id="purpose"
                className="booking-textarea"
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="Briefly describe your research purpose or experiment details…"
                rows={4}
                maxLength={1000}
                required
              />
              <span className="booking-char-count">
                {purpose.length}/1000 characters
              </span>
            </div>

            {formError && (
              <div className="booking-form-error" role="alert">
                ⚠️ {formError}
              </div>
            )}

            {/* Waitlist Call-to-action when conflict occurs */}
            {showWaitlistOption && (
              <div className="booking-waitlist-callout">
                <div className="booking-waitlist-info">
                  <strong>⏳ High-Demand Time Slot!</strong>
                  <p>
                    This time slot is already reserved by another session. You can join the equipment waitlist to be automatically promoted if the slot is cancelled or freed.
                  </p>
                </div>
                <button
                  type="button"
                  className="booking-join-waitlist-btn"
                  onClick={handleJoinWaitlist}
                  disabled={joiningWaitlist}
                >
                  {joiningWaitlist ? "Joining Waitlist…" : "Join Waitlist for this Slot"}
                </button>
              </div>
            )}

            {/* Recurring partial results */}
            {recurringResult && (
              <div className="booking-recurring-summary">
                <h4>Recurring Booking Summary</h4>
                <p>
                  ✅ <strong>{recurringResult.totalBooked}</strong> session(s) booked.
                  {recurringResult.totalConflicted > 0 && (
                    <span> ⚠️ <strong>{recurringResult.totalConflicted}</strong> session(s) had conflicting reservations.</span>
                  )}
                </p>
                <button
                  type="button"
                  className="bh-browse-btn"
                  onClick={() => navigate("/dashboard/student/bookings")}
                  style={{ marginTop: "10px" }}
                >
                  View My Bookings →
                </button>
              </div>
            )}

            <div className="booking-form-actions">
              <button
                type="button"
                className="booking-cancel-btn"
                onClick={() => navigate("/dashboard/student/equipments")}
                disabled={submitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="booking-submit-btn"
                disabled={submitting || equipment?.status !== "AVAILABLE"}
              >
                {submitting ? (
                  <>
                    <span className="btn-spinner" /> Submitting…
                  </>
                ) : isRecurring ? (
                  `Submit ${occurrences} Recurring Sessions`
                ) : (
                  "Submit Booking Request"
                )}
              </button>
            </div>
            {equipment?.status !== "AVAILABLE" && (
              <p className="booking-unavailable-notice">
                This equipment is currently not available for booking.
              </p>
            )}
          </form>
        </section>
      </div>
    </div>
  );
}

export default StudentBookingPage;
