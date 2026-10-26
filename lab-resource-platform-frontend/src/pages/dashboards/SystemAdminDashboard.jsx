
import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

function SystemAdminDashboard() {
    const toast = useToast();

    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [processingId, setProcessingId] = useState(null);
    const [loadError, setLoadError] = useState("");

    const loadRequests = useCallback(async () => {
        setLoading(true);
        setLoadError("");

        try {
            const response = await api.get(
                "/api/admin/technician-requests/pending"
            );
            setRequests(response.data);
        } catch (error) {
            setLoadError(
                error.response?.status === 403
                    ? "You need System Administrator access to view these requests."
                    : "Could not load technician requests. Please try again."
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadRequests();
    }, [loadRequests]);

    async function handleReview(requestId, action) {
        const actionName = action === "approve" ? "approve" : "reject";

        setProcessingId(requestId);

        try {
            await api.post(
                `/api/admin/technician-requests/${requestId}/${actionName}`
            );

            setRequests((current) =>
                current.filter((request) => request.id !== requestId)
            );

            toast.success(
                action === "approve"
                    ? "Technician request approved."
                    : "Technician request rejected."
            );
        } catch (error) {
            toast.error(
                error.response?.data?.detail ||
                error.response?.data?.message ||
                `Could not ${actionName} this request. Please try again.`
            );
        } finally {
            setProcessingId(null);
        }
    }

    return (
        <div className="system-admin-dashboard">
            <header className="admin-welcome">
                <p className="admin-eyebrow">PLATFORM ADMINISTRATION</p>
                <h1>System Administrator Dashboard</h1>
                <p>
                    Manage platform operations, institutions, users, and technician
                    access requests.
                </p>
            </header>

            <section className="dashboard-cards">
                <article className="task-card">
                    <h3>Platform Overview</h3>
                    <p>Review platform-level information.</p>
                </article>

                <article className="task-card">
                    <h3>Institutions</h3>
                    <p>Manage institutions on the platform.</p>
                </article>

                <article className="task-card">
                    <h3>User Management</h3>
                    <p>Manage platform accounts and access.</p>
                </article>
            </section>

            <section className="technician-requests-panel">
                <div className="requests-heading">
                    <div>
                        <h2>Technician Access Requests</h2>
                        <p>
                            Review requests before granting laboratory technician access.
                        </p>
                    </div>

                    <button
                        type="button"
                        className="requests-refresh-button"
                        onClick={loadRequests}
                        disabled={loading || processingId !== null}
                    >
                        Refresh
                    </button>
                </div>

                <div className="requests-count">
                    Pending requests: {requests.length}
                </div>

                {loading && (
                    <p className="requests-message">Loading requests...</p>
                )}

                {!loading && loadError && (
                    <div className="requests-error">
                        <p>{loadError}</p>
                        <button type="button" onClick={loadRequests}>
                            Try again
                        </button>
                    </div>
                )}

                {!loading && !loadError && requests.length === 0 && (
                    <div className="requests-empty">
                        <h3>No pending requests</h3>
                        <p>New technician access requests will appear here.</p>
                    </div>
                )}

                {!loading && !loadError && requests.length > 0 && (
                    <div className="technician-request-list">
                        {requests.map((request) => (
                            <article
                                className="technician-request-card"
                                key={request.id}
                            >
                                <div className="request-details">
                                    <div className="request-person">
                                        <div className="request-avatar" aria-hidden="true">
                                            {request.fullName?.charAt(0)?.toUpperCase() || "T"}
                                        </div>

                                        <div>
                                            <h3>{request.fullName}</h3>
                                            <p>{request.email}</p>
                                        </div>
                                    </div>

                                    {request.institution && (
                                        <p>
                                            <strong>Institution:</strong> {request.institution}
                                        </p>
                                    )}

                                    {request.reason && (
                                        <p className="request-reason">
                                            <strong>Reason:</strong> {request.reason}
                                        </p>
                                    )}

                                    <p className="request-date">
                                        Submitted:{" "}
                                        {request.createdAt
                                            ? new Date(request.createdAt).toLocaleString()
                                            : "Date unavailable"}
                                    </p>

                                    <span className="request-pending-label">PENDING</span>
                                </div>

                                <div className="request-actions">
                                    <button
                                        type="button"
                                        className="request-approve-button"
                                        disabled={processingId !== null}
                                        onClick={() => handleReview(request.id, "approve")}
                                    >
                                        {processingId === request.id
                                            ? "Processing..."
                                            : "Approve"}
                                    </button>

                                    <button
                                        type="button"
                                        className="request-reject-button"
                                        disabled={processingId !== null}
                                        onClick={() => handleReview(request.id, "reject")}
                                    >
                                        Reject
                                    </button>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </div>
    );
}

export default SystemAdminDashboard;
