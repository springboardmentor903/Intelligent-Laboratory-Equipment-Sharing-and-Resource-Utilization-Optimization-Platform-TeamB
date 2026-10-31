import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

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

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError(null);
    const err = validate();
    if (err) {
      setFormError(err);
      return;
    }

    setSubmitting(true);
    try {
      await api.post("/api/bookings", {
        equipmentId: Number(id),
        startTime: toISOInstant(startDate, startTime),
        endTime: toISOInstant(endDate, endTime),
        purpose: purpose.trim(),
      });
      toast.success("Booking request submitted successfully!");
      navigate("/dashboard/student/bookings");
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data ||
        "Failed to submit booking. Please try again.";
      setFormError(typeof msg === "string" ? msg : "Booking failed.");
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

      <h1 className="booking-page-title">Book Equipment</h1>

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
            {equipment?.requiresApproval && (
              <div className="booking-approval-notice">
                ⚠️ This equipment requires admin approval before confirmation.
              </div>
            )}
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
                  onChange={(e) => setStartDate(e.target.value)}
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

