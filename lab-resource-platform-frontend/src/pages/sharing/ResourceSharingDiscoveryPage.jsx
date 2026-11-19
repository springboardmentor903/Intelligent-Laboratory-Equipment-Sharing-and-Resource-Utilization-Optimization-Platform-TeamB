import { useState, useEffect, useCallback, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import EquipmentDetailsModal from "./access-requests/EquipmentDetailsModal";
import AccessRequestModal from "./access-requests/AccessRequestModal";

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

export default function ResourceSharingDiscoveryPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const [listings, setListings] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [selectedInstitutionId, setSelectedInstitutionId] = useState("");
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [agreementStatusFilter, setAgreementStatusFilter] = useState("ALL"); // ALL | READY_TO_BOOK | PENDING | REQUEST_REQUIRED
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // Modals state
  const [detailsListing, setDetailsListing] = useState(null);
  const [requestListing, setRequestListing] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search partner equipment by name, category, specification...");
  }, [setSearchPlaceholder]);

  // Load Colleges & Categories for filter dropdowns
  useEffect(() => {
    api
      .get("/api/colleges")
      .then((res) => setColleges(res.data || []))
      .catch((err) => console.error("Could not fetch colleges:", err));

    api
      .get("/api/equipment-categories")
      .then((res) => setCategories(res.data || []))
      .catch((err) => console.error("Could not fetch categories:", err));
  }, []);

  // Fetch Discovery Listings
  const fetchDiscoveryListings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await api.get("/api/sharing/discovery", {
        params: {
          page,
          size: 9,
          institutionId: selectedInstitutionId || undefined,
          categoryId: selectedCategoryId || undefined,
          keyword: searchTerm || undefined,
        },
      });

      if (resp.data?.content) {
        setListings(resp.data.content);
        setTotalPages(resp.data.totalPages || 1);
      } else if (Array.isArray(resp.data)) {
        setListings(resp.data);
        setTotalPages(1);
      } else {
        setListings([]);
        setTotalPages(1);
      }
    } catch (err) {
      console.error("Failed to discover shared resources:", err);
      setError(err.response?.data?.message || "Failed to load shared laboratory equipment catalog.");
    } finally {
      setLoading(false);
    }
  }, [page, selectedInstitutionId, selectedCategoryId, searchTerm]);

  useEffect(() => {
    fetchDiscoveryListings();
  }, [fetchDiscoveryListings]);

  // Client-side Agreement Status Filtering
  const filteredListings = useMemo(() => {
    if (agreementStatusFilter === "ALL") return listings;

    return listings.filter((item) => {
      const hasAgreement = Boolean(item.userStatus?.hasActiveAgreement);
      const isPending =
        item.userStatus?.latestRequestStatus === "PENDING" ||
        item.userStatus?.latestRequestStatus === "FORWARDED";

      if (agreementStatusFilter === "READY_TO_BOOK") return hasAgreement;
      if (agreementStatusFilter === "PENDING") return isPending;
      if (agreementStatusFilter === "REQUEST_REQUIRED") return !hasAgreement && !isPending;
      return true;
    });
  }, [listings, agreementStatusFilter]);

  const handleBookEquipment = (item) => {
    navigate(`/dashboard/sharing/external-booking?equipmentId=${item.equipmentId}`);
  };

  const handleRequestAccess = (item) => {
    setRequestListing(item);
  };

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
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "4px" }}>
            <span style={{ fontSize: "28px" }}>🌐</span>
            <h1 style={{ fontSize: "24px", fontWeight: 700, color: "#0f172a", margin: 0 }}>
              Resource Sharing Discovery Catalog
            </h1>
          </div>
          <p style={{ color: "#64748b", margin: 0, fontSize: "14px" }}>
            Discover and access specialized laboratory equipment, research instruments, and facilities shared across partner institutions.
          </p>
        </div>

        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
          <Link
            to="/dashboard/sharing/my-requests"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 16px",
              borderRadius: "8px",
              background: "#fff",
              border: "1px solid #cbd5e1",
              color: "#334155",
              textDecoration: "none",
              fontWeight: 600,
              fontSize: "14px",
            }}
          >
            <span>📑</span>
            <span>My Access Requests</span>
          </Link>

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
            <span>📅</span>
            <span>External Booking Hub</span>
          </Link>
        </div>
      </div>

      {/* Filter and Control Bar */}
      <div
        style={{
          background: "#fff",
          borderRadius: "12px",
          padding: "18px 20px",
          border: "1px solid #e2e8f0",
          boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
          marginBottom: "24px",
        }}
      >
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "14px" }}>
          {/* Institution Filter */}
          <div>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#475569", marginBottom: "6px" }}>
              OWNER INSTITUTION
            </label>
            <select
              value={selectedInstitutionId}
              onChange={(e) => {
                setSelectedInstitutionId(e.target.value);
                setPage(0);
              }}
              style={{
                width: "100%",
                padding: "8px 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "13px",
                color: "#1e293b",
                background: "#fff",
              }}
            >
              <option value="">All Partner Institutions</option>
              {colleges.map((col) => (
                <option key={col.id} value={col.id}>
                  {col.name}
                </option>
              ))}
            </select>
          </div>

          {/* Category Filter */}
          <div>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#475569", marginBottom: "6px" }}>
              EQUIPMENT CATEGORY
            </label>
            <select
              value={selectedCategoryId}
              onChange={(e) => {
                setSelectedCategoryId(e.target.value);
                setPage(0);
              }}
              style={{
                width: "100%",
                padding: "8px 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "13px",
                color: "#1e293b",
                background: "#fff",
              }}
            >
              <option value="">All Equipment Categories</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          </div>

          {/* Agreement Status Filter */}
          <div>
            <label style={{ display: "block", fontSize: "12px", fontWeight: 600, color: "#475569", marginBottom: "6px" }}>
              MY ACCESS ELIGIBILITY
            </label>
            <select
              value={agreementStatusFilter}
              onChange={(e) => setAgreementStatusFilter(e.target.value)}
              style={{
                width: "100%",
                padding: "8px 12px",
                borderRadius: "8px",
                border: "1px solid #cbd5e1",
                fontSize: "13px",
                color: "#1e293b",
                background: "#fff",
              }}
            >
              <option value="ALL">Show All Shared Resources</option>
              <option value="READY_TO_BOOK">Ready to Book (Active Agreement ✓)</option>
              <option value="PENDING">Access Request Pending / In Review ⏳</option>
              <option value="REQUEST_REQUIRED">Access Request Required 📝</option>
            </select>
          </div>
        </div>
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
            marginBottom: "20px",
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {/* Grid of Shared Instruments */}
      {loading ? (
        <div style={{ padding: "64px", textAlign: "center", color: "#64748b" }}>
          <div className="spinner" style={{ margin: "0 auto 12px auto" }} />
          Discovering shared laboratory equipment across institutions...
        </div>
      ) : filteredListings.length === 0 ? (
        <div
          style={{
            background: "#fff",
            borderRadius: "12px",
            padding: "64px 24px",
            textAlign: "center",
            border: "1px solid #e2e8f0",
          }}
        >
          <span style={{ fontSize: "48px", display: "block", marginBottom: "12px" }}>🔍</span>
          <h3 style={{ fontSize: "18px", fontWeight: 700, color: "#1e293b", margin: "0 0 8px 0" }}>
            No matching shared equipment found
          </h3>
          <p style={{ color: "#64748b", fontSize: "14px", margin: "0 0 16px 0" }}>
            {searchTerm || selectedInstitutionId || selectedCategoryId || agreementStatusFilter !== "ALL"
              ? "Try adjusting your filters or search keywords."
              : "No inter-institutional equipment listings have been published yet."}
          </p>
          {(selectedInstitutionId || selectedCategoryId || agreementStatusFilter !== "ALL") && (
            <button
              type="button"
              onClick={() => {
                setSelectedInstitutionId("");
                setSelectedCategoryId("");
                setAgreementStatusFilter("ALL");
              }}
              style={{
                padding: "8px 16px",
                borderRadius: "6px",
                background: "#f1f5f9",
                border: "1px solid #cbd5e1",
                fontSize: "13px",
                fontWeight: 600,
                cursor: "pointer",
                color: "#334155",
              }}
            >
              Reset Filters
            </button>
          )}
        </div>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(360px, 1fr))",
            gap: "20px",
            marginBottom: "24px",
          }}
        >
          {filteredListings.map((item) => {
            const userStatus = item.userStatus;
            const hasAgreement = Boolean(userStatus?.hasActiveAgreement);
            const isPending =
              userStatus?.latestRequestStatus === "PENDING" ||
              userStatus?.latestRequestStatus === "FORWARDED";
            const isRejected = userStatus?.latestRequestStatus === "REJECTED";

            return (
              <div
                key={item.listingId || item.equipmentId}
                style={{
                  background: "#fff",
                  borderRadius: "12px",
                  border: "1px solid #e2e8f0",
                  boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
                  display: "flex",
                  flexDirection: "column",
                  justifyContent: "space-between",
                  overflow: "hidden",
                  transition: "transform 0.15s, box-shadow 0.15s",
                }}
              >
                <div style={{ padding: "20px" }}>
                  {/* Top Tags */}
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
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
                      {item.categoryName || "Equipment"}
                    </span>

                    <span style={{ fontSize: "12px", color: "#64748b", fontFamily: "monospace" }}>
                      Asset #{item.assetTag || item.equipmentId}
                    </span>
                  </div>

                  {/* Title & Owner */}
                  <h3 style={{ margin: "0 0 4px 0", fontSize: "17px", fontWeight: 700, color: "#0f172a" }}>
                    {item.equipmentName}
                  </h3>
                  <div style={{ fontSize: "13px", color: "#475569", marginBottom: "12px" }}>
                    🏛️ {item.ownerInstitutionName}
                  </div>

                  {/* Short Description */}
                  {item.description && (
                    <p
                      style={{
                        margin: "0 0 14px 0",
                        fontSize: "13px",
                        color: "#64748b",
                        lineHeight: 1.5,
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        overflow: "hidden",
                      }}
                    >
                      {item.description}
                    </p>
                  )}

                  {/* Sharing Policy Parameters Pill Grid */}
                  <div
                    style={{
                      display: "grid",
                      gridTemplateColumns: "1fr 1fr",
                      gap: "8px",
                      background: "#f8fafc",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      border: "1px solid #f1f5f9",
                      fontSize: "12px",
                      marginBottom: "14px",
                    }}
                  >
                    <div>
                      <span style={{ color: "#64748b" }}>Tariff:</span>{" "}
                      <strong style={{ color: "#0f172a" }}>
                        {fmtCurrency(userStatus?.agreementFeePerHour || item.feePerHour)}/hr
                      </strong>
                    </div>

                    <div>
                      <span style={{ color: "#64748b" }}>Ext Share:</span>{" "}
                      <strong style={{ color: "#2563eb" }}>{item.externalSharePercentage}%</strong>
                    </div>

                    <div>
                      <span style={{ color: "#64748b" }}>Min Notice:</span>{" "}
                      <strong>{item.minNoticeHours}h</strong>
                    </div>

                    <div>
                      <span style={{ color: "#64748b" }}>Max Session:</span>{" "}
                      <strong>{item.maxBookingDurationHours}h</strong>
                    </div>
                  </div>

                  {/* Access Status Banner */}
                  <div>
                    {hasAgreement ? (
                      <div
                        style={{
                          padding: "6px 10px",
                          borderRadius: "6px",
                          background: "#dcfce7",
                          color: "#15803d",
                          fontSize: "12px",
                          fontWeight: 600,
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          width: "100%",
                        }}
                      >
                        <span>✓</span>
                        <span>
                          Active Agreement (Valid through {fmtDate(userStatus?.agreementEndDate)})
                        </span>
                      </div>
                    ) : isPending ? (
                      <div
                        style={{
                          padding: "6px 10px",
                          borderRadius: "6px",
                          background: "#fef3c7",
                          color: "#b45309",
                          fontSize: "12px",
                          fontWeight: 600,
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          width: "100%",
                        }}
                      >
                        <span>⏳</span>
                        <span>Request #{userStatus?.latestRequestId} ({userStatus?.latestRequestStatus})</span>
                      </div>
                    ) : isRejected ? (
                      <div
                        style={{
                          padding: "6px 10px",
                          borderRadius: "6px",
                          background: "#fee2e2",
                          color: "#b91c1c",
                          fontSize: "12px",
                          fontWeight: 600,
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          width: "100%",
                        }}
                      >
                        <span>✕</span>
                        <span>Prior Request Rejected (New request permitted)</span>
                      </div>
                    ) : (
                      <div
                        style={{
                          padding: "6px 10px",
                          borderRadius: "6px",
                          background: "#f1f5f9",
                          color: "#475569",
                          fontSize: "12px",
                          fontWeight: 500,
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          width: "100%",
                        }}
                      >
                        <span>ℹ️</span>
                        <span>Access Grant Required</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* Card Action Buttons Footer */}
                <div
                  style={{
                    padding: "12px 20px",
                    background: "#f8fafc",
                    borderTop: "1px solid #e2e8f0",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: "8px",
                  }}
                >
                  <button
                    type="button"
                    onClick={() => setDetailsListing(item)}
                    style={{
                      padding: "8px 12px",
                      borderRadius: "6px",
                      background: "#fff",
                      border: "1px solid #cbd5e1",
                      color: "#334155",
                      fontSize: "13px",
                      fontWeight: 600,
                      cursor: "pointer",
                    }}
                  >
                    Specs &amp; Details
                  </button>

                  {hasAgreement ? (
                    <button
                      type="button"
                      onClick={() => handleBookEquipment(item)}
                      style={{
                        padding: "8px 16px",
                        borderRadius: "6px",
                        background: "#16a34a",
                        color: "#fff",
                        border: "none",
                        fontSize: "13px",
                        fontWeight: 600,
                        cursor: "pointer",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "6px",
                        boxShadow: "0 2px 4px rgba(22,163,74,0.2)",
                      }}
                    >
                      <span>📅</span>
                      <span>Book Now</span>
                    </button>
                  ) : isPending ? (
                    <Link
                      to="/dashboard/sharing/my-requests"
                      style={{
                        padding: "8px 14px",
                        borderRadius: "6px",
                        background: "#eff6ff",
                        color: "#1d4ed8",
                        border: "1px solid #bfdbfe",
                        fontSize: "13px",
                        fontWeight: 600,
                        textDecoration: "none",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "6px",
                      }}
                    >
                      <span>📑</span>
                      <span>Track Status</span>
                    </Link>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleRequestAccess(item)}
                      style={{
                        padding: "8px 14px",
                        borderRadius: "6px",
                        background: "#2563eb",
                        color: "#fff",
                        border: "none",
                        fontSize: "13px",
                        fontWeight: 600,
                        cursor: "pointer",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "6px",
                        boxShadow: "0 2px 4px rgba(37,99,235,0.2)",
                      }}
                    >
                      <span>📝</span>
                      <span>Request Access</span>
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", gap: "12px", marginTop: "24px" }}>
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            style={{
              padding: "8px 16px",
              borderRadius: "6px",
              border: "1px solid #cbd5e1",
              background: page === 0 ? "#f1f5f9" : "#fff",
              color: page === 0 ? "#94a3b8" : "#334155",
              cursor: page === 0 ? "not-allowed" : "pointer",
              fontWeight: 600,
              fontSize: "13px",
            }}
          >
            ← Previous
          </button>

          <span style={{ fontSize: "14px", color: "#64748b", fontWeight: 500 }}>
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
          </span>

          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            style={{
              padding: "8px 16px",
              borderRadius: "6px",
              border: "1px solid #cbd5e1",
              background: page >= totalPages - 1 ? "#f1f5f9" : "#fff",
              color: page >= totalPages - 1 ? "#94a3b8" : "#334155",
              cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
              fontWeight: 600,
              fontSize: "13px",
            }}
          >
            Next →
          </button>
        </div>
      )}

      {/* Equipment Details Modal */}
      {detailsListing && (
        <EquipmentDetailsModal
          listing={detailsListing}
          onClose={() => setDetailsListing(null)}
          onBook={(item) => handleBookEquipment(item)}
          onRequestAccess={(item) => handleRequestAccess(item)}
        />
      )}

      {/* Access Request Submission Modal */}
      {requestListing && (
        <AccessRequestModal
          listing={requestListing}
          onClose={() => setRequestListing(null)}
          onSuccess={fetchDiscoveryListings}
        />
      )}
    </div>
  );
}

