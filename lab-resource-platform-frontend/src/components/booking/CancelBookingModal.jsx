import { useState } from "react";

export default function CancelBookingModal({
  isOpen,
  booking,
  onClose,
  onConfirm,
  isSubmitting = false,
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");

  if (!isOpen || !booking) return null;

  function handleSubmit(e) {
    e.preventDefault();
    if (!reason.trim()) {
      setError("Please provide a reason for the cancellation.");
      return;
    }
    setError("");
    onConfirm(booking.id, reason.trim());
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
      <div className="modal-content">
        <div className="modal-header">
          <h3 id="cancel-modal-title">Cancel Reservation</h3>
          <p>
            Are you sure you want to cancel your reservation for{" "}
            <strong>{booking.equipmentName}</strong>?
          </p>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          {error && (
            <div className="alert-error" style={{ marginBottom: "12px", padding: "8px 12px" }}>
              <span>{error}</span>
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="cancel-reason">
              Reason for Cancellation <span className="required-star">*</span>
            </label>
            <textarea
              id="cancel-reason"
              className="form-textarea"
              rows={3}
              placeholder="e.g. Experiment postponed due to sample preparation delay..."
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                if (error) setError("");
              }}
              disabled={isSubmitting}
            />
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-secondary"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Keep Reservation
            </button>
            <button
              type="submit"
              className="btn-primary"
              style={{ background: "var(--color-error, #DC2626)", borderColor: "#DC2626" }}
              disabled={isSubmitting}
            >
              {isSubmitting ? "Cancelling..." : "Confirm Cancellation"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
