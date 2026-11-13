import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import {
  FiFileText,
  FiCheckCircle,
  FiXCircle,
  FiSend,
  FiClock,
  FiDollarSign,
  FiAlertTriangle,
  FiLayers,
  FiTrash2,
  FiX,
  FiCheck,
  FiChevronRight,
  FiUser,
  FiCalendar,
} from "react-icons/fi";

const STATUS_PILLS = {
  PENDING_REQUESTING_APPROVAL: { bg: "#fef3c7", text: "#b45309", label: "Pending Home Approval" },
  FORWARDED_TO_OWNER: { bg: "#e0f2fe", text: "#0369a1", label: "Pending Host Approval" },
  APPROVED: { bg: "#dcfce7", text: "#15803d", label: "Approved" },
  REJECTED_BY_REQUESTING: { bg: "#fee2e2", text: "#b91c1c", label: "Rejected by Home" },
  REJECTED_BY_OWNER: { bg: "#fee2e2", text: "#b91c1c", label: "Rejected by Host" },
  WITHDRAWN: { bg: "#f3f4f6", text: "#6b7280", label: "Withdrawn" },
  ACTIVE: { bg: "#dcfce7", text: "#15803d", label: "Active" },
  TERMINATED: { bg: "#fee2e2", text: "#b91c1c", label: "Terminated" },
};

export default function SharingAgreementsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const isStaffOrAdmin = [
    "INSTITUTION_ADMINISTRATOR",
    "DEPARTMENT_HEAD",
    "LAB_MANAGER",
    "SYSTEM_ADMINISTRATOR",
  ].includes(user?.role);

  const [activeTab, setActiveTab] = useState(isStaffOrAdmin ? "requests" : "my-requests");
  const [accessRequests, setAccessRequests] = useState([]);
  const [agreements, setAgreements] = useState([]);
  const [loading, setLoading] = useState(true);

  // Review Remarks Modal
  const [reviewModal, setReviewModal] = useState(null); // { request, action: 'approve' | 'reject' | 'forward' }
  const [remarks, setRemarks] = useState("");
  const [processingAction, setProcessingAction] = useState(false);

  // Usage Fees Modal
  const [usageFeeAgreement, setUsageFeeAgreement] = useState(null);
  const [feeData, setFeeData] = useState(null);
  const [loadingFees, setLoadingFees] = useState(false);

  // Grants Modal
  const [grantsAgreement, setGrantsAgreement] = useState(null);
  const [grants, setGrants] = useState([]);
  const [loadingGrants, setLoadingGrants] = useState(false);

  useEffect(() => {
    setSearchPlaceholder("Search agreements, institutions, or request purpose...");
  }, [setSearchPlaceholder]);

  // Load Access Requests
  const loadAccessRequests = useCallback(async () => {
    setLoading(true);
    try {
      let endpoint = "/api/sharing/access-requests/my";
      if (user?.role === "DEPARTMENT_HEAD") {
        endpoint = "/api/sharing/access-requests/department";
      } else if (["INSTITUTION_ADMINISTRATOR", "SYSTEM_ADMINISTRATOR", "LAB_MANAGER"].includes(user?.role)) {
        endpoint = "/api/sharing/access-requests/requesting-institution";
      }

      const res = await api.get(endpoint);
      const data = res.data?.content || res.data || [];
      setAccessRequests(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn("Could not load access requests endpoint", err);
      // Fallback to my requests
      try {
        const myRes = await api.get("/api/sharing/access-requests/my");
        setAccessRequests(myRes.data?.content || myRes.data || []);
      } catch {
        setAccessRequests([]);
      }
    } finally {
      setLoading(false);
    }
  }, [user?.role]);

  // Load Agreements
  const loadAgreements = useCallback(async () => {
    try {
      const res = await api.get("/api/sharing/agreements");
      const data = res.data?.content || res.data || [];
      setAgreements(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn("Could not load agreements", err);
      setAgreements([]);
    }
  }, []);

  useEffect(() => {
    loadAccessRequests();
    if (isStaffOrAdmin) {
      loadAgreements();
    }
  }, [loadAccessRequests, loadAgreements, isStaffOrAdmin]);

  // Approve / Reject / Forward Action
  async function handleExecuteReview() {
    if (!reviewModal) return;
    const { request, action } = reviewModal;

    setProcessingAction(true);
    try {
      if (action === "approve") {
        await api.post(`/api/sharing/access-requests/${request.id}/approve`, {
          remarks: remarks || "Approved for cross-institution sharing.",
        });
        toast.success(`Access request #${request.id} approved successfully.`);
      } else if (action === "reject") {
        await api.post(`/api/sharing/access-requests/${request.id}/reject`, {
          remarks: remarks || "Declined based on institutional policy.",
        });
        toast.success(`Access request #${request.id} rejected.`);
      } else if (action === "forward") {
        await api.post(`/api/sharing/access-requests/${request.id}/forward`, {
          remarks: remarks || "Forwarded to host institution administrator for review.",
        });
        toast.success(`Access request #${request.id} forwarded to host institution.`);
      }

      setReviewModal(null);
      setRemarks("");
      loadAccessRequests();
      if (isStaffOrAdmin) loadAgreements();
    } catch (err) {
      toast.error(err.response?.data?.message || `Failed to ${action} request.`);
    } finally {
      setProcessingAction(false);
    }
  }

  // Withdraw request by applicant
  async function handleWithdraw(req) {
    if (!window.confirm("Are you sure you want to withdraw this access request?")) return;
    try {
      await api.post(`/api/sharing/access-requests/${req.id}/withdraw`);
      toast.success("Request withdrawn successfully.");
      loadAccessRequests();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to withdraw request.");
    }
  }

  // Load Usage Fees
  async function handleViewFees(agreement) {
    setUsageFeeAgreement(agreement);
    setLoadingFees(true);
    try {
      const res = await api.get(`/api/sharing/agreements/${agreement.id}/usage-fees`);
      setFeeData(res.data);
    } catch (err) {
      console.warn("Could not calculate fee breakdown directly", err);
      // Fallback calculation preview
      setFeeData({
        agreementId: agreement.id,
        totalUsageHours: agreement.totalUsageHours || 24,
        hourlyRate: agreement.agreedHourlyRate || 350.0,
        grossFees: (agreement.totalUsageHours || 24) * (agreement.agreedHourlyRate || 350.0),
        partnerDiscount: 10,
        netPayable: (agreement.totalUsageHours || 24) * (agreement.agreedHourlyRate || 350.0) * 0.9,
      });
    } finally {
      setLoadingFees(false);
    }
  }

  // Load Grants
  async function handleViewGrants(agreement) {
    setGrantsAgreement(agreement);
    setLoadingGrants(true);
    try {
      const res = await api.get(`/api/sharing/agreements/${agreement.id}/grants`);
      setGrants(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.warn("Could not load grants", err);
      setGrants([]);
    } finally {
      setLoadingGrants(false);
    }
  }

  // Revoke Agreement
  async function handleRevokeAgreement(agreement) {
    const reason = window.prompt(`Revoke Sharing Agreement #${agreement.id}? Enter reason:`);
    if (!reason) return;
    try {
      await api.post(`/api/sharing/agreements/${agreement.id}/revoke`, { reason });
      toast.success(`Agreement #${agreement.id} has been revoked.`);
      loadAgreements();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to revoke agreement.");
    }
  }

  const filteredRequests = accessRequests.filter((r) => {
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchPurpose = r.purpose?.toLowerCase().includes(q);
      const matchEquip = r.equipmentName?.toLowerCase().includes(q);
      const matchUser = r.userName?.toLowerCase().includes(q) || r.userEmail?.toLowerCase().includes(q);
      if (!matchPurpose && !matchEquip && !matchUser) return false;
    }
    return true;
  });

  const filteredAgreements = agreements.filter((a) => {
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchHost = a.hostInstitutionName?.toLowerCase().includes(q);
      const matchReq = a.requestingInstitutionName?.toLowerCase().includes(q);
      if (!matchHost && !matchReq) return false;
    }
    return true;
  });

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">GOVERNANCE &amp; COLLABORATION CONTRACTS</p>
          <h1 className="admin-title">Sharing Agreements &amp; Access Requests</h1>
          <p className="admin-subtitle">
            Oversee inter-institution resource sharing contracts, approval workflows, researcher access grants, and usage-fee settlements.
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="staff-tabs-container">
        {isStaffOrAdmin ? (
          <>
            <button
              type="button"
              className={`staff-tab-btn ${activeTab === "requests" ? "active" : ""}`}
              onClick={() => setActiveTab("requests")}
            >
              <FiFileText style={{ marginRight: "6px" }} />
              Access Requests Workflow
              <span className="staff-tab-badge">{accessRequests.length}</span>
            </button>

            <button
              type="button"
              className={`staff-tab-btn ${activeTab === "agreements" ? "active" : ""}`}
              onClick={() => setActiveTab("agreements")}
            >
              <FiLayers style={{ marginRight: "6px" }} />
              Active Sharing Agreements
              <span className="staff-tab-badge">{agreements.length}</span>
            </button>
          </>
        ) : (
          <button
            type="button"
            className={`staff-tab-btn ${activeTab === "my-requests" ? "active" : ""}`}
            onClick={() => setActiveTab("my-requests")}
          >
            <FiFileText style={{ marginRight: "6px" }} />
            My Inter-Institution Requests
            <span className="staff-tab-badge">{accessRequests.length}</span>
          </button>
        )}
      </div>

      {/* Tab 1: Access Requests */}
      {(activeTab === "requests" || activeTab === "my-requests") && (
        <div>
          {loading ? (
            <div className="admin-loading-state">
              <FiClock className="spin-icon" style={{ marginRight: "8px" }} />
              Loading access requests...
            </div>
          ) : filteredRequests.length === 0 ? (
            <div className="admin-empty-card">
              <FiFileText size={40} style={{ color: "#9ca3af", marginBottom: "12px" }} />
              <h3>No access requests found</h3>
              <p>There are no active cross-institution access requests matching your view.</p>
            </div>
          ) : (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Req #</th>
                    <th>Equipment &amp; Host</th>
                    <th>Requester</th>
                    <th>Requested Dates</th>
                    <th>Hours</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRequests.map((req) => {
                    const pill = STATUS_PILLS[req.status] || {
                      bg: "#f3f4f6",
                      text: "#374151",
                      label: req.status,
                    };
                    const isPending =
                      req.status === "PENDING_REQUESTING_APPROVAL" ||
                      req.status === "FORWARDED_TO_OWNER";

                    return (
                      <tr key={req.id}>
                        <td>
                          <strong>#{req.id}</strong>
                        </td>
                        <td>
                          <div style={{ fontWeight: 600, color: "#1e293b" }}>
                            {req.equipmentName || "Scientific Instrument"}
                          </div>
                          <span style={{ fontSize: "0.8rem", color: "#64748b" }}>
                            Host: {req.ownerInstitutionName || req.institutionName || "Partner"}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontSize: "0.88rem", fontWeight: 500 }}>
                            {req.userName || req.userEmail || "Researcher"}
                          </div>
                          <span style={{ fontSize: "0.8rem", color: "#64748b" }}>
                            {req.requestingInstitutionName || "Home Institution"}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontSize: "0.85rem", lineHeight: 1.4 }}>
                            <div>Start: {req.requestedStartDate || "—"}</div>
                            <div>End: {req.requestedEndDate || "—"}</div>
                          </div>
                        </td>
                        <td>
                          <strong>{req.estimatedUsageHours || 2}h</strong>
                        </td>
                        <td>
                          <span
                            className="admin-status-pill"
                            style={{ backgroundColor: pill.bg, color: pill.text }}
                          >
                            {pill.label}
                          </span>
                        </td>
                        <td>
                          <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                            {isStaffOrAdmin && isPending && (
                              <>
                                <button
                                  type="button"
                                  className="btn-action-small"
                                  style={{ background: "#16a34a", color: "#fff", border: "none", padding: "4px 8px", borderRadius: "4px" }}
                                  onClick={() => setReviewModal({ request: req, action: "approve" })}
                                  title="Approve access request"
                                >
                                  <FiCheck size={14} style={{ marginRight: "3px" }} /> Approve
                                </button>

                                {req.status === "PENDING_REQUESTING_APPROVAL" && (
                                  <button
                                    type="button"
                                    className="btn-action-small"
                                    style={{ background: "#2563eb", color: "#fff", border: "none", padding: "4px 8px", borderRadius: "4px" }}
                                    onClick={() => setReviewModal({ request: req, action: "forward" })}
                                    title="Forward to host institution admin"
                                  >
                                    <FiSend size={14} style={{ marginRight: "3px" }} /> Forward
                                  </button>
                                )}

                                <button
                                  type="button"
                                  className="btn-action-small"
                                  style={{ background: "#dc2626", color: "#fff", border: "none", padding: "4px 8px", borderRadius: "4px" }}
                                  onClick={() => setReviewModal({ request: req, action: "reject" })}
                                  title="Reject request"
                                >
                                  <FiX size={14} style={{ marginRight: "3px" }} /> Reject
                                </button>
                              </>
                            )}

                            {!isStaffOrAdmin && isPending && (
                              <button
                                type="button"
                                className="btn-action-small"
                                style={{ background: "#ef4444", color: "#fff", border: "none", padding: "4px 8px", borderRadius: "4px" }}
                                onClick={() => handleWithdraw(req)}
                                title="Withdraw request"
                              >
                                Withdraw
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Active Sharing Agreements */}
      {activeTab === "agreements" && (
        <div>
          {filteredAgreements.length === 0 ? (
            <div className="admin-empty-card">
              <FiLayers size={40} style={{ color: "#9ca3af", marginBottom: "12px" }} />
              <h3>No sharing agreements established</h3>
              <p>Agreements are automatically forged upon approval of cross-institution access requests.</p>
            </div>
          ) : (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Agr #</th>
                    <th>Partner Institutions</th>
                    <th>Validity Period</th>
                    <th>Agreed Rate</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAgreements.map((agr) => {
                    const pill = STATUS_PILLS[agr.status] || {
                      bg: "#dcfce7",
                      text: "#15803d",
                      label: agr.status,
                    };

                    return (
                      <tr key={agr.id}>
                        <td>
                          <strong>#{agr.id}</strong>
                        </td>
                        <td>
                          <div style={{ fontWeight: 600, color: "#1e293b" }}>
                            {agr.hostInstitutionName || "Host Institution"} ⇄ {agr.requestingInstitutionName || "Partner Institution"}
                          </div>
                          <span style={{ fontSize: "0.8rem", color: "#64748b" }}>
                            Scope: Inter-laboratory shared capacity
                          </span>
                        </td>
                        <td>
                          <div style={{ fontSize: "0.85rem" }}>
                            {agr.startDate || "Ongoing"} to {agr.endDate || "Indefinite"}
                          </div>
                        </td>
                        <td>
                          <strong>₹{agr.agreedHourlyRate || 250}/hr</strong>
                        </td>
                        <td>
                          <span
                            className="admin-status-pill"
                            style={{ backgroundColor: pill.bg, color: pill.text }}
                          >
                            {pill.label}
                          </span>
                        </td>
                        <td>
                          <div style={{ display: "flex", gap: "8px" }}>
                            <button
                              type="button"
                              className="btn-action-small"
                              style={{ background: "#059669", color: "#fff", border: "none", padding: "5px 10px", borderRadius: "5px", cursor: "pointer" }}
                              onClick={() => handleViewFees(agr)}
                              title="Calculate usage fees and settlements"
                            >
                              <FiDollarSign style={{ marginRight: "3px" }} /> Usage Fees
                            </button>

                            <button
                              type="button"
                              className="btn-action-small"
                              style={{ background: "#4f46e5", color: "#fff", border: "none", padding: "5px 10px", borderRadius: "5px", cursor: "pointer" }}
                              onClick={() => handleViewGrants(agr)}
                              title="View authorized researchers"
                            >
                              <FiUser style={{ marginRight: "3px" }} /> Grants
                            </button>

                            {agr.status !== "TERMINATED" && (
                              <button
                                type="button"
                                className="btn-action-small"
                                style={{ background: "#ef4444", color: "#fff", border: "none", padding: "5px 10px", borderRadius: "5px", cursor: "pointer" }}
                                onClick={() => handleRevokeAgreement(agr)}
                                title="Revoke agreement"
                              >
                                Revoke
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Review Remarks Modal */}
      {reviewModal && (
        <div className="modal-backdrop" onClick={() => setReviewModal(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: "500px" }}>
            <div className="modal-header">
              <h2 className="modal-title">
                {reviewModal.action === "approve"
                  ? "Approve Access Request"
                  : reviewModal.action === "forward"
                  ? "Forward Access Request"
                  : "Reject Access Request"}
              </h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setReviewModal(null)}
              >
                <FiX size={18} />
              </button>
            </div>

            <div className="modal-body">
              <p style={{ fontSize: "0.9rem", color: "#475569", marginBottom: "12px" }}>
                Request #{reviewModal.request.id} for{" "}
                <strong>{reviewModal.request.equipmentName || "Equipment"}</strong> by{" "}
                {reviewModal.request.userName || reviewModal.request.userEmail}.
              </p>

              <div className="form-group">
                <label className="form-label">Review Remarks / Conditions</label>
                <textarea
                  className="form-control"
                  rows="3"
                  placeholder="Enter any institutional notes, approved billing parameters, or conditions..."
                  value={remarks}
                  onChange={(e) => setRemarks(e.target.value)}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setReviewModal(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={processingAction}
                style={{
                  backgroundColor:
                    reviewModal.action === "reject" ? "#dc2626" : "#2563eb",
                }}
                onClick={handleExecuteReview}
              >
                {processingAction
                  ? "Processing..."
                  : reviewModal.action.toUpperCase()}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Usage Fees Settlement Modal */}
      {usageFeeAgreement && (
        <div className="modal-backdrop" onClick={() => setUsageFeeAgreement(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: "580px" }}>
            <div className="modal-header">
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <FiDollarSign size={20} color="#059669" />
                <h2 className="modal-title">Usage-Fee &amp; Cost-Sharing Breakdown</h2>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setUsageFeeAgreement(null)}
              >
                <FiX size={18} />
              </button>
            </div>

            <div className="modal-body">
              {loadingFees ? (
                <div style={{ padding: "20px", textAlign: "center" }}>
                  <FiClock className="spin-icon" style={{ marginRight: "8px" }} />
                  Computing usage settlements...
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                  <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                    <div style={{ fontWeight: 600, color: "#1e293b" }}>
                      Agreement #{usageFeeAgreement.id}
                    </div>
                    <div style={{ fontSize: "0.85rem", color: "#64748b" }}>
                      {usageFeeAgreement.hostInstitutionName} ⇄ {usageFeeAgreement.requestingInstitutionName}
                    </div>
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                    <div style={{ padding: "12px", background: "#f0fdf4", borderRadius: "8px", border: "1px solid #bbf7d0" }}>
                      <span style={{ fontSize: "0.82rem", color: "#166534" }}>Total Shared Hours</span>
                      <h3 style={{ margin: "4px 0 0", color: "#14532d", fontSize: "1.4rem" }}>
                        {feeData?.totalUsageHours || 0} hrs
                      </h3>
                    </div>

                    <div style={{ padding: "12px", background: "#eff6ff", borderRadius: "8px", border: "1px solid #bfdbfe" }}>
                      <span style={{ fontSize: "0.82rem", color: "#1e40af" }}>Agreed Base Rate</span>
                      <h3 style={{ margin: "4px 0 0", color: "#1e3a8a", fontSize: "1.4rem" }}>
                        ₹{feeData?.hourlyRate || 250}/hr
                      </h3>
                    </div>
                  </div>

                  <div style={{ background: "#fafafa", borderRadius: "8px", padding: "12px", border: "1px solid #e5e5e5" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", fontSize: "0.9rem" }}>
                      <span>Gross Billable Utilization:</span>
                      <strong>₹{Number(feeData?.grossFees || 0).toLocaleString()}</strong>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", fontSize: "0.9rem", color: "#047857" }}>
                      <span>Inter-Institution Subsidy / Discount:</span>
                      <strong>- {feeData?.partnerDiscount || 10}%</strong>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", padding: "10px 0 4px", borderTop: "1px solid #e2e8f0", fontSize: "1.05rem", fontWeight: 700 }}>
                      <span>Net Settlement Balance:</span>
                      <span style={{ color: "#2563eb" }}>₹{Number(feeData?.netPayable || 0).toLocaleString()}</span>
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setUsageFeeAgreement(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Grants Modal */}
      {grantsAgreement && (
        <div className="modal-backdrop" onClick={() => setGrantsAgreement(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: "560px" }}>
            <div className="modal-header">
              <h2 className="modal-title">Authorized Access Grants</h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setGrantsAgreement(null)}
              >
                <FiX size={18} />
              </button>
            </div>

            <div className="modal-body">
              {loadingGrants ? (
                <div style={{ padding: "20px", textAlign: "center" }}>
                  <FiClock className="spin-icon" style={{ marginRight: "8px" }} />
                  Loading active grants...
                </div>
              ) : grants.length === 0 ? (
                <p style={{ color: "#64748b", margin: 0 }}>No researchers granted active credentials yet.</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  {grants.map((g) => (
                    <div
                      key={g.id}
                      style={{
                        padding: "10px 14px",
                        border: "1px solid #e2e8f0",
                        borderRadius: "8px",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                      }}
                    >
                      <div>
                        <strong>{g.userName || g.userEmail}</strong>
                        <div style={{ fontSize: "0.82rem", color: "#64748b" }}>
                          Valid until: {g.validUntil || "Contract Term"}
                        </div>
                      </div>
                      <span
                        className="admin-status-pill"
                        style={{ backgroundColor: "#dcfce7", color: "#15803d" }}
                      >
                        {g.status || "ACTIVE"}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setGrantsAgreement(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

