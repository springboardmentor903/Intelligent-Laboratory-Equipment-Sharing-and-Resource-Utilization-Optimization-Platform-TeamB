import { useAuth } from "../../context/AuthContext";
import AccessRequestsReviewSection from "./access-requests/AccessRequestsReviewSection";

export default function AccessRequestReviewsPage() {
  const { user } = useAuth();
  const isSystemAdmin = user?.role === "SYSTEM_ADMINISTRATOR";
  const isInstitutionAdmin = user?.role === "INSTITUTION_ADMINISTRATOR";

  return (
    <div style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      {/* Header Cluster */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          marginBottom: "24px",
          flexWrap: "wrap",
          gap: "16px",
        }}
      >
        <div>
          <div
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "4px 10px",
              background: "#eff6ff",
              color: "#2563eb",
              borderRadius: "20px",
              fontSize: "12px",
              fontWeight: 600,
              marginBottom: "8px",
            }}
          >
            <span>📬</span>
            <span>Inter-Institution Access Management</span>
          </div>
          <h1
            style={{
              fontSize: "26px",
              fontWeight: 800,
              color: "#0f172a",
              margin: "0 0 6px 0",
              letterSpacing: "-0.5px",
            }}
          >
            Access Request Reviews
          </h1>
          <p style={{ margin: 0, color: "#64748b", fontSize: "14px" }}>
            Multi-tier administrative review workflow for cross-institutional equipment sharing.
            Endorse outgoing requests from your institution or grant bilateral agreements for incoming requests.
          </p>
        </div>
      </div>

      {/* Review Section */}
      <AccessRequestsReviewSection
        canManageListings={isSystemAdmin || isInstitutionAdmin}
        userRole={user?.role}
      />
    </div>
  );
}

