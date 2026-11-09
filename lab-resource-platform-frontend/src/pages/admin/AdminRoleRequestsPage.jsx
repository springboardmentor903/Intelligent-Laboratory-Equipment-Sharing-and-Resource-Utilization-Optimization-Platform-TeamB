import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

const ROLE_LABELS = {
  SYSTEM_ADMINISTRATOR: "System Administrator",
  INSTITUTION_ADMINISTRATOR: "Institution Administrator",
  DEPARTMENT_HEAD: "Department Head",
  LAB_MANAGER: "Lab Manager",
  LAB_TECHNICIAN: "Lab Technician",
  RESEARCHER_STUDENT: "Researcher / Student",
};

const ROLE_COLORS = {
  SYSTEM_ADMINISTRATOR: "#6d28d9",
  INSTITUTION_ADMINISTRATOR: "#1d4ed8",
  DEPARTMENT_HEAD: "#0369a1",
  LAB_MANAGER: "#047857",
  LAB_TECHNICIAN: "#b45309",
  RESEARCHER_STUDENT: "#6b7280",
};

export default function AdminRoleRequestsPage() {
  const toast = useToast();

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [activeTab, setActiveTab] = useState("pending"); // 'pending' | 'history'

  const [pendingRequests, setPendingRequests] = useState([]);
  const [historyRequests, setHistoryRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");

  // Reject modal
  const [rejectModalItem, setRejectModalItem] = useState(null);
  const [rejectReason, setRejectReason] = useState("");
  const [processingId, setProcessingId] = useState(null);

  const loadColleges = useCallback(async () => {
    try {
      const res = await api.get("/api/colleges");
      setColleges(res.data);
    } catch (err) {
      console.error("Could not load colleges", err);
    }
  }, []);

  const loadRequests = useCallback(async () => {
    setLoading(true);
    try {
      const [pendingRes, processedRes] = await Promise.all([
        api.get("/api/admin/technician-requests/pending"),
        api.get("/api/admin/technician-requests/processed"),
      ]);
      setPendingRequests(pendingRes.data || []);
      setHistoryRequests(processedRes.data || []);
    } catch (err) {
      toast.error("Could not load role requests. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadColleges();
    loadRequests();
  }, [loadColleges, loadRequests]);

  async function handleApprove(requestId) {
    setProcessingId(requestId);
    try {
      await api.post(`/api/admin/technician-requests/${requestId}/approve`);
      toast.success("Role access request approved. User account granted.");
      // Move from pending to history
      const approved = pendingRequests.find((r) => r.id === requestId);
      if (approved) {
        setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
        setHistoryRequests((prev) => [
          {
            ...approved,
            status: "APPROVED",
            reviewedAt: new Date().toISOString(),
          },
          ...prev,
        ]);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to approve request.");
    } finally {
      setProcessingId(null);
    }
  }

  async function handleConfirmReject() {
    if (!rejectModalItem) return;
    const requestId = rejectModalItem.id;
    setProcessingId(requestId);
    try {
      await api.post(`/api/admin/technician-requests/${requestId}/reject`, {
        reason: rejectReason.trim(),
      });
      toast.success("Request rejected.");
      const rejected = pendingRequests.find((r) => r.id === requestId);
      if (rejected) {
        setPendingRequests((prev) => prev.filter((r) => r.id !== requestId));
        setHistoryRequests((prev) => [
          {
            ...rejected,
            status: "REJECTED",
            rejectionReason: rejectReason.trim(),
            reviewedAt: new Date().toISOString(),
          },
          ...prev,
        ]);
      }
      setRejectModalItem(null);
      setRejectReason("");
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to reject request.");
    } finally {
      setProcessingId(null);
    }
  }

  // Filter helper by college & search
  function filterList(list) {
    return list.filter((item) => {
      if (selectedCollegeId) {
        const itemCol = item.collegeId;
        const matchesColId = itemCol && String(itemCol) === String(selectedCollegeId);
        const selectedColObj = colleges.find((c) => String(c.id) === String(selectedCollegeId));
        const matchesColName =
          selectedColObj &&
          item.institution &&
          item.institution.toLowerCase().includes(selectedColObj.name.toLowerCase());

        if (!matchesColId && !matchesColName) {
          return false;
        }
      }

      if (searchTerm) {
        const q = searchTerm.toLowerCase();
        const matchName = item.fullName?.toLowerCase().includes(q);
        const matchEmail = item.email?.toLowerCase().includes(q);
        const matchInst = item.institution?.toLowerCase().includes(q);
        const matchDept = item.department?.toLowerCase().includes(q);
        if (!matchName && !matchEmail && !matchInst && !matchDept) {
          return false;
        }
      }

      return true;
    });
  }

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  const displayedList = filterList(activeTab === "pending" ? pendingRequests : historyRequests);

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">ACCESS CONTROL</p>
          <h1 className="admin-title">Role Access Requests &amp; History</h1>
          <p className="admin-subtitle">
            Review onboarding submissions and manage access history organized by college.
          </p>
        </div>

        <button
          type="button"
          className="admin-btn-secondary"
          onClick={loadRequests}
          disabled={loading || processingId !== null}
        >
          ↻ Refresh Requests
        </button>
      </div>

      {/* Tabs */}
      <div className="admin-tabs-row">
        <button
          type="button"
          className={`admin-tab-btn ${activeTab === "pending" ? "active" : ""}`}
          onClick={() => setActiveTab("pending")}
        >
          Pending Requests{" "}
          <span className="admin-tab-count">{pendingRequests.length}</span>
        </button>
        <button
          type="button"
          className={`admin-tab-btn ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          History by College{" "}
          <span className="admin-tab-count">{historyRequests.length}</span>
        </button>
      </div>

      {/* Filter bar */}
      <div className="admin-filter-bar">
        <div className="admin-filter-group">
          <label htmlFor="reqCollegeSelect" className="admin-filter-label">
            Filter by College:
          </label>
          <select
            id="reqCollegeSelect"
            className="admin-select admin-college-select"
            value={selectedCollegeId}
            onChange={(e) => setSelectedCollegeId(e.target.value)}
          >
            <option value="">All Colleges ({colleges.length})</option>
            {colleges.map((col) => (
              <option key={col.id} value={col.id}>
                {col.name} ({col.code || "COL"})
              </option>
            ))}
          </select>
        </div>

        <div className="admin-filter-group admin-search-group">
          <input
            type="text"
            className="admin-input"
            placeholder="Search by name, email, department, college..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="admin-clear-btn"
              onClick={() => setSearchTerm("")}
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Indicator */}
      <div className="admin-active-filter-badge">
        Showing {activeTab === "pending" ? "pending requests" : "history records"} for:{" "}
        <strong>{currentCollegeName}</strong> ({displayedList.length} items)
      </div>

      {/* Content */}
      {loading ? (
        <div className="admin-loading-state">Loading access requests...</div>
      ) : displayedList.length === 0 ? (
        <div className="admin-empty-card">
          <h3>
            {activeTab === "pending"
              ? "No pending requests found"
              : "No historical records found"}
          </h3>
          <p>
            {selectedCollegeId
              ? `There are no ${activeTab} records for ${currentCollegeName}.`
              : "No access requests match your criteria."}
          </p>
        </div>
      ) : (
        <div className="admin-cards-grid">
          {displayedList.map((req) => {
            const roleColor = ROLE_COLORS[req.requestedRole] || "#4b5563";
            const roleTitle = ROLE_LABELS[req.requestedRole] || req.requestedRole;

            return (
              <article key={req.id} className="admin-request-card">
                <div className="admin-card-top">
                  <div className="admin-applicant-meta">
                    <div className="admin-avatar-circle">
                      {req.fullName?.charAt(0)?.toUpperCase() || "?"}
                    </div>
                    <div>
                      <h3 className="admin-applicant-name">{req.fullName}</h3>
                      <p className="admin-applicant-email">{req.email}</p>
                    </div>
                  </div>

                  <span
                    className="admin-role-pill"
                    style={{ backgroundColor: roleColor }}
                  >
                    {roleTitle}
                  </span>
                </div>

                <div className="admin-card-fields">
                  <div className="admin-field-row">
                    <span className="admin-field-label">College / Institution:</span>
                    <span className="admin-field-val">
                      {req.institution || "Unspecified"}
                    </span>
                  </div>

                  {req.department && (
                    <div className="admin-field-row">
                      <span className="admin-field-label">Department:</span>
                      <span className="admin-field-val">{req.department}</span>
                    </div>
                  )}

                  {req.reason && (
                    <div className="admin-field-row admin-reason-block">
                      <span className="admin-field-label">Purpose / Reason:</span>
                      <p className="admin-reason-text">{req.reason}</p>
                    </div>
                  )}

                  <div className="admin-field-row">
                    <span className="admin-field-label">Submitted:</span>
                    <span className="admin-field-val">
                      {req.createdAt ? new Date(req.createdAt).toLocaleString() : "—"}
                    </span>
                  </div>

                  {/* History details */}
                  {req.status === "APPROVED" && (
                    <div className="admin-history-badge-row approved">
                      <span>✓ APPROVED</span>
                      {req.reviewedAt && (
                        <small>
                          on {new Date(req.reviewedAt).toLocaleDateString()}
                        </small>
                      )}
                    </div>
                  )}

                  {req.status === "REJECTED" && (
                    <div className="admin-history-badge-row rejected">
                      <span>✕ REJECTED</span>
                      {req.rejectionReason && (
                        <p className="admin-rejection-msg">
                          Reason: {req.rejectionReason}
                        </p>
                      )}
                    </div>
                  )}
                </div>

                {/* Actions for Pending Requests */}
                {req.status === "PENDING" && (
                  <div className="admin-card-actions">
                    <button
                      type="button"
                      className="admin-btn-primary"
                      disabled={processingId !== null}
                      onClick={() => handleApprove(req.id)}
                    >
                      {processingId === req.id ? "Approving..." : "Approve & Grant Role"}
                    </button>
                    <button
                      type="button"
                      className="admin-btn-danger"
                      disabled={processingId !== null}
                      onClick={() => {
                        setRejectModalItem(req);
                        setRejectReason("");
                      }}
                    >
                      Reject
                    </button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {/* Reject Dialog */}
      {rejectModalItem && (
        <div
          className="admin-modal-backdrop"
          onClick={() => setRejectModalItem(null)}
        >
          <div
            className="admin-modal-content"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="admin-modal-header">
              <h2>Reject Access Request</h2>
              <button
                type="button"
                className="admin-modal-close"
                onClick={() => setRejectModalItem(null)}
              >
                ✕
              </button>
            </div>

            <p style={{ fontSize: "0.95rem", color: "#4b5563", marginBottom: "1rem" }}>
              Rejecting request for <strong>{rejectModalItem.fullName}</strong> (
              {rejectModalItem.email}) requesting{" "}
              <strong>{ROLE_LABELS[rejectModalItem.requestedRole] || rejectModalItem.requestedRole}</strong>.
            </p>

            <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: 600 }}>
              Rejection Reason <span style={{ color: "#ef4444" }}>*</span>
            </label>
            <textarea
              className="admin-input"
              rows={3}
              placeholder="Provide a clear reason for the applicant (e.g. Verification failed, invalid institution credentials)"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              style={{ width: "100%", resize: "vertical" }}
            />

            <div className="admin-modal-actions" style={{ marginTop: "1.25rem" }}>
              <button
                type="button"
                className="admin-btn-secondary"
                onClick={() => setRejectModalItem(null)}
                disabled={processingId !== null}
              >
                Cancel
              </button>
              <button
                type="button"
                className="admin-btn-danger"
                disabled={processingId !== null || rejectReason.trim().length < 4}
                onClick={handleConfirmReject}
              >
                {processingId !== null ? "Rejecting..." : "Confirm Rejection"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

