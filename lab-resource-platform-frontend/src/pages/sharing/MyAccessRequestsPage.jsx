import { useState, useEffect, useCallback, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";

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

export default function MyAccessRequestsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusFilter, setStatusFilter] = useState("ALL");

  // Withdraw action modal state
  const [withdrawModalOpen, setWithdrawModalOpen] = useState(false);
  const [selectedRequestForWithdraw, setSelectedRequestForWithdraw] = useState(null);
  const [withdrawSubmitting, setWithdrawSubmitting] = useState(false);

  // Request audit details modal
  const [detailsModalOpen, setDetailsModalOpen] = useState(false);
  const [selectedDetails, setSelectedDetails] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search your access requests by equipment or institution...");
  }, [setSearchPlaceholder]);

  const fetchMyRequests = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await api.get("/api/sharing/access-requests/my", {
        params: {
          page,
          size: 10,
        },
      });
      if (resp.data?.content) {
        setRequests(resp.data.content);
        setTotalPages(resp.data.totalPages || 1);
      } else if (Array.isArray(resp.data)) {
        setRequests(resp.data);
        setTotalPages(1);
      } else {
        setRequests([]);
      }
    } catch (err) {
      console.error("Failed to load user access requests:", err);
      setError(err.response?.data?.message || "Failed to load your access requests.");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchMyRequests();
  }, [fetchMyRequests]);

  const handleOpenWithdraw = (req) => {
    setSelectedRequestForWithdraw(req);
    setWithdrawModalOpen(true);
  };

  const handleConfirmWithdraw = async () => {
    if (!selectedRequestForWithdraw) return;
    setWithdrawSubmitting(true);
    try {
      await api.post(`/api/sharing/access-requests/${selectedRequestForWithdraw.id}/withdraw`);
      toast.success("Access request withdrawn successfully.");
      setWithdrawModalOpen(false);
      fetchMyRequests();
    } catch (err) {
      console.error("Failed to withdraw access request:", err);
      toast.error(err.response?.data?.message || "Failed to withdraw access request.");
    } finally {
      setWithdrawSubmitting(false);
    }
  };

  // Filter requests
  const filteredRequests = useMemo(() => {
    let list = requests;
    if (statusFilter !== "ALL") {
      list = list.filter((r) => r.status === statusFilter);
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      list = list.filter(
        (r) =>
          r.equipmentName?.toLowerCase().includes(q) ||
          r.assetTag?.toLowerCase().includes(q) ||
          r.ownerInstitutionName?.toLowerCase().includes(q) ||
          r.purpose?.toLowerCase().includes(q)
      );
    }
    return list;
  }, [requests, statusFilter, searchTerm]);

  return (
    <div style={{ padding: "24px", maxWidth: "1280px", margin: "0 auto" }}>
      {/* Top Header */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "24px",
          flexWrap: "wrap",
          gap: "16px",
        }}
      >
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "4px" }}>
            <span style={{ fontSize: "28px" }}>📑</span>
            <h1 style={{ fontSize: "24px", fontWeight: 700, color: "#0f172a", margin: 0 }}>
              My Inter-Institution Access Requests
            </h1>
          </div>
          <p style={{ color: "#64748b", margin: 0, fontSize: "14px" }}>
            Track the approval lifecycle of your requests to access and book instruments at partner institutions.
          </p>
        </div>

        <div style={{ display: "flex", gap: "12px" }}>
          <Link
            to="/dashboard/sharing/external-booking"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 18px",
              borderRadius: "8px",
              background: "#2563eb",
              color: "#fff",
              textDecoration: "none",
              fontWeight: 600,
              fontSize: "14px",
              boxShadow: "0 2px 4px rgba(37,99,235,0.2)",
            }}
          >
            <span>🌐</span>
            <span>Browse Partner Instruments</span>
          </Link>
        </div>
      </div>

      {/* Filter and Status Counts Bar */}
      <div
        style={{
          background: "#fff",
          borderRadius: "12px",
          padding: "16px 20px",
          border: "1px solid #e2e8f0",
          marginBottom: "20px",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "12px",
        }}
      >
        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
          {["ALL", "PENDING", "FORWARDED", "APPROVED", "REJECTED", "WITHDRAWN"].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => setStatusFilter(st)}
              style={{
                padding: "6px 14px",
                borderRadius: "20px",
                fontSize: "13px",
                fontWeight: 600,
                border: statusFilter === st ? "1px solid #2563eb" : "1px solid #cbd5e1",
                background: statusFilter === st ? "#eff6ff" : "#fff",
                color: statusFilter === st ? "#2563eb" : "#475569",
                cursor: "pointer",
                transition: "all 0.15s",
              }}
            >
              {st === "ALL" ? "All Requests" : st}
            </button>
          ))}
        </div>

        <button
          type="button"
          onClick={fetchMyRequests}
          style={{
            background: "#f8fafc",
            border: "1px solid #cbd5e1",
            borderRadius: "6px",
            padding: "6px 12px",
            fontSize: "13px",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            color: "#334155",
            fontWeight: 500,
          }}
        >
          <span>🔄</span> Refresh
        </button>
      </div>

      {/* Error state */}
      {error && (
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
          ⚠️ {error}
        </div>
      )}

      {/* Requests Content */}
      {loading ? (
        <div style={{ padding: "64px", textAlign: "center", color: "#64748b" }}>
          <div className="spinner" style={{ margin: "0 auto 12px auto" }} />
          Loading your access requests...
        </div>
      ) : filteredRequests.length === 0 ? (
        <div
          style={{
            background: "#fff",
            borderRadius: "12px",
            padding: "64px 24px",
            textAlign: "center",
            border: "1px solid #e2e8f0",
          }}
        >
          <span style={{ fontSize: "40px", display: "block", marginBottom: "12px" }}>📬</span>
          <h3 style={{ fontSize: "18px", fontWeight: 700, color: "#1e293b", margin: "0 0 8px 0" }}>
            No access requests found
          </h3>
          <p style={{ color: "#64748b", fontSize: "14px", margin: "0 0 20px 0" }}>
            {statusFilter !== "ALL" || searchTerm
              ? "No requests match your current filters."
              : "You have not submitted any inter-institution access requests yet."}
          </p>
          <Link
            to="/dashboard/sharing/external-booking"
            style={{
              padding: "10px 20px",
              borderRadius: "8px",
              background: "#2563eb",
              color: "#fff",
              textDecoration: "none",
              fontWeight: 600,
              fontSize: "14px",
              display: "inline-block",
            }}
          >
            Explore Partner Instruments
          </Link>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
          {filteredRequests.map((r) => (
            <div
              key={r.id}
              style={{
                background: "#fff",
                borderRadius: "12px",
                border: "1px solid #e2e8f0",
                padding: "20px",
                boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
                display: "flex",
                flexDirection: "column",
                gap: "16px",
              }}
            >
              {/* Card Header */}
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  flexWrap: "wrap",
                  gap: "12px",
                }}
              >
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                    <span style={{ fontSize: "12px", fontWeight: 700, color: "#64748b" }}>
                      REQUEST #{r.id}
                    </span>
                    <span style={{ color: "#cbd5e1" }}>•</span>
                    <span style={{ fontSize: "12px", color: "#64748b" }}>
                      Submitted {fmtDateTime(r.createdAt)}
                    </span>
                  </div>
                  <h3 style={{ margin: "0 0 4px 0", fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                    {r.equipmentName}
                  </h3>
                  <div style={{ fontSize: "13px", color: "#475569" }}>
                    Asset #{r.assetTag} • Owner: <strong>{r.ownerInstitutionName}</strong>
                  </div>
                </div>

                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                  <span
                    style={{
                      padding: "6px 14px",
                      borderRadius: "16px",
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
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                    }}
                  >
                    <span
                      style={{
                        width: "8px",
                        height: "8px",
                        borderRadius: "50%",
                        background:
                          r.status === "APPROVED"
                            ? "#16a34a"
                            : r.status === "FORWARDED"
                            ? "#2563eb"
                            : r.status === "PENDING"
                            ? "#d97706"
                            : r.status === "REJECTED"
                            ? "#dc2626"
                            : "#64748b",
                      }}
                    />
                    {r.status}
                  </span>
                </div>
              </div>

              {/* Research Purpose & Validity Window */}
              <div
                style={{
                  background: "#f8fafc",
                  borderRadius: "8px",
                  padding: "12px 16px",
                  border: "1px solid #e2e8f0",
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
                  gap: "12px",
                }}
              >
                <div>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>Requested Window:</div>
                  <div style={{ fontWeight: 600, color: "#1e293b", fontSize: "14px" }}>
                    📅 {fmtDate(r.startDate)} – {fmtDate(r.endDate)}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>Purpose:</div>
                  <div style={{ color: "#334155", fontSize: "13px", fontStyle: "italic" }}>
                    "{r.purpose}"
                  </div>
                </div>
              </div>

              {/* Visual Approval Progress Bar */}
              <div style={{ marginTop: "4px" }}>
                <div style={{ fontSize: "12px", fontWeight: 600, color: "#64748b", marginBottom: "8px" }}>
                  APPROVAL WORKFLOW LIFECYCLE
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
                  {/* Step 1: Submission */}
                  <div
                    style={{
                      padding: "10px",
                      borderRadius: "6px",
                      background: "#f0fdf4",
                      border: "1px solid #bbf7d0",
                      fontSize: "12px",
                    }}
                  >
                    <div style={{ fontWeight: 700, color: "#166534" }}>1. Submitted ✓</div>
                    <div style={{ color: "#475569", fontSize: "11px" }}>{fmtDateTime(r.createdAt)}</div>
                  </div>

                  {/* Step 2: Home Institution Review */}
                  <div
                    style={{
                      padding: "10px",
                      borderRadius: "6px",
                      background:
                        r.status === "REJECTED" && !r.requesterReviewedBy
                          ? "#fee2e2"
                          : r.requesterReviewedBy
                          ? "#f0fdf4"
                          : r.status === "PENDING"
                          ? "#fffbeb"
                          : "#f8fafc",
                      border:
                        r.requesterReviewedBy
                          ? "1px solid #bbf7d0"
                          : r.status === "PENDING"
                          ? "1px solid #fde68a"
                          : "1px solid #e2e8f0",
                      fontSize: "12px",
                    }}
                  >
                    <div
                      style={{
                        fontWeight: 700,
                        color:
                          r.requesterReviewedBy
                            ? "#166534"
                            : r.status === "PENDING"
                            ? "#b45309"
                            : "#64748b",
                      }}
                    >
                      2. Home Admin {r.requesterReviewedBy ? "Forwarded ✓" : r.status === "PENDING" ? "Pending..." : "—"}
                    </div>
                    {r.requesterReviewRemarks && (
                      <div style={{ color: "#475569", fontSize: "11px", fontStyle: "italic" }}>
                        "{r.requesterReviewRemarks}"
                      </div>
                    )}
                  </div>

                  {/* Step 3: Owner Institution Approval */}
                  <div
                    style={{
                      padding: "10px",
                      borderRadius: "6px",
                      background:
                        r.status === "APPROVED"
                          ? "#f0fdf4"
                          : r.status === "REJECTED" && r.finalReviewedBy
                          ? "#fee2e2"
                          : r.status === "FORWARDED"
                          ? "#eff6ff"
                          : "#f8fafc",
                      border:
                        r.status === "APPROVED"
                          ? "1px solid #bbf7d0"
                          : r.status === "FORWARDED"
                          ? "1px solid #bfdbfe"
                          : "1px solid #e2e8f0",
                      fontSize: "12px",
                    }}
                  >
                    <div
                      style={{
                        fontWeight: 700,
                        color:
                          r.status === "APPROVED"
                            ? "#166534"
                            : r.status === "REJECTED" && r.finalReviewedBy
                            ? "#b91c1c"
                            : r.status === "FORWARDED"
                            ? "#1d4ed8"
                            : "#64748b",
                      }}
                    >
                      3. Owner Admin {r.status === "APPROVED" ? "Approved 🎉" : r.status === "FORWARDED" ? "In Review..." : r.status === "REJECTED" && r.finalReviewedBy ? "Rejected ✕" : "—"}
                    </div>
                    {r.finalReviewRemarks && (
                      <div style={{ color: "#475569", fontSize: "11px", fontStyle: "italic" }}>
                        "{r.finalReviewRemarks}"
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div
                style={{
                  display: "flex",
                  justifyContent: "flex-end",
                  alignItems: "center",
                  gap: "12px",
                  borderTop: "1px solid #f1f5f9",
                  paddingTop: "14px",
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    setSelectedDetails(r);
                    setDetailsModalOpen(true);
                  }}
                  style={{
                    padding: "8px 14px",
                    borderRadius: "6px",
                    background: "#f1f5f9",
                    border: "1px solid #cbd5e1",
                    color: "#334155",
                    fontSize: "13px",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  ℹ️ Full Audit Trail
                </button>

                {/* If PENDING or FORWARDED, allow withdraw */}
                {(r.status === "PENDING" || r.status === "FORWARDED") && (
                  <button
                    type="button"
                    onClick={() => handleOpenWithdraw(r)}
                    style={{
                      padding: "8px 14px",
                      borderRadius: "6px",
                      background: "#fff1f2",
                      border: "1px solid #fecdd3",
                      color: "#be123c",
                      fontSize: "13px",
                      fontWeight: 600,
                      cursor: "pointer",
                    }}
                  >
                    Withdraw Request
                  </button>
                )}

                {/* If APPROVED, allow immediate booking */}
                {r.status === "APPROVED" && (
                  <Link
                    to={`/dashboard/sharing/external-booking?equipmentId=${r.equipmentId}`}
                    style={{
                      padding: "8px 16px",
                      borderRadius: "6px",
                      background: "#16a34a",
                      color: "#fff",
                      textDecoration: "none",
                      fontSize: "13px",
                      fontWeight: 600,
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                      boxShadow: "0 2px 4px rgba(22,163,74,0.2)",
                    }}
                  >
                    <span>📅</span>
                    <span>Book Equipment Now</span>
                  </Link>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: WITHDRAW CONFIRMATION                                   */}
      {/* ============================================================== */}
      {withdrawModalOpen && selectedRequestForWithdraw && (
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
              maxWidth: "460px",
              width: "100%",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
              padding: "24px",
            }}
          >
            <div style={{ textAlign: "center", marginBottom: "16px" }}>
              <span style={{ fontSize: "40px" }}>⚠️</span>
              <h3 style={{ margin: "8px 0 4px 0", fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                Withdraw Access Request?
              </h3>
              <p style={{ margin: 0, fontSize: "14px", color: "#64748b" }}>
                Are you sure you want to withdraw request <strong>#{selectedRequestForWithdraw.id}</strong> for <strong>{selectedRequestForWithdraw.equipmentName}</strong>?
              </p>
            </div>

            <div style={{ display: "flex", justifyContent: "center", gap: "12px", marginTop: "24px" }}>
              <button
                type="button"
                onClick={() => setWithdrawModalOpen(false)}
                style={{
                  padding: "10px 18px",
                  borderRadius: "8px",
                  border: "1px solid #cbd5e1",
                  background: "#fff",
                  color: "#475569",
                  fontWeight: 600,
                  cursor: "pointer",
                }}
              >
                No, Keep Request
              </button>
              <button
                type="button"
                disabled={withdrawSubmitting}
                onClick={handleConfirmWithdraw}
                style={{
                  padding: "10px 20px",
                  borderRadius: "8px",
                  border: "none",
                  background: "#dc2626",
                  color: "#fff",
                  fontWeight: 600,
                  cursor: "pointer",
                }}
              >
                {withdrawSubmitting ? "Withdrawing..." : "Yes, Withdraw"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: FULL AUDIT TRAIL DETAILS                                */}
      {/* ============================================================== */}
      {detailsModalOpen && selectedDetails && (
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
              maxWidth: "560px",
              width: "100%",
              maxHeight: "90vh",
              overflowY: "auto",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
            }}
          >
            <div
              style={{
                padding: "20px 24px",
                borderBottom: "1px solid #e2e8f0",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                background: "#f8fafc",
              }}
            >
              <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                Request Audit Trail
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
              <div style={{ marginBottom: "16px" }}>
                <div style={{ fontSize: "12px", color: "#64748b" }}>Equipment:</div>
                <div style={{ fontWeight: 700, color: "#0f172a", fontSize: "16px" }}>{selectedDetails.equipmentName}</div>
                <div style={{ fontSize: "12px", color: "#64748b" }}>Asset #{selectedDetails.assetTag} • {selectedDetails.ownerInstitutionName}</div>
              </div>

              <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0", marginBottom: "16px" }}>
                <div style={{ fontSize: "12px", color: "#64748b" }}>Purpose:</div>
                <div style={{ color: "#334155", marginTop: "2px" }}>{selectedDetails.purpose}</div>
              </div>

              {/* Stage 1 Audit */}
              <div style={{ padding: "12px", background: "#f0fdf4", borderRadius: "8px", border: "1px solid #bbf7d0", marginBottom: "12px" }}>
                <div style={{ fontWeight: 600, color: "#166534", fontSize: "13px" }}>Stage 1: Home Institution Endorsement</div>
                {selectedDetails.requesterReviewedBy ? (
                  <div style={{ fontSize: "13px", marginTop: "4px", color: "#1e293b" }}>
                    <div>Reviewer: <strong>{selectedDetails.requesterReviewedByName || `#${selectedDetails.requesterReviewedBy}`}</strong></div>
                    <div>Date: {fmtDateTime(selectedDetails.requesterReviewedAt)}</div>
                    {selectedDetails.requesterReviewRemarks && (
                      <div style={{ marginTop: "4px", fontStyle: "italic", color: "#334155" }}>
                        Remarks: "{selectedDetails.requesterReviewRemarks}"
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                    Pending initial review by your institution's administrator.
                  </div>
                )}
              </div>

              {/* Stage 2 Audit */}
              <div style={{ padding: "12px", background: "#eff6ff", borderRadius: "8px", border: "1px solid #bfdbfe", marginBottom: "16px" }}>
                <div style={{ fontWeight: 600, color: "#1e40af", fontSize: "13px" }}>Stage 2: Equipment Owner Final Decision</div>
                {selectedDetails.finalReviewedBy ? (
                  <div style={{ fontSize: "13px", marginTop: "4px", color: "#1e293b" }}>
                    <div>Reviewer: <strong>{selectedDetails.finalReviewedByName || `#${selectedDetails.finalReviewedBy}`}</strong></div>
                    <div>Date: {fmtDateTime(selectedDetails.finalReviewedAt)}</div>
                    {selectedDetails.finalReviewRemarks && (
                      <div style={{ marginTop: "4px", fontStyle: "italic", color: "#334155" }}>
                        Remarks: "{selectedDetails.finalReviewRemarks}"
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                    {selectedDetails.status === "PENDING"
                      ? "Awaiting Stage 1 forwarding before owner review."
                      : "Awaiting final decision from partner institution administrator."}
                  </div>
                )}
              </div>

              <div style={{ textAlign: "right" }}>
                <button
                  type="button"
                  onClick={() => setDetailsModalOpen(false)}
                  style={{
                    padding: "8px 16px",
                    borderRadius: "6px",
                    border: "1px solid #cbd5e1",
                    background: "#fff",
                    color: "#475569",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

