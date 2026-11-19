import { useState, useEffect } from "react";
import api from "../../../services/api";

const IST_ZONE = "Asia/Kolkata";

function fmtCurrency(val) {
  if (val === null || val === undefined || isNaN(val)) return "₹0.00";
  const num = typeof val === "number" ? val : parseFloat(val);
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(num);
}

function fmtDate(isoStr) {
  if (!isoStr) return "—";
  try {
    return new Date(isoStr).toLocaleDateString("en-IN", {
      timeZone: IST_ZONE,
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return isoStr;
  }
}

/**
 * EquipmentDetailsModal (T11/T12)
 *
 * Displays full equipment specifications, operating parameters, sharing terms,
 * and current user agreement eligibility.
 */
export default function EquipmentDetailsModal({ listing, onClose, onBook, onRequestAccess }) {
  const [details, setDetails] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!listing?.equipmentId) return;
    setLoading(true);
    api
      .get(`/api/equipment/${listing.equipmentId}`)
      .then((res) => setDetails(res.data))
      .catch((err) => {
        console.error("Failed to load deep equipment details:", err);
      })
      .finally(() => setLoading(false));
  }, [listing?.equipmentId]);

  if (!listing) return null;

  const userStatus = listing.userStatus;
  const hasAgreement = Boolean(userStatus?.hasActiveAgreement);
  const isPending =
    userStatus?.latestRequestStatus === "PENDING" || userStatus?.latestRequestStatus === "FORWARDED";

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(15, 23, 42, 0.65)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "16px",
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: "#fff",
          borderRadius: "16px",
          maxWidth: "720px",
          width: "100%",
          maxHeight: "90vh",
          overflowY: "auto",
          boxShadow: "0 25px 50px -12px rgba(0,0,0,0.25)",
          border: "1px solid #e2e8f0",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: "20px 24px",
            borderBottom: "1px solid #e2e8f0",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            background: "#f8fafc",
          }}
        >
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
              <span
                style={{
                  fontSize: "11px",
                  fontWeight: 700,
                  padding: "3px 8px",
                  borderRadius: "12px",
                  background: "#eff6ff",
                  color: "#1d4ed8",
                  textTransform: "uppercase",
                }}
              >
                {listing.categoryName || "Equipment"}
              </span>
              <span style={{ fontSize: "12px", color: "#64748b", fontFamily: "monospace" }}>
                Asset #{listing.assetTag || listing.equipmentId}
              </span>
            </div>
            <h2 style={{ margin: "4px 0 2px 0", fontSize: "20px", fontWeight: 700, color: "#0f172a" }}>
              {listing.equipmentName}
            </h2>
            <div style={{ fontSize: "13px", color: "#475569" }}>
              Owner Institution: <strong>{listing.ownerInstitutionName}</strong>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              fontSize: "22px",
              color: "#64748b",
              cursor: "pointer",
              padding: "4px 8px",
              borderRadius: "6px",
            }}
          >
            ✕
          </button>
        </div>

        {/* Body Content */}
        <div style={{ padding: "24px" }}>
          {/* Key Metric Highlights Grid */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))",
              gap: "12px",
              marginBottom: "20px",
            }}
          >
            <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: "11px", color: "#64748b", fontWeight: 600 }}>HOURLY TARIFF</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                {fmtCurrency(userStatus?.agreementFeePerHour || listing.feePerHour)}
                <span style={{ fontSize: "11px", color: "#64748b", fontWeight: 400 }}> / hr</span>
              </div>
            </div>

            <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: "11px", color: "#64748b", fontWeight: 600 }}>EXTERNAL SHARE</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#2563eb" }}>
                {listing.externalSharePercentage}%
              </div>
            </div>

            <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: "11px", color: "#64748b", fontWeight: 600 }}>MIN NOTICE</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                {listing.minNoticeHours} hrs
              </div>
            </div>

            <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
              <div style={{ fontSize: "11px", color: "#64748b", fontWeight: 600 }}>MAX DURATION</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                {listing.maxBookingDurationHours} hrs
              </div>
            </div>
          </div>

          {/* Description */}
          {listing.description && (
            <div style={{ marginBottom: "18px" }}>
              <h4 style={{ margin: "0 0 6px 0", fontSize: "13px", fontWeight: 700, color: "#334155" }}>
                DESCRIPTION
              </h4>
              <p style={{ margin: 0, fontSize: "14px", color: "#475569", lineHeight: 1.6 }}>
                {listing.description}
              </p>
            </div>
          )}

          {/* Detailed Specs from backend if available */}
          {loading ? (
            <div style={{ padding: "16px", textAlign: "center", color: "#64748b", fontSize: "13px" }}>
              Loading equipment specifications...
            </div>
          ) : details ? (
            <div
              style={{
                background: "#f8fafc",
                borderRadius: "8px",
                padding: "16px",
                border: "1px solid #e2e8f0",
                marginBottom: "18px",
              }}
            >
              <h4 style={{ margin: "0 0 10px 0", fontSize: "13px", fontWeight: 700, color: "#334155" }}>
                TECHNICAL SPECIFICATIONS &amp; LOCATION
              </h4>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", fontSize: "13px" }}>
                {details.manufacturer && (
                  <div>
                    <span style={{ color: "#64748b" }}>Manufacturer:</span>{" "}
                    <strong>{details.manufacturer}</strong>
                  </div>
                )}
                {details.model && (
                  <div>
                    <span style={{ color: "#64748b" }}>Model:</span> <strong>{details.model}</strong>
                  </div>
                )}
                {details.serialNumber && (
                  <div>
                    <span style={{ color: "#64748b" }}>Serial:</span>{" "}
                    <span style={{ fontFamily: "monospace" }}>{details.serialNumber}</span>
                  </div>
                )}
                {details.location && (
                  <div>
                    <span style={{ color: "#64748b" }}>Lab Location:</span>{" "}
                    <strong>{details.location}</strong>
                  </div>
                )}
                {details.operatingStartTime && details.operatingEndTime && (
                  <div>
                    <span style={{ color: "#64748b" }}>Operating Hours:</span>{" "}
                    <strong>{details.operatingStartTime} – {details.operatingEndTime}</strong>
                  </div>
                )}
                {details.documentationUrl && (
                  <div>
                    <span style={{ color: "#64748b" }}>Manual / Docs:</span>{" "}
                    <a
                      href={details.documentationUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{ color: "#2563eb", textDecoration: "underline" }}
                    >
                      View Documentation ↗
                    </a>
                  </div>
                )}
              </div>

              {details.specifications && (
                <div style={{ marginTop: "12px", borderTop: "1px solid #e2e8f0", paddingTop: "10px" }}>
                  <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>Specifications:</div>
                  <div style={{ fontSize: "13px", color: "#1e293b", whiteSpace: "pre-wrap" }}>
                    {details.specifications}
                  </div>
                </div>
              )}
            </div>
          ) : null}

          {/* Sharing Terms */}
          {listing.sharingTerms && (
            <div style={{ marginBottom: "18px" }}>
              <h4 style={{ margin: "0 0 6px 0", fontSize: "13px", fontWeight: 700, color: "#334155" }}>
                TERMS &amp; ACCESS POLICIES
              </h4>
              <div
                style={{
                  background: "#fffbeb",
                  border: "1px solid #fde68a",
                  borderRadius: "8px",
                  padding: "12px 14px",
                  fontSize: "13px",
                  color: "#92400e",
                  whiteSpace: "pre-line",
                  lineHeight: 1.5,
                }}
              >
                {listing.sharingTerms}
              </div>
            </div>
          )}

          {/* Agreement / Access Status Banner */}
          <div
            style={{
              padding: "14px 16px",
              borderRadius: "8px",
              border: hasAgreement
                ? "1px solid #bbf7d0"
                : isPending
                ? "1px solid #fde68a"
                : "1px solid #e2e8f0",
              background: hasAgreement ? "#f0fdf4" : isPending ? "#fffbeb" : "#f8fafc",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "12px",
              marginBottom: "20px",
            }}
          >
            <div>
              <div
                style={{
                  fontSize: "12px",
                  fontWeight: 700,
                  color: hasAgreement ? "#166534" : isPending ? "#92400e" : "#475569",
                }}
              >
                {hasAgreement
                  ? "✓ ACTIVE SHARING AGREEMENT ESTABLISHED"
                  : isPending
                  ? "⏳ ACCESS REQUEST IN REVIEW"
                  : "ℹ️ INTER-INSTITUTION ACCESS GRANT REQUIRED"}
              </div>
              <div style={{ fontSize: "13px", color: "#334155", marginTop: "2px" }}>
                {hasAgreement
                  ? `Valid from ${fmtDate(userStatus?.agreementStartDate)} to ${fmtDate(userStatus?.agreementEndDate)}. You are authorized to book this instrument directly.`
                  : isPending
                  ? `Request #${userStatus?.latestRequestId} is currently ${userStatus?.latestRequestStatus}. You will be able to book once the owner approves.`
                  : "An inter-institution access request must be endorsed by your home institution and approved by the owner institution before booking."}
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: "10px 18px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                background: "#fff",
                color: "#475569",
                fontWeight: 600,
                cursor: "pointer",
                fontSize: "14px",
              }}
            >
              Close
            </button>

            {hasAgreement ? (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  if (onBook) onBook(listing);
                }}
                style={{
                  padding: "10px 20px",
                  borderRadius: "8px",
                  border: "none",
                  background: "#16a34a",
                  color: "#fff",
                  fontWeight: 600,
                  fontSize: "14px",
                  cursor: "pointer",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  boxShadow: "0 2px 4px rgba(22,163,74,0.25)",
                }}
              >
                <span>📅</span>
                <span>Proceed to External Booking</span>
              </button>
            ) : isPending ? (
              <button
                type="button"
                disabled
                style={{
                  padding: "10px 20px",
                  borderRadius: "8px",
                  border: "1px solid #cbd5e1",
                  background: "#f1f5f9",
                  color: "#64748b",
                  fontWeight: 600,
                  fontSize: "14px",
                  cursor: "not-allowed",
                }}
              >
                Request In Review ({userStatus?.latestRequestStatus})
              </button>
            ) : (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  if (onRequestAccess) onRequestAccess(listing);
                }}
                style={{
                  padding: "10px 20px",
                  borderRadius: "8px",
                  border: "none",
                  background: "#2563eb",
                  color: "#fff",
                  fontWeight: 600,
                  fontSize: "14px",
                  cursor: "pointer",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  boxShadow: "0 2px 4px rgba(37,99,235,0.25)",
                }}
              >
                <span>📝</span>
                <span>Submit Access Request</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

