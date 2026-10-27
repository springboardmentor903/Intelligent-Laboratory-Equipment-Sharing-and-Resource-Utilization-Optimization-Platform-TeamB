import { useState, useMemo } from "react";
import { Link } from "react-router-dom";

export default function BookingForm({
  equipmentList = [],
  selectedEquipment = null,
  initialStart = "",
  initialEnd = "",
  onSubmit,
  isSubmitting = false,
  serverError = "",
  onEquipmentChange,
}) {
  const [equipmentId, setEquipmentId] = useState(
    selectedEquipment?.id ? String(selectedEquipment.id) : ""
  );
  const [startTime, setStartTime] = useState(initialStart || "");
  const [endTime, setEndTime] = useState(initialEnd || "");
  const [purpose, setPurpose] = useState("");
  const [errors, setErrors] = useState({});

  const currentEquipment = useMemo(() => {
    if (selectedEquipment && String(selectedEquipment.id) === String(equipmentId)) {
      return selectedEquipment;
    }
    return equipmentList.find((e) => String(e.id) === String(equipmentId)) || null;
  }, [selectedEquipment, equipmentList, equipmentId]);

  // Duration in hours and minutes
  const durationInfo = useMemo(() => {
    if (!startTime || !endTime) return null;
    const start = new Date(startTime).getTime();
    const end = new Date(endTime).getTime();
    if (isNaN(start) || isNaN(end) || end <= start) return null;

    const diffMinutes = Math.round((end - start) / (1000 * 60));
    const hours = Math.floor(diffMinutes / 60);
    const minutes = diffMinutes % 60;
    const totalHours = diffMinutes / 60;

    const maxAllowed = currentEquipment?.maxBookingHours || 8;
    const isExceeded = totalHours > maxAllowed;

    return {
      hours,
      minutes,
      totalHours,
      maxAllowed,
      isExceeded,
      displayText: `${hours}h ${minutes > 0 ? `${minutes}m` : ""}`.trim(),
    };
  }, [startTime, endTime, currentEquipment]);

  function handleEquipmentSelect(e) {
    const id = e.target.value;
    setEquipmentId(id);
    if (onEquipmentChange) {
      const match = equipmentList.find((item) => String(item.id) === id);
      onEquipmentChange(match || null);
    }
    if (errors.equipmentId) {
      setErrors((prev) => ({ ...prev, equipmentId: null }));
    }
  }

  function validate() {
    const newErrors = {};

    if (!equipmentId) {
      newErrors.equipmentId = "Please select an equipment item.";
    }

    if (!startTime) {
      newErrors.startTime = "Start date and time is required.";
    } else {
      const startMs = new Date(startTime).getTime();
      if (isNaN(startMs)) {
        newErrors.startTime = "Invalid start time.";
      } else if (startMs < Date.now() - 60000) {
        newErrors.startTime = "Booking start time must be in the future.";
      }
    }

    if (!endTime) {
      newErrors.endTime = "End date and time is required.";
    } else if (startTime) {
      const startMs = new Date(startTime).getTime();
      const endMs = new Date(endTime).getTime();
      if (isNaN(endMs)) {
        newErrors.endTime = "Invalid end time.";
      } else if (endMs <= startMs) {
        newErrors.endTime = "End time must be after start time.";
      } else if (durationInfo?.isExceeded) {
        newErrors.endTime = `Booking duration exceeds maximum allowed limit of ${durationInfo.maxAllowed} hours.`;
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!validate()) return;

    const payload = {
      equipmentId: Number(equipmentId),
      startTime: new Date(startTime).toISOString(),
      endTime: new Date(endTime).toISOString(),
      purpose: purpose.trim() || undefined,
    };

    await onSubmit(payload);
  }

  return (
    <form className="booking-form-card" onSubmit={handleSubmit} noValidate>
      {serverError && (
        <div className="alert-error" role="alert" style={{ marginBottom: "20px" }}>
          <span aria-hidden="true">⚠️</span>
          <span>{serverError}</span>
        </div>
      )}

      {currentEquipment && (
        <div
          style={{
            padding: "14px 18px",
            background: "var(--bg-subtle)",
            border: "1px solid var(--border-color)",
            borderRadius: "var(--radius-md)",
            marginBottom: "22px",
            display: "flex",
            flexDirection: "column",
            gap: "8px",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <strong style={{ fontSize: "16px", color: "var(--text-main)" }}>
              {currentEquipment.name}
            </strong>
            <span className="summary-pill">
              ⏱ Max {currentEquipment.maxBookingHours || 8}h limit
            </span>
          </div>

          <p style={{ margin: 0, fontSize: "13px", color: "var(--text-secondary)" }}>
            {[currentEquipment.category, currentEquipment.model].filter(Boolean).join(" • ")}
          </p>

          {currentEquipment.requiresApproval ? (
            <div
              style={{
                fontSize: "12px",
                color: "#B45309",
                background: "#FFFBEB",
                padding: "6px 10px",
                borderRadius: "4px",
                border: "1px solid #FCD34D",
                marginTop: "4px",
              }}
            >
              🛡 <strong>Requires Manager Approval:</strong> This reservation will be placed in{" "}
              <em>Pending Approval</em> status and requires lab manager confirmation before use.
            </div>
          ) : (
            <div
              style={{
                fontSize: "12px",
                color: "#047857",
                background: "#ECFDF5",
                padding: "6px 10px",
                borderRadius: "4px",
                border: "1px solid #A7F3D0",
                marginTop: "4px",
              }}
            >
              ⚡ <strong>Instant Confirmation:</strong> This equipment supports direct reservation confirmation.
            </div>
          )}
        </div>
      )}

      <div className="booking-form-grid">
        {/* Equipment Selector (if multiple equipment available) */}
        {!selectedEquipment && (
          <div className="form-group span-2">
            <label className="form-label" htmlFor="booking-equipment">
              Select Equipment <span className="required-star">*</span>
            </label>
            <select
              id="booking-equipment"
              className={`form-input ${errors.equipmentId ? "input-error" : ""}`}
              value={equipmentId}
              onChange={handleEquipmentSelect}
              disabled={isSubmitting}
            >
              <option value="">-- Choose laboratory equipment --</option>
              {equipmentList.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} ({item.category || "General"} • Max {item.maxBookingHours || 8}h)
                </option>
              ))}
            </select>
            {errors.equipmentId && <span className="form-error">{errors.equipmentId}</span>}
          </div>
        )}

        {/* Start Time */}
        <div className="form-group">
          <label className="form-label" htmlFor="booking-start">
            Start Date & Time <span className="required-star">*</span>
          </label>
          <input
            id="booking-start"
            type="datetime-local"
            className={`form-input ${errors.startTime ? "input-error" : ""}`}
            value={startTime}
            onChange={(e) => {
              setStartTime(e.target.value);
              if (errors.startTime) setErrors((prev) => ({ ...prev, startTime: null }));
            }}
            disabled={isSubmitting}
          />
          {errors.startTime && <span className="form-error">{errors.startTime}</span>}
        </div>

        {/* End Time */}
        <div className="form-group">
          <label className="form-label" htmlFor="booking-end">
            End Date & Time <span className="required-star">*</span>
          </label>
          <input
            id="booking-end"
            type="datetime-local"
            className={`form-input ${errors.endTime ? "input-error" : ""}`}
            value={endTime}
            onChange={(e) => {
              setEndTime(e.target.value);
              if (errors.endTime) setErrors((prev) => ({ ...prev, endTime: null }));
            }}
            disabled={isSubmitting}
          />
          {errors.endTime && <span className="form-error">{errors.endTime}</span>}
        </div>

        {/* Duration Meter */}
        {durationInfo && (
          <div className="form-group span-2">
            <div className="booking-duration-meter">
              <span>Selected Duration:</span>
              <span className={`duration-meter-val ${durationInfo.isExceeded ? "exceeded" : ""}`}>
                {durationInfo.displayText} / Max {durationInfo.maxAllowed}h allowed
              </span>
            </div>
          </div>
        )}

        {/* Purpose / Reason */}
        <div className="form-group span-2">
          <label className="form-label" htmlFor="booking-purpose">
            Reservation Purpose / Experiment Notes
          </label>
          <textarea
            id="booking-purpose"
            className="form-textarea"
            rows={3}
            placeholder="Describe your research experiment or intended usage (optional)..."
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
            disabled={isSubmitting}
          />
        </div>
      </div>

      <div className="form-actions">
        <Link to="/dashboard/bookings/calendar" className="btn-secondary">
          View Availability Calendar
        </Link>
        <button
          type="submit"
          className="btn-primary"
          disabled={isSubmitting || durationInfo?.isExceeded}
        >
          {isSubmitting ? "Creating Reservation..." : "Confirm & Reserve Slot"}
        </button>
      </div>
    </form>
  );
}
