import { useState } from "react";
import api from "../../../services/api";
import { useToast } from "../../../context/ToastContext";

/**
 * AccessRequestModal (T11 - Resource Sharing Discovery & Access Request UI)
 * 
 * Allows researchers to submit an inter-institution access request for equipment
 * that requires bilateral agreement authorization.
 * 
 * Submits to: `POST /api/sharing/access-requests`
 * Owned by Dhyanalakshmi under Milestone 2 Task T11.
 */
export default function AccessRequestModal({ listing, onClose, onSuccess }) {
  const toast = useToast();

  const [reqStartDate, setReqStartDate] = useState("");
  const [reqEndDate, setReqEndDate] = useState("");
  const [reqPurpose, setReqPurpose] = useState("");
  const [submittingRequest, setSubmittingRequest] = useState(false);
  const [error, setError] = useState(null);

  if (!listing) return null;

  const handleRequestSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!reqStartDate || !reqEndDate || !reqPurpose.trim()) {
      setError("Please specify the intended start date, end date, and research project purpose.");
      return;
    }

    if (new Date(reqEndDate) < new Date(reqStartDate)) {
      setError("The agreement end date cannot precede the start date.");
      return;
    }

    setSubmittingRequest(true);
    try {
      await api.post("/api/sharing/access-requests", {
        equipmentId: listing.equipmentId,
        startDate: reqStartDate,
        endDate: reqEndDate,
        purpose: reqPurpose.trim(),
      });

      toast.success(
        `Access request submitted for ${listing.equipmentName}! It has been queued for institutional review.`
      );
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      console.error("Failed to submit access request:", err);
      const msg = err.response?.data?.message || "Failed to submit inter-institution access request.";
      setError(msg);
      toast.error(msg);
    } finally {
      setSubmittingRequest(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: "560px", width: "95%" }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "16px" }}>
          <div>
            <span className="admin-eyebrow" style={{ color: "#166534" }}>T11 • RESEARCH ACCESS REQUEST</span>
            <h2 style={{ margin: "4px 0 0 0", fontSize: "18px", color: "#0f172a" }}>
              Request Access to {listing.equipmentName}
            </h2>
            <p style={{ margin: "2px 0 0 0", fontSize: "12px", color: "#64748b" }}>
              Owner Institution: <strong>{listing.ownerInstitutionName}</strong>
            </p>
          </div>
          <button
            type="button"
            style={{ background: "none", border: "none", fontSize: "20px", cursor: "pointer", color: "#64748b" }}
            onClick={onClose}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleRequestSubmit}>
          {error && (
            <div className="analytics-error-box" style={{ marginBottom: "14px", padding: "10px 12px" }}>
              <span>⚠️ {error}</span>
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "14px" }}>
            <div>
              <label className="booking-form-label" htmlFor="req-start">Agreement Start</label>
              <input
                id="req-start"
                type="date"
                className="booking-form-input"
                value={reqStartDate}
                onChange={(e) => setReqStartDate(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="booking-form-label" htmlFor="req-end">Agreement End</label>
              <input
                id="req-end"
                type="date"
                className="booking-form-input"
                value={reqEndDate}
                onChange={(e) => setReqEndDate(e.target.value)}
                required
              />
            </div>
          </div>

          <div style={{ marginBottom: "16px" }}>
            <label className="booking-form-label" htmlFor="req-purpose">Project Justification &amp; Research Scope</label>
            <textarea
              id="req-purpose"
              className="booking-form-textarea"
              rows={4}
              placeholder="Explain why your lab requires this instrument, expected usage volume, and affiliated department..."
              value={reqPurpose}
              onChange={(e) => setReqPurpose(e.target.value)}
              required
            />
          </div>

          <div
            style={{
              background: "#f0fdf4",
              borderRadius: "6px",
              padding: "10px 12px",
              fontSize: "12px",
              color: "#166534",
              border: "1px solid #bbf7d0",
              marginBottom: "16px",
            }}
          >
            ℹ️ Once submitted, your Department Head/Institution Admin reviews the request, followed by final approval from the equipment owner. An official sharing agreement will be established upon approval.
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
            <button type="button" className="secondary-button" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="primary-button" disabled={submittingRequest}>
              {submittingRequest ? "Submitting Request…" : "Send Access Request"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

