import { useState, useEffect, useCallback } from "react";
import api from "../../../services/api";
import { useToast } from "../../../context/ToastContext";

const IST_ZONE = "Asia/Kolkata";

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

function fmtDateTime(isoStr) {
  if (!isoStr) return "—";
  try {
    return new Date(isoStr).toLocaleString("en-IN", {
      timeZone: IST_ZONE,
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoStr;
  }
}

/**
 * AccessRequestsReviewSection (T11 - Resource Sharing Discovery & Access Request UI)
 * 
 * Separated component for multi-tier access request review:
 * - Requesting Institution Admin: Endorse & Forward (`POST /{id}/forward`) or Reject (`POST /{id}/reject-requesting`)
 * - Owner Institution Admin: Final Approval (`POST /{id}/approve`) or Rejection (`POST /{id}/reject-owner`)
 * - Audit details modal with two-stage review timestamps and remarks.
 * 
 * Owned by Dhyanalakshmi under Milestone 2 Task T11.
 */
export default function AccessRequestsReviewSection({ canManageListings = false, userRole = "" }) {
  const toast = useToast();

  const isDepartmentHead = userRole === "DEPARTMENT_HEAD";
  const isSystemAdmin = userRole === "SYSTEM_ADMINISTRATOR";
  const [requestSubTab, setRequestSubTab] = useState(isDepartmentHead ? "department" : "incoming"); // 'incoming' | 'outgoing' | 'department'
  const [accessRequests, setAccessRequests] = useState([]);
  const [requestsLoading, setRequestsLoading] = useState(false);
  const [requestsError, setRequestsError] = useState(null);
  const [requestsPage, setRequestsPage] = useState(0);
  const [requestsTotalPages, setRequestsTotalPages] = useState(1);

  // Review modal state (Forward, Approve, Reject)
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [selectedRequestForReview, setSelectedRequestForReview] = useState(null);
  const [reviewActionType, setReviewActionType] = useState(""); // 'forward' | 'reject-requesting' | 'approve' | 'reject-owner'
  const [reviewRemarks, setReviewRemarks] = useState("");
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  // Details audit modal
  const [detailsModalOpen, setDetailsModalOpen] = useState(false);
  const [selectedRequestDetails, setSelectedRequestDetails] = useState(null);

  const fetchAccessRequests = useCallback(async () => {
    setRequestsLoading(true);
    setRequestsError(null);
    try {
      let endpoint = "/api/sharing/access-requests/owner-institution";
      if (requestSubTab === "department") {
        endpoint = "/api/sharing/access-requests/department";
      } else if (requestSubTab === "outgoing") {
        endpoint = "/api/sharing/access-requests/requesting-institution";
      }

      const resp = await api.get(endpoint, {
        params: {
          page: requestsPage,
          size: 10,
        },
      });
      if (resp.data?.content) {
        setAccessRequests(resp.data.content);
        setRequestsTotalPages(resp.data.totalPages || 1);
      } else if (Array.isArray(resp.data)) {
        setAccessRequests(resp.data);
        setRequestsTotalPages(1);
      } else {
        setAccessRequests([]);
      }
    } catch (err) {
      console.error("Failed to fetch access requests:", err);
      setRequestsError(err.response?.data?.message || "Failed to load access requests.");
    } finally {
      setRequestsLoading(false);
    }
  }, [requestSubTab, requestsPage]);

  useEffect(() => {
    fetchAccessRequests();
  }, [fetchAccessRequests]);

  const handleOpenReviewModal = (req, action) => {
    setSelectedRequestForReview(req);
    setReviewActionType(action);
    setReviewRemarks("");
    setReviewModalOpen(true);
  };

  const handleSubmitReviewAction = async (e) => {
    e.preventDefault();
    const isRejection =
      reviewActionType === "reject-requesting" || reviewActionType === "reject-owner";
    if (isRejection && !reviewRemarks.trim()) {
      toast.error("Rejection remarks are mandatory. Please provide a clear explanation.");
      return;
    }

    setReviewSubmitting(true);
    try {
      const payload = reviewRemarks.trim() ? { remarks: reviewRemarks.trim() } : {};
      const endpoint = `/api/sharing/access-requests/${selectedRequestForReview.id}/${reviewActionType}`;
      await api.post(endpoint, payload);

      if (reviewActionType === "forward") {
        toast.success("Access request forwarded to equipment owner institution for final review.");
      } else if (reviewActionType === "approve") {
        toast.success("Access request approved! Bilateral sharing agreement and access grant created.");
      } else {
        toast.success("Access request rejected.");
      }

      setReviewModalOpen(false);
      fetchAccessRequests();
    } catch (err) {
      console.error("Review action failed:", err);
      toast.error(err.response?.data?.message || "Failed to submit review decision.");
    } finally {
      setReviewSubmitting(false);
    }
  };

  return (
    <section>
      {/* T11 Scope Attribution Note */}
      <div
        style={{
          padding: "10px 14px",
          background: "#f0fdf4",
          border: "1px solid #bbf7d0",
          borderRadius: "8px",
          fontSize: "12px",
          color: "#166534",
          marginBottom: "16px",
          display: "flex",
          alignItems: "center",
          gap: "8px",
        }}
      >
        <span>📌</span>
        <span>
          <strong>Milestone 2 Task T11 Component:</strong> Multi-tier access request review workflow (Home Institution Endorsement &amp; Partner Equipment Owner Approval).
        </span>
      </div>

      {requestsError && (
        <div
          style={{
            padding: "16px",
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "8px",
            color: "#b91c1c",
            marginBottom: "16px",
          }}
        >
          ⚠️ {requestsError}
        </div>
      )}

      {/* Sub-tab selection */}
      <div style={{ display: "flex", gap: "8px", marginBottom: "16px", flexWrap: "wrap" }}>
        {(isDepartmentHead || isSystemAdmin) && (
          <button
            type="button"
            onClick={() => {
              setRequestSubTab("department");
              setRequestsPage(0);
            }}
            style={{
              padding: "8px 16px",
              borderRadius: "8px",
              border: requestSubTab === "department" ? "2px solid #2563eb" : "1px solid #cbd5e1",
              background: requestSubTab === "department" ? "#eff6ff" : "#fff",
              color: requestSubTab === "department" ? "#1e40af" : "#475569",
              fontWeight: 600,
              fontSize: "14px",
              cursor: "pointer",
            }}
          >
            🏢 Department Requests
          </button>
        )}

        <button
          type="button"
          onClick={() => {
            setRequestSubTab("incoming");
            setRequestsPage(0);
          }}
          style={{
            padding: "8px 16px",
            borderRadius: "8px",
            border: requestSubTab === "incoming" ? "2px solid #2563eb" : "1px solid #cbd5e1",
            background: requestSubTab === "incoming" ? "#eff6ff" : "#fff",
            color: requestSubTab === "incoming" ? "#1e40af" : "#475569",
            fontWeight: 600,
            fontSize: "14px",
            cursor: "pointer",
          }}
        >
          📥 Incoming Requests (as Equipment Owner)
        </button>

        <button
          type="button"
          onClick={() => {
            setRequestSubTab("outgoing");
            setRequestsPage(0);
          }}
          style={{
            padding: "8px 16px",
            borderRadius: "8px",
            border: requestSubTab === "outgoing" ? "2px solid #2563eb" : "1px solid #cbd5e1",
            background: requestSubTab === "outgoing" ? "#eff6ff" : "#fff",
            color: requestSubTab === "outgoing" ? "#1e40af" : "#475569",
            fontWeight: 600,
            fontSize: "14px",
            cursor: "pointer",
          }}
        >
          📤 Outgoing Requests (from Our Researchers)
        </button>
      </div>

      <div
        style={{
          background: "#fff",
          borderRadius: "12px",
          border: "1px solid #e2e8f0",
          overflow: "hidden",
          boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
        }}
      >
        <div
          style={{
            padding: "16px 20px",
            borderBottom: "1px solid #e2e8f0",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            background: "#f8fafc",
          }}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#1e293b" }}>
              {requestSubTab === "department"
                ? "Department Access Requests"
                : requestSubTab === "incoming"
                ? "Incoming Partner Access Requests"
                : "Requests Forwarded by Our Members"}
            </h3>
            <span style={{ fontSize: "13px", color: "#64748b" }}>
              {requestSubTab === "department"
                ? "Cross-institution access requests submitted by researchers in your department"
                : requestSubTab === "incoming"
                ? "Requests from partner institutions forwarded to you for final approval or rejection"
                : "Requests submitted by your institution's researchers requiring internal endorsement/forwarding"}
            </span>
          </div>
          <button
            type="button"
            onClick={fetchAccessRequests}
            style={{
              background: "#fff",
              border: "1px solid #cbd5e1",
              borderRadius: "6px",
              padding: "6px 12px",
              fontSize: "13px",
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            <span>🔄</span> Refresh
          </button>
        </div>

        {requestsLoading ? (
          <div style={{ padding: "48px", textAlign: "center", color: "#64748b" }}>
            <div className="spinner" style={{ margin: "0 auto 12px auto" }} />
            Loading access requests...
          </div>
        ) : accessRequests.length === 0 ? (
          <div style={{ padding: "48px 24px", textAlign: "center", color: "#64748b" }}>
            <p style={{ fontSize: "16px", fontWeight: 600, margin: "0 0 8px 0", color: "#334155" }}>
              No access requests to review
            </p>
            <p style={{ margin: 0, fontSize: "14px" }}>
              {requestSubTab === "incoming"
                ? "You have no pending requests awaiting owner approval from partner institutions."
                : "No outgoing requests are currently waiting for requesting institution administrator action."}
            </p>
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
              <thead>
                <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569", fontWeight: 600 }}>
                  <th style={{ padding: "12px 16px" }}>Req #</th>
                  <th style={{ padding: "12px 16px" }}>Requester</th>
                  <th style={{ padding: "12px 16px" }}>Equipment</th>
                  <th style={{ padding: "12px 16px" }}>Institutions</th>
                  <th style={{ padding: "12px 16px" }}>Dates</th>
                  <th style={{ padding: "12px 16px" }}>Status</th>
                  <th style={{ padding: "12px 16px", textAlign: "right" }}>Review Actions</th>
                </tr>
              </thead>
              <tbody>
                {accessRequests.map((r) => (
                  <tr key={r.id} style={{ borderBottom: "1px solid #f1f5f9" }}>
                    <td style={{ padding: "14px 16px", fontWeight: 600, color: "#0f172a" }}>
                      #{r.id}
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <div style={{ fontWeight: 600, color: "#0f172a" }}>{r.requesterName}</div>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>{r.requesterEmail}</div>
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <div style={{ fontWeight: 600, color: "#0f172a" }}>{r.equipmentName}</div>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>Asset #{r.assetTag}</div>
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <div style={{ fontSize: "13px", color: "#1e293b" }}>
                        From: <strong>{r.requestingInstitutionName}</strong>
                      </div>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>
                        To: {r.ownerInstitutionName}
                      </div>
                    </td>
                    <td style={{ padding: "14px 16px", color: "#475569" }}>
                      <div>{fmtDate(r.startDate)} – {fmtDate(r.endDate)}</div>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>
                        Submitted: {fmtDate(r.createdAt)}
                      </div>
                    </td>
                    <td style={{ padding: "14px 16px" }}>
                      <span
                        style={{
                          padding: "4px 10px",
                          borderRadius: "12px",
                          fontSize: "12px",
                          fontWeight: 700,
                          background:
                            r.status === "APPROVED"
                              ? "#dcfce7"
                              : r.status === "FORWARDED"
                              ? "#dbeafe"
                              : r.status === "PENDING"
                              ? "#fef3c7"
                              : r.status === "REJECTED"
                              ? "#fee2e2"
                              : "#f1f5f9",
                          color:
                            r.status === "APPROVED"
                              ? "#15803d"
                              : r.status === "FORWARDED"
                              ? "#1d4ed8"
                              : r.status === "PENDING"
                              ? "#b45309"
                              : r.status === "REJECTED"
                              ? "#b91c1c"
                              : "#475569",
                        }}
                      >
                        {r.status}
                      </span>
                    </td>
                    <td style={{ padding: "14px 16px", textAlign: "right" }}>
                      <div style={{ display: "inline-flex", gap: "6px", justifyContent: "flex-end" }}>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedRequestDetails(r);
                            setDetailsModalOpen(true);
                          }}
                          style={{
                            padding: "6px 10px",
                            background: "#f1f5f9",
                            border: "1px solid #cbd5e1",
                            borderRadius: "6px",
                            cursor: "pointer",
                            fontSize: "12px",
                            fontWeight: 500,
                            color: "#334155",
                          }}
                        >
                          ℹ️ Details
                        </button>

                        {/* Actions for Step 1: Requesting Admin Review (Status PENDING) */}
                        {requestSubTab === "outgoing" && r.status === "PENDING" && canManageListings && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleOpenReviewModal(r, "forward")}
                              style={{
                                padding: "6px 10px",
                                background: "#2563eb",
                                color: "#fff",
                                border: "none",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                              }}
                            >
                              Forward ➡️
                            </button>
                            <button
                              type="button"
                              onClick={() => handleOpenReviewModal(r, "reject-requesting")}
                              style={{
                                padding: "6px 10px",
                                background: "#fee2e2",
                                color: "#b91c1c",
                                border: "1px solid #fecaca",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                              }}
                            >
                              Reject ❌
                            </button>
                          </>
                        )}

                        {/* Actions for Step 2: Owner Admin Review (Status FORWARDED) */}
                        {requestSubTab === "incoming" && r.status === "FORWARDED" && canManageListings && (
                          <>
                            <button
                              type="button"
                              onClick={() => handleOpenReviewModal(r, "approve")}
                              style={{
                                padding: "6px 10px",
                                background: "#16a34a",
                                color: "#fff",
                                border: "none",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                              }}
                            >
                              Approve ✅
                            </button>
                            <button
                              type="button"
                              onClick={() => handleOpenReviewModal(r, "reject-owner")}
                              style={{
                                padding: "6px 10px",
                                background: "#fee2e2",
                                color: "#b91c1c",
                                border: "1px solid #fecaca",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                              }}
                            >
                              Reject ❌
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination bar */}
        {requestsTotalPages > 1 && (
          <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "12px", padding: "14px 20px", borderTop: "1px solid #e2e8f0", background: "#f8fafc" }}>
            <button
              type="button"
              disabled={requestsPage === 0}
              onClick={() => setRequestsPage((p) => Math.max(0, p - 1))}
              style={{
                padding: "6px 14px",
                borderRadius: "6px",
                border: "1px solid #cbd5e1",
                background: requestsPage === 0 ? "#f1f5f9" : "#fff",
                cursor: requestsPage === 0 ? "not-allowed" : "pointer",
                fontSize: "13px",
                fontWeight: 500,
              }}
            >
              ← Previous
            </button>
            <span style={{ fontSize: "13px", color: "#475569" }}>
              Page {requestsPage + 1} of {requestsTotalPages}
            </span>
            <button
              type="button"
              disabled={requestsPage >= requestsTotalPages - 1}
              onClick={() => setRequestsPage((p) => Math.min(requestsTotalPages - 1, p + 1))}
              style={{
                padding: "6px 14px",
                borderRadius: "6px",
                border: "1px solid #cbd5e1",
                background: requestsPage >= requestsTotalPages - 1 ? "#f1f5f9" : "#fff",
                cursor: requestsPage >= requestsTotalPages - 1 ? "not-allowed" : "pointer",
                fontSize: "13px",
                fontWeight: 500,
              }}
            >
              Next →
            </button>
          </div>
        )}
      </div>

      {/* MODAL: REVIEW DECISION (Forward, Approve, Reject) */}
      {reviewModalOpen && selectedRequestForReview && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(15, 23, 42, 0.6)",
            backdropFilter: "blur(4px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
            padding: "16px",
          }}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: "16px",
              maxWidth: "520px",
              width: "100%",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
            }}
          >
            <div
              style={{
                padding: "20px 24px",
                borderBottom: "1px solid #e2e8f0",
                background:
                  reviewActionType === "approve"
                    ? "#f0fdf4"
                    : reviewActionType === "forward"
                    ? "#eff6ff"
                    : "#fef2f2",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#0f172a" }}>
                {reviewActionType === "forward" && "Forward Access Request to Owner Admin"}
                {reviewActionType === "approve" && "Approve Inter-Institution Access Request"}
                {(reviewActionType === "reject-requesting" || reviewActionType === "reject-owner") &&
                  "Reject Access Request"}
              </h3>
              <button
                type="button"
                onClick={() => setReviewModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmitReviewAction} style={{ padding: "24px" }}>
              <div style={{ marginBottom: "16px", fontSize: "14px", color: "#334155" }}>
                <div>Request: <strong>#{selectedRequestForReview.id}</strong></div>
                <div>Requester: <strong>{selectedRequestForReview.requesterName}</strong> ({selectedRequestForReview.requesterEmail})</div>
                <div>Equipment: <strong>{selectedRequestForReview.equipmentName}</strong></div>
                <div style={{ marginTop: "4px", fontSize: "13px", color: "#64748b" }}>
                  Purpose: <em>"{selectedRequestForReview.purpose}"</em>
                </div>
              </div>

              {reviewActionType === "approve" && (
                <div style={{ padding: "12px", background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: "8px", fontSize: "13px", color: "#166534", marginBottom: "16px" }}>
                  ✅ Approving this request will immediately create a bilateral <strong>Sharing Agreement</strong> and grant <strong>{selectedRequestForReview.requesterName}</strong> permission to book this equipment.
                </div>
              )}

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                  {reviewActionType.startsWith("reject") ? "Mandatory Rejection Remarks *" : "Reviewer Remarks (Optional)"}
                </label>
                <textarea
                  rows="3"
                  required={reviewActionType.startsWith("reject")}
                  value={reviewRemarks}
                  onChange={(e) => setReviewRemarks(e.target.value)}
                  placeholder={
                    reviewActionType.startsWith("reject")
                      ? "Explain why the request cannot be approved..."
                      : "Add any guidance or laboratory conditions for the researcher..."
                  }
                  style={{
                    width: "100%",
                    padding: "10px 12px",
                    borderRadius: "8px",
                    border: "1px solid #cbd5e1",
                    fontSize: "13px",
                    fontFamily: "inherit",
                  }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
                <button
                  type="button"
                  onClick={() => setReviewModalOpen(false)}
                  style={{ padding: "10px 16px", borderRadius: "8px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={reviewSubmitting}
                  style={{
                    padding: "10px 20px",
                    borderRadius: "8px",
                    border: "none",
                    background:
                      reviewActionType === "approve"
                        ? "#16a34a"
                        : reviewActionType === "forward"
                        ? "#2563eb"
                        : "#dc2626",
                    color: "#fff",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  {reviewSubmitting
                    ? "Submitting..."
                    : reviewActionType === "forward"
                    ? "Forward Request"
                    : reviewActionType === "approve"
                    ? "Confirm Approval"
                    : "Reject Request"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: REQUEST DETAILS (Audit Trail) */}
      {detailsModalOpen && selectedRequestDetails && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(15, 23, 42, 0.6)",
            backdropFilter: "blur(4px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
            padding: "16px",
          }}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: "16px",
              maxWidth: "600px",
              width: "100%",
              maxHeight: "90vh",
              overflowY: "auto",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
            }}
          >
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center", background: "#f8fafc" }}>
              <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                Access Request Audit Details
              </h3>
              <button
                type="button"
                onClick={() => setDetailsModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <div style={{ padding: "24px", fontSize: "14px" }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "16px" }}>
                <div>
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Request ID:</span>
                  <div style={{ fontWeight: 600 }}>#{selectedRequestDetails.id}</div>
                </div>
                <div>
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Current Status:</span>
                  <div>
                    <span style={{ padding: "2px 8px", borderRadius: "10px", fontSize: "12px", fontWeight: 700, background: "#f1f5f9", color: "#1e293b" }}>
                      {selectedRequestDetails.status}
                    </span>
                  </div>
                </div>
                <div>
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Requester:</span>
                  <div style={{ fontWeight: 600 }}>{selectedRequestDetails.requesterName}</div>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>{selectedRequestDetails.requesterEmail}</div>
                </div>
                <div>
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Target Equipment:</span>
                  <div style={{ fontWeight: 600 }}>{selectedRequestDetails.equipmentName}</div>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>Asset #{selectedRequestDetails.assetTag}</div>
                </div>
              </div>

              <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0", marginBottom: "16px" }}>
                <span style={{ fontSize: "12px", color: "#64748b" }}>Research Purpose:</span>
                <p style={{ margin: "4px 0 0 0", color: "#1e293b" }}>{selectedRequestDetails.purpose}</p>
              </div>

              <div style={{ marginBottom: "16px" }}>
                <span style={{ fontSize: "12px", color: "#64748b" }}>Validity Window:</span>
                <div style={{ fontWeight: 600, color: "#1e293b" }}>
                  {fmtDate(selectedRequestDetails.startDate)} – {fmtDate(selectedRequestDetails.endDate)}
                </div>
              </div>

              {/* Reviewer 1 (Requesting Admin) Audit */}
              <div style={{ padding: "12px", background: "#f0fdf4", borderRadius: "8px", border: "1px solid #bbf7d0", marginBottom: "12px" }}>
                <div style={{ fontWeight: 600, color: "#166534", fontSize: "13px" }}>Stage 1: Home Institution Endorsement</div>
                {selectedRequestDetails.requesterReviewedBy ? (
                  <div style={{ fontSize: "13px", marginTop: "4px", color: "#1e293b" }}>
                    <div>Reviewed By: <strong>{selectedRequestDetails.requesterReviewedByName || `#${selectedRequestDetails.requesterReviewedBy}`}</strong></div>
                    <div>Date: {fmtDateTime(selectedRequestDetails.requesterReviewedAt)}</div>
                    {selectedRequestDetails.requesterReviewRemarks && (
                      <div style={{ marginTop: "4px", fontStyle: "italic" }}>
                        Remarks: "{selectedRequestDetails.requesterReviewRemarks}"
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                    Pending initial review by home institution administrator.
                  </div>
                )}
              </div>

              {/* Reviewer 2 (Owner Admin) Audit */}
              <div style={{ padding: "12px", background: "#eff6ff", borderRadius: "8px", border: "1px solid #bfdbfe", marginBottom: "16px" }}>
                <div style={{ fontWeight: 600, color: "#1e40af", fontSize: "13px" }}>Stage 2: Equipment Owner Final Decision</div>
                {selectedRequestDetails.finalReviewedBy ? (
                  <div style={{ fontSize: "13px", marginTop: "4px", color: "#1e293b" }}>
                    <div>Decision By: <strong>{selectedRequestDetails.finalReviewedByName || `#${selectedRequestDetails.finalReviewedBy}`}</strong></div>
                    <div>Date: {fmtDateTime(selectedRequestDetails.finalReviewedAt)}</div>
                    {selectedRequestDetails.finalReviewRemarks && (
                      <div style={{ marginTop: "4px", fontStyle: "italic" }}>
                        Remarks: "{selectedRequestDetails.finalReviewRemarks}"
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                    {selectedRequestDetails.status === "PENDING"
                      ? "Awaiting Stage 1 forwarding before owner review."
                      : "Awaiting final decision from equipment owner administrator."}
                  </div>
                )}
              </div>

              <div style={{ textAlign: "right" }}>
                <button
                  type="button"
                  onClick={() => setDetailsModalOpen(false)}
                  style={{ padding: "8px 16px", borderRadius: "6px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

