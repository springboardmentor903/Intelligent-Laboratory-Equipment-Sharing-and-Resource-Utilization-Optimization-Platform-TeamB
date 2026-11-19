import { useState, useEffect, useCallback, useMemo } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import AccessRequestsReviewSection from "./access-requests/AccessRequestsReviewSection";

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

export default function SharedResourceManagementPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  // Active Main Tab: "listings" | "agreements" | "access-requests"
  const activeTab = searchParams.get("tab") || "listings";
  const setActiveTab = (tab) => {
    setSearchParams({ tab });
  };

  const isSystemAdmin = user?.role === "SYSTEM_ADMINISTRATOR";
  const isInstitutionAdmin = user?.role === "INSTITUTION_ADMINISTRATOR";
  const isDeptHead = user?.role === "DEPARTMENT_HEAD";
  const isLabManager = user?.role === "LAB_MANAGER";

  // Check if current user can publish or manage listings
  const canManageListings = isSystemAdmin || isInstitutionAdmin;

  useEffect(() => {
    if (activeTab === "listings") {
      setSearchPlaceholder("Search sharing listings by equipment or owner...");
    } else if (activeTab === "agreements") {
      setSearchPlaceholder("Search agreements by equipment or partner...");
    } else {
      setSearchPlaceholder("Search access requests by equipment, requester or institute...");
    }
  }, [activeTab, setSearchPlaceholder]);

  // ==========================================
  // TAB 1: SHARING LISTINGS STATE
  // ==========================================
  const [listings, setListings] = useState([]);
  const [listingsLoading, setListingsLoading] = useState(false);
  const [listingsError, setListingsError] = useState(null);
  const [listingPage, setListingPage] = useState(0);
  const [listingTotalPages, setListingTotalPages] = useState(1);

  // Modal State for Listing Create/Edit
  const [listingModalOpen, setListingModalOpen] = useState(false);
  const [editingListing, setEditingListing] = useState(null);
  const [ownedEquipments, setOwnedEquipments] = useState([]);
  const [ownedEquipmentsLoading, setOwnedEquipmentsLoading] = useState(false);
  const [listingSubmitting, setListingSubmitting] = useState(false);
  const [listingForm, setListingForm] = useState({
    equipmentId: "",
    feePerHour: "0",
    externalSharePercentage: "25",
    minNoticeHours: "24",
    maxBookingDurationHours: "4",
    sharingTerms: "",
    active: true,
  });

  // Action Loading for Listing Activate/Deactivate
  const [listingActionId, setListingActionId] = useState(null);

  // ==========================================
  // TAB 2: SHARING AGREEMENTS & GRANTS STATE
  // ==========================================
  const [agreements, setAgreements] = useState([]);
  const [agreementsLoading, setAgreementsLoading] = useState(false);
  const [agreementsError, setAgreementsError] = useState(null);
  const [agreementPage, setAgreementPage] = useState(0);
  const [agreementTotalPages, setAgreementTotalPages] = useState(1);

  // Grants Drawer / Inspection Modal
  const [grantsModalOpen, setGrantsModalOpen] = useState(false);
  const [selectedAgreementForGrants, setSelectedAgreementForGrants] = useState(null);
  const [grantsList, setGrantsList] = useState([]);
  const [grantsLoading, setGrantsLoading] = useState(false);
  const [grantActionId, setGrantActionId] = useState(null);

  // Revoke Modal (for Agreement or Grant)
  const [revokeModalOpen, setRevokeModalOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState(null); // { type: 'agreement'|'grant', id: 123, title: '...' }
  const [revokeReason, setRevokeReason] = useState("");
  const [revokeSubmitting, setRevokeSubmitting] = useState(false);

  // Usage Fees Modal
  const [usageFeesModalOpen, setUsageFeesModalOpen] = useState(false);
  const [usageFeesData, setUsageFeesData] = useState(null);
  const [usageFeesLoading, setUsageFeesLoading] = useState(false);



  // ==========================================
  // FETCH LISTINGS
  // ==========================================
  const fetchListings = useCallback(async () => {
    setListingsLoading(true);
    setListingsError(null);
    try {
      const resp = await api.get("/api/sharing/discovery", {
        params: {
          page: listingPage,
          size: 10,
          keyword: searchTerm || undefined,
        },
      });
      if (resp.data?.content) {
        setListings(resp.data.content);
        setListingTotalPages(resp.data.totalPages || 1);
      } else if (Array.isArray(resp.data)) {
        setListings(resp.data);
        setListingTotalPages(1);
      } else {
        setListings([]);
      }
    } catch (err) {
      console.error("Failed to fetch listings:", err);
      setListingsError(err.response?.data?.message || "Failed to load sharing listings.");
    } finally {
      setListingsLoading(false);
    }
  }, [listingPage, searchTerm]);

  // ==========================================
  // FETCH AGREEMENTS
  // ==========================================
  const fetchAgreements = useCallback(async () => {
    setAgreementsLoading(true);
    setAgreementsError(null);
    try {
      const resp = await api.get("/api/sharing/agreements", {
        params: {
          page: agreementPage,
          size: 10,
        },
      });
      if (resp.data?.content) {
        setAgreements(resp.data.content);
        setAgreementTotalPages(resp.data.totalPages || 1);
      } else if (Array.isArray(resp.data)) {
        setAgreements(resp.data);
        setAgreementTotalPages(1);
      } else {
        setAgreements([]);
      }
    } catch (err) {
      console.error("Failed to fetch agreements:", err);
      setAgreementsError(err.response?.data?.message || "Failed to load sharing agreements.");
    } finally {
      setAgreementsLoading(false);
    }
  }, [agreementPage]);

  // Trigger data load based on active tab
  useEffect(() => {
    if (activeTab === "listings") {
      fetchListings();
    } else if (activeTab === "agreements") {
      fetchAgreements();
    }
  }, [activeTab, fetchListings, fetchAgreements]);

  // Load owned equipments when opening create listing modal
  const loadOwnedEquipments = async () => {
    setOwnedEquipmentsLoading(true);
    try {
      const resp = await api.get("/api/equipment", {
        params: { page: 0, size: 100 },
      });
      const items = resp.data?.content || (Array.isArray(resp.data) ? resp.data : []);
      setOwnedEquipments(items);
    } catch (err) {
      console.error("Failed to load equipments for listing:", err);
      toast.error("Could not fetch institution equipment.");
    } finally {
      setOwnedEquipmentsLoading(false);
    }
  };

  // ==========================================
  // LISTING ACTIONS (Create, Edit, Toggle)
  // ==========================================
  const handleOpenCreateModal = () => {
    setEditingListing(null);
    setListingForm({
      equipmentId: "",
      feePerHour: "0",
      externalSharePercentage: "25",
      minNoticeHours: "24",
      maxBookingDurationHours: "4",
      sharingTerms: "1. Users must adhere to standard laboratory safety protocols.\n2. Calibration settings must not be altered.\n3. Late cancellations are subject to standard fees.",
      active: true,
    });
    setListingModalOpen(true);
    loadOwnedEquipments();
  };

  const handleOpenEditModal = (listing) => {
    setEditingListing(listing);
    setListingForm({
      equipmentId: listing.equipmentId,
      feePerHour: String(listing.feePerHour ?? 0),
      externalSharePercentage: String(listing.externalSharePercentage ?? 25),
      minNoticeHours: String(listing.minNoticeHours ?? 24),
      maxBookingDurationHours: String(listing.maxBookingDurationHours ?? 4),
      sharingTerms: listing.sharingTerms || "",
      active: listing.active ?? true,
    });
    setListingModalOpen(true);
  };

  const handleSaveListing = async (e) => {
    e.preventDefault();
    if (!listingForm.equipmentId && !editingListing) {
      toast.error("Please select an equipment to share.");
      return;
    }
    const fee = parseFloat(listingForm.feePerHour);
    if (isNaN(fee) || fee < 0) {
      toast.error("Fee per hour must be a non-negative number.");
      return;
    }
    const sharePct = parseFloat(listingForm.externalSharePercentage);
    if (isNaN(sharePct) || sharePct < 0 || sharePct > 100) {
      toast.error("External share percentage must be between 0% and 100%.");
      return;
    }
    const notice = parseInt(listingForm.minNoticeHours, 10);
    if (isNaN(notice) || notice < 0) {
      toast.error("Minimum notice hours cannot be negative.");
      return;
    }
    const maxDuration = parseInt(listingForm.maxBookingDurationHours, 10);
    if (isNaN(maxDuration) || maxDuration < 1) {
      toast.error("Maximum booking duration must be at least 1 hour.");
      return;
    }

    setListingSubmitting(true);
    try {
      const payload = {
        equipmentId: editingListing ? editingListing.equipmentId : Number(listingForm.equipmentId),
        feePerHour: fee,
        externalSharePercentage: sharePct,
        minNoticeHours: notice,
        maxBookingDurationHours: maxDuration,
        sharingTerms: listingForm.sharingTerms.trim(),
        active: Boolean(listingForm.active),
      };

      if (editingListing) {
        await api.put(`/api/sharing/listings/${editingListing.id}`, payload);
        toast.success("Sharing listing updated successfully!");
      } else {
        await api.post("/api/sharing/listings", payload);
        toast.success("Equipment sharing listing published successfully!");
      }
      setListingModalOpen(false);
      fetchListings();
    } catch (err) {
      console.error("Listing save failed:", err);
      toast.error(err.response?.data?.message || "Failed to save sharing listing.");
    } finally {
      setListingSubmitting(false);
    }
  };

  const handleToggleListingActive = async (listing) => {
    setListingActionId(listing.id);
    const willActivate = !listing.active;
    const actionPath = willActivate ? "activate" : "deactivate";
    try {
      await api.patch(`/api/sharing/listings/${listing.id}/${actionPath}`);
      toast.success(`Listing ${willActivate ? "activated" : "deactivated"} successfully!`);
      // Update local state smoothly
      setListings((prev) =>
        prev.map((item) =>
          item.id === listing.id ? { ...item, active: willActivate } : item
        )
      );
    } catch (err) {
      console.error("Failed to toggle listing active state:", err);
      toast.error(err.response?.data?.message || `Failed to ${actionPath} listing.`);
    } finally {
      setListingActionId(null);
    }
  };

  // ==========================================
  // AGREEMENT & GRANT ACTIONS
  // ==========================================
  const handleOpenGrants = async (agreement) => {
    setSelectedAgreementForGrants(agreement);
    setGrantsModalOpen(true);
    setGrantsLoading(true);
    try {
      const resp = await api.get(`/api/sharing/agreements/${agreement.id}/grants`);
      setGrantsList(Array.isArray(resp.data) ? resp.data : []);
    } catch (err) {
      console.error("Failed to load grants:", err);
      toast.error(err.response?.data?.message || "Failed to load authorized grants.");
    } finally {
      setGrantsLoading(false);
    }
  };

  const handleOpenUsageFees = async (agreement) => {
    setUsageFeesModalOpen(true);
    setUsageFeesLoading(true);
    setUsageFeesData(null);
    try {
      const resp = await api.get(`/api/sharing/agreements/${agreement.id}/usage-fees`);
      setUsageFeesData(resp.data);
    } catch (err) {
      console.error("Failed to fetch usage fees:", err);
      toast.error(err.response?.data?.message || "Failed to calculate usage fees.");
    } finally {
      setUsageFeesLoading(false);
    }
  };

  const handleOpenRevokeAgreement = (agreement) => {
    setRevokeTarget({
      type: "agreement",
      id: agreement.id,
      title: `Agreement #${agreement.id} (${agreement.equipmentName}) with ${agreement.partnerInstitutionName}`,
    });
    setRevokeReason("");
    setRevokeModalOpen(true);
  };

  const handleOpenRevokeGrant = (grant) => {
    setRevokeTarget({
      type: "grant",
      id: grant.id,
      title: `Access Grant for ${grant.userName} (${grant.userEmail})`,
    });
    setRevokeReason("");
    setRevokeModalOpen(true);
  };

  const handleSubmitRevocation = async (e) => {
    e.preventDefault();
    if (!revokeReason.trim()) {
      toast.error("Please provide a mandatory revocation reason.");
      return;
    }
    setRevokeSubmitting(true);
    try {
      const payload = { reason: revokeReason.trim() };
      if (revokeTarget.type === "agreement") {
        await api.post(`/api/sharing/agreements/${revokeTarget.id}/revoke`, payload);
        toast.success("Sharing agreement revoked. Associated future bookings released.");
        fetchAgreements();
      } else {
        await api.post(`/api/sharing/grants/${revokeTarget.id}/revoke`, payload);
        toast.success("User access grant revoked successfully.");
        // Refresh grants list if modal is open
        if (selectedAgreementForGrants) {
          const resp = await api.get(`/api/sharing/agreements/${selectedAgreementForGrants.id}/grants`);
          setGrantsList(Array.isArray(resp.data) ? resp.data : []);
        }
      }
      setRevokeModalOpen(false);
    } catch (err) {
      console.error("Revocation failed:", err);
      toast.error(err.response?.data?.message || "Failed to revoke.");
    } finally {
      setRevokeSubmitting(false);
    }
  };

  // Filter listings by search
  const filteredListings = useMemo(() => {
    if (!searchTerm) return listings;
    const q = searchTerm.toLowerCase();
    return listings.filter(
      (l) =>
        l.equipmentName?.toLowerCase().includes(q) ||
        l.assetTag?.toLowerCase().includes(q) ||
        l.ownerInstitutionName?.toLowerCase().includes(q)
    );
  }, [listings, searchTerm]);

  return (
    <div className="shared-mgmt-container" style={{ padding: "24px", maxWidth: "1400px", margin: "0 auto" }}>
      {/* Top Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px", flexWrap: "wrap", gap: "16px" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "4px" }}>
            <span style={{ fontSize: "28px" }}>🔄</span>
            <h1 style={{ fontSize: "24px", fontWeight: 700, color: "#0f172a", margin: 0 }}>
              Shared Resource Management
            </h1>
          </div>
          <p style={{ color: "#64748b", margin: 0, fontSize: "14px" }}>
            Administer published equipment listings, inter-institution sharing agreements, and access review workflows.
          </p>
        </div>

        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap" }}>
          <Link
            to="/dashboard/sharing/external-booking"
            className="btn btn-outline"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 16px",
              borderRadius: "8px",
              textDecoration: "none",
              border: "1px solid #cbd5e1",
              color: "#334155",
              fontWeight: 600,
              fontSize: "14px",
              background: "#fff",
            }}
          >
            <span>🌐</span>
            <span>External Booking Catalog</span>
          </Link>

          {canManageListings && (
            <button
              type="button"
              onClick={handleOpenCreateModal}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                padding: "10px 18px",
                borderRadius: "8px",
                background: "#2563eb",
                color: "#fff",
                border: "none",
                fontWeight: 600,
                fontSize: "14px",
                cursor: "pointer",
                boxShadow: "0 2px 4px rgba(37,99,235,0.2)",
              }}
            >
              <span style={{ fontSize: "16px", fontWeight: "bold" }}>+</span>
              <span>Publish Equipment Listing</span>
            </button>
          )}
        </div>
      </div>

      {/* Main Tab Navigation */}
      <div style={{ display: "flex", borderBottom: "2px solid #e2e8f0", marginBottom: "24px", gap: "8px" }}>
        <button
          type="button"
          onClick={() => setActiveTab("listings")}
          style={{
            padding: "12px 20px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "listings" ? "3px solid #2563eb" : "3px solid transparent",
            color: activeTab === "listings" ? "#2563eb" : "#64748b",
            fontWeight: activeTab === "listings" ? 700 : 500,
            fontSize: "15px",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            transition: "all 0.2s",
          }}
        >
          <span>📋</span>
          <span>Equipment Listings</span>
          <span
            style={{
              padding: "2px 8px",
              fontSize: "12px",
              borderRadius: "12px",
              background: activeTab === "listings" ? "#eff6ff" : "#f1f5f9",
              color: activeTab === "listings" ? "#2563eb" : "#64748b",
              fontWeight: 600,
            }}
          >
            {listings.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab("agreements")}
          style={{
            padding: "12px 20px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "agreements" ? "3px solid #2563eb" : "3px solid transparent",
            color: activeTab === "agreements" ? "#2563eb" : "#64748b",
            fontWeight: activeTab === "agreements" ? 700 : 500,
            fontSize: "15px",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            transition: "all 0.2s",
          }}
        >
          <span>🤝</span>
          <span>Agreements & Grants</span>
          <span
            style={{
              padding: "2px 8px",
              fontSize: "12px",
              borderRadius: "12px",
              background: activeTab === "agreements" ? "#eff6ff" : "#f1f5f9",
              color: activeTab === "agreements" ? "#2563eb" : "#64748b",
              fontWeight: 600,
            }}
          >
            {agreements.length}
          </span>
        </button>

        <button
          type="button"
          onClick={() => setActiveTab("access-requests")}
          style={{
            padding: "12px 20px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "access-requests" ? "3px solid #2563eb" : "3px solid transparent",
            color: activeTab === "access-requests" ? "#2563eb" : "#64748b",
            fontWeight: activeTab === "access-requests" ? 700 : 500,
            fontSize: "15px",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            transition: "all 0.2s",
          }}
        >
          <span>📬</span>
          <span>Access Requests Review</span>
          <span
            style={{
              padding: "2px 8px",
              fontSize: "12px",
              borderRadius: "12px",
              background: activeTab === "access-requests" ? "#eff6ff" : "#f1f5f9",
              color: activeTab === "access-requests" ? "#2563eb" : "#64748b",
              fontWeight: 600,
            }}
          >
            T11
          </span>
        </button>
      </div>

      {/* ============================================================== */}
      {/* TAB 1: EQUIPMENT SHARING LISTINGS                             */}
      {/* ============================================================== */}
      {activeTab === "listings" && (
        <section>
          {listingsError && (
            <div style={{ padding: "16px", background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", color: "#b91c1c", marginBottom: "16px" }}>
              ⚠️ {listingsError}
            </div>
          )}

          <div style={{ background: "#fff", borderRadius: "12px", border: "1px solid #e2e8f0", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <div style={{ padding: "16px 20px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center", background: "#f8fafc" }}>
              <div>
                <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#1e293b" }}>
                  Active & Configured Sharing Listings
                </h3>
                <span style={{ fontSize: "13px", color: "#64748b" }}>
                  Showing {filteredListings.length} equipment available or listed for inter-institution sharing
                </span>
              </div>
              <button
                type="button"
                onClick={fetchListings}
                style={{ background: "#fff", border: "1px solid #cbd5e1", borderRadius: "6px", padding: "6px 12px", fontSize: "13px", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: "6px" }}
              >
                <span>🔄</span> Refresh
              </button>
            </div>

            {listingsLoading ? (
              <div style={{ padding: "48px", textAlign: "center", color: "#64748b" }}>
                <div className="spinner" style={{ margin: "0 auto 12px auto" }} />
                Loading sharing listings...
              </div>
            ) : filteredListings.length === 0 ? (
              <div style={{ padding: "48px 24px", textAlign: "center", color: "#64748b" }}>
                <p style={{ fontSize: "16px", fontWeight: 600, margin: "0 0 8px 0", color: "#334155" }}>
                  No sharing listings found
                </p>
                <p style={{ margin: 0, fontSize: "14px" }}>
                  {searchTerm ? "No listings matched your search criteria." : "No equipment has been published for inter-institution sharing yet."}
                </p>
                {canManageListings && !searchTerm && (
                  <button
                    type="button"
                    onClick={handleOpenCreateModal}
                    style={{ marginTop: "16px", padding: "8px 16px", background: "#2563eb", color: "#fff", border: "none", borderRadius: "6px", fontWeight: 600, cursor: "pointer" }}
                  >
                    + Publish Your First Listing
                  </button>
                )}
              </div>
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
                  <thead>
                    <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569", fontWeight: 600 }}>
                      <th style={{ padding: "12px 16px" }}>Equipment</th>
                      <th style={{ padding: "12px 16px" }}>Owner Institution</th>
                      <th style={{ padding: "12px 16px" }}>Rate</th>
                      <th style={{ padding: "12px 16px" }}>External Share</th>
                      <th style={{ padding: "12px 16px" }}>Min Notice</th>
                      <th style={{ padding: "12px 16px" }}>Max Duration</th>
                      <th style={{ padding: "12px 16px" }}>Status</th>
                      <th style={{ padding: "12px 16px", textAlign: "right" }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredListings.map((l) => (
                      <tr key={l.id} style={{ borderBottom: "1px solid #f1f5f9", transition: "background 0.15s" }}>
                        <td style={{ padding: "14px 16px" }}>
                          <div style={{ fontWeight: 600, color: "#0f172a" }}>{l.equipmentName}</div>
                          <div style={{ fontSize: "12px", color: "#64748b", fontFamily: "monospace" }}>
                            Asset #{l.assetTag || l.equipmentId}
                          </div>
                        </td>
                        <td style={{ padding: "14px 16px", color: "#334155" }}>
                          <div style={{ fontWeight: 500 }}>{l.ownerInstitutionName || "My Institution"}</div>
                        </td>
                        <td style={{ padding: "14px 16px", fontWeight: 600, color: "#0f172a" }}>
                          {fmtCurrency(l.feePerHour)}<span style={{ fontSize: "12px", color: "#64748b", fontWeight: 400 }}> / hr</span>
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <div style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                            <div style={{ width: "60px", background: "#e2e8f0", height: "8px", borderRadius: "4px", overflow: "hidden" }}>
                              <div style={{ width: `${Math.min(100, l.externalSharePercentage || 0)}%`, background: "#2563eb", height: "100%" }} />
                            </div>
                            <span style={{ fontSize: "13px", fontWeight: 600, color: "#1e293b" }}>
                              {l.externalSharePercentage}%
                            </span>
                          </div>
                        </td>
                        <td style={{ padding: "14px 16px", color: "#475569" }}>
                          {l.minNoticeHours} hrs
                        </td>
                        <td style={{ padding: "14px 16px", color: "#475569" }}>
                          {l.maxBookingDurationHours} hrs
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <span
                            style={{
                              padding: "4px 10px",
                              borderRadius: "12px",
                              fontSize: "12px",
                              fontWeight: 600,
                              background: l.active ? "#dcfce7" : "#fee2e2",
                              color: l.active ? "#15803d" : "#b91c1c",
                              display: "inline-flex",
                              alignItems: "center",
                              gap: "4px",
                            }}
                          >
                            <span style={{ width: "6px", height: "6px", borderRadius: "50%", background: l.active ? "#16a34a" : "#dc2626" }} />
                            {l.active ? "Active" : "Inactive"}
                          </span>
                        </td>
                        <td style={{ padding: "14px 16px", textAlign: "right" }}>
                          <div style={{ display: "inline-flex", gap: "8px", justifyContent: "flex-end" }}>
                            {canManageListings && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => handleOpenEditModal(l)}
                                  title="Edit Listing Parameters"
                                  style={{
                                    padding: "6px 10px",
                                    background: "#f1f5f9",
                                    border: "1px solid #cbd5e1",
                                    borderRadius: "6px",
                                    cursor: "pointer",
                                    fontSize: "13px",
                                    fontWeight: 500,
                                    color: "#334155",
                                  }}
                                >
                                  ✏️ Edit
                                </button>
                                <button
                                  type="button"
                                  disabled={listingActionId === l.id}
                                  onClick={() => handleToggleListingActive(l)}
                                  style={{
                                    padding: "6px 10px",
                                    background: l.active ? "#fff1f2" : "#ecfdf5",
                                    border: l.active ? "1px solid #fecdd3" : "1px solid #a7f3d0",
                                    borderRadius: "6px",
                                    cursor: "pointer",
                                    fontSize: "13px",
                                    fontWeight: 600,
                                    color: l.active ? "#be123c" : "#047857",
                                  }}
                                >
                                  {listingActionId === l.id ? "..." : l.active ? "Deactivate" : "Activate"}
                                </button>
                              </>
                            )}
                            <Link
                              to={`/dashboard/sharing/external-booking?equipmentId=${l.equipmentId}`}
                              title="View in External Catalog"
                              style={{
                                padding: "6px 10px",
                                background: "#eff6ff",
                                border: "1px solid #bfdbfe",
                                borderRadius: "6px",
                                textDecoration: "none",
                                fontSize: "13px",
                                fontWeight: 600,
                                color: "#1d4ed8",
                              }}
                            >
                              🔍 View
                            </Link>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      )}

      {/* ============================================================== */}
      {/* TAB 2: INTER-INSTITUTION SHARING AGREEMENTS & GRANTS          */}
      {/* ============================================================== */}
      {activeTab === "agreements" && (
        <section>
          {agreementsError && (
            <div style={{ padding: "16px", background: "#fef2f2", border: "1px solid #fecaca", borderRadius: "8px", color: "#b91c1c", marginBottom: "16px" }}>
              ⚠️ {agreementsError}
            </div>
          )}

          <div style={{ background: "#fff", borderRadius: "12px", border: "1px solid #e2e8f0", overflow: "hidden", boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <div style={{ padding: "16px 20px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center", background: "#f8fafc" }}>
              <div>
                <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#1e293b" }}>
                  Bilateral Sharing Agreements & Authorization Grants
                </h3>
                <span style={{ fontSize: "13px", color: "#64748b" }}>
                  Active contracts governing inter-institutional equipment access, operating windows, and tariff rates
                </span>
              </div>
              <button
                type="button"
                onClick={fetchAgreements}
                style={{ background: "#fff", border: "1px solid #cbd5e1", borderRadius: "6px", padding: "6px 12px", fontSize: "13px", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: "6px" }}
              >
                <span>🔄</span> Refresh
              </button>
            </div>

            {agreementsLoading ? (
              <div style={{ padding: "48px", textAlign: "center", color: "#64748b" }}>
                <div className="spinner" style={{ margin: "0 auto 12px auto" }} />
                Loading agreements...
              </div>
            ) : agreements.length === 0 ? (
              <div style={{ padding: "48px 24px", textAlign: "center", color: "#64748b" }}>
                <p style={{ fontSize: "16px", fontWeight: 600, margin: "0 0 8px 0", color: "#334155" }}>
                  No sharing agreements found
                </p>
                <p style={{ margin: 0, fontSize: "14px" }}>
                  Sharing agreements are automatically established when an equipment access request is approved.
                </p>
              </div>
            ) : (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
                  <thead>
                    <tr style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0", color: "#475569", fontWeight: 600 }}>
                      <th style={{ padding: "12px 16px" }}>Agreement</th>
                      <th style={{ padding: "12px 16px" }}>Equipment</th>
                      <th style={{ padding: "12px 16px" }}>Partner Institution</th>
                      <th style={{ padding: "12px 16px" }}>Validity Period</th>
                      <th style={{ padding: "12px 16px" }}>Rate</th>
                      <th style={{ padding: "12px 16px" }}>Status</th>
                      <th style={{ padding: "12px 16px", textAlign: "right" }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {agreements.map((a) => (
                      <tr key={a.id} style={{ borderBottom: "1px solid #f1f5f9" }}>
                        <td style={{ padding: "14px 16px" }}>
                          <span style={{ fontWeight: 600, color: "#0f172a" }}>#{a.id}</span>
                          {a.accessRequestId && (
                            <div style={{ fontSize: "12px", color: "#64748b" }}>
                              Req #{a.accessRequestId}
                            </div>
                          )}
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <div style={{ fontWeight: 600, color: "#0f172a" }}>{a.equipmentName}</div>
                          <div style={{ fontSize: "12px", color: "#64748b" }}>Asset #{a.assetTag}</div>
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <div style={{ fontWeight: 500, color: "#1e293b" }}>{a.partnerInstitutionName}</div>
                          <div style={{ fontSize: "12px", color: "#64748b" }}>Owner: {a.ownerInstitutionName}</div>
                        </td>
                        <td style={{ padding: "14px 16px", color: "#475569" }}>
                          <div>{fmtDate(a.startDate)} – {fmtDate(a.endDate)}</div>
                          {a.allowedStartTime && a.allowedEndTime && (
                            <div style={{ fontSize: "12px", color: "#64748b" }}>
                              🕒 {a.allowedStartTime} – {a.allowedEndTime}
                            </div>
                          )}
                        </td>
                        <td style={{ padding: "14px 16px", fontWeight: 600, color: "#0f172a" }}>
                          {fmtCurrency(a.feePerHour)}<span style={{ fontSize: "12px", color: "#64748b", fontWeight: 400 }}> / hr</span>
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <div style={{ display: "inline-flex", flexDirection: "column", gap: "4px" }}>
                            <span
                              style={{
                                padding: "3px 8px",
                                borderRadius: "12px",
                                fontSize: "11px",
                                fontWeight: 700,
                                background: a.status === "ACTIVE" ? "#dcfce7" : "#fee2e2",
                                color: a.status === "ACTIVE" ? "#15803d" : "#b91c1c",
                              }}
                            >
                              {a.status}
                            </span>
                            {a.expired && (
                              <span style={{ padding: "2px 6px", borderRadius: "10px", fontSize: "10px", background: "#fef3c7", color: "#92400e", fontWeight: 600 }}>
                                EXPIRED
                              </span>
                            )}
                          </div>
                        </td>
                        <td style={{ padding: "14px 16px", textAlign: "right" }}>
                          <div style={{ display: "inline-flex", gap: "6px", justifyContent: "flex-end" }}>
                            <button
                              type="button"
                              onClick={() => handleOpenGrants(a)}
                              title="Inspect Authorized Researchers"
                              style={{
                                padding: "6px 10px",
                                background: "#eff6ff",
                                border: "1px solid #bfdbfe",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                                color: "#1d4ed8",
                              }}
                            >
                              👥 Grants ({a.grants?.length ?? "•"})
                            </button>

                            <button
                              type="button"
                              onClick={() => handleOpenUsageFees(a)}
                              title="Inspect Accrued Usage Fees"
                              style={{
                                padding: "6px 10px",
                                background: "#f0fdf4",
                                border: "1px solid #bbf7d0",
                                borderRadius: "6px",
                                cursor: "pointer",
                                fontSize: "12px",
                                fontWeight: 600,
                                color: "#15803d",
                              }}
                            >
                              💰 Fees
                            </button>

                            {a.status === "ACTIVE" && canManageListings && (
                              <button
                                type="button"
                                onClick={() => handleOpenRevokeAgreement(a)}
                                title="Revoke Agreement"
                                style={{
                                  padding: "6px 10px",
                                  background: "#fef2f2",
                                  border: "1px solid #fecaca",
                                  borderRadius: "6px",
                                  cursor: "pointer",
                                  fontSize: "12px",
                                  fontWeight: 600,
                                  color: "#b91c1c",
                                }}
                              >
                                🚫 Revoke
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      )}

      {/* ============================================================== */}
      {/* TAB 3: ACCESS REQUESTS REVIEW (T11 SEPARATED COMPONENT)        */}
      {/* ============================================================== */}
      {activeTab === "access-requests" && (
        <AccessRequestsReviewSection
          canManageListings={canManageListings}
          userRole={user?.role}
        />
      )}

      {/* ============================================================== */}
      {/* MODAL: CREATE / EDIT SHARING LISTING                           */}
      {/* ============================================================== */}
      {listingModalOpen && (
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
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                {editingListing ? "Edit Sharing Listing" : "Publish Equipment for Sharing"}
              </h2>
              <button
                type="button"
                onClick={() => setListingModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSaveListing} style={{ padding: "24px" }}>
              {/* Equipment Selection (only on Create) */}
              {!editingListing ? (
                <div style={{ marginBottom: "16px" }}>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                    Select Equipment *
                  </label>
                  {ownedEquipmentsLoading ? (
                    <div style={{ fontSize: "13px", color: "#64748b" }}>Loading equipment list...</div>
                  ) : (
                    <select
                      value={listingForm.equipmentId}
                      onChange={(e) => setListingForm({ ...listingForm, equipmentId: e.target.value })}
                      required
                      style={{
                        width: "100%",
                        padding: "10px 12px",
                        borderRadius: "8px",
                        border: "1px solid #cbd5e1",
                        fontSize: "14px",
                        background: "#fff",
                        color: "#0f172a",
                      }}
                    >
                      <option value="">-- Choose Equipment --</option>
                      {ownedEquipments.map((eq) => (
                        <option key={eq.id} value={eq.id}>
                          {eq.name} ({eq.assetTag || `#${eq.id}`}) {eq.isShareable ? "✓ Shareable" : "⚠️ Not marked shareable"}
                        </option>
                      ))}
                    </select>
                  )}
                  <span style={{ fontSize: "12px", color: "#64748b", marginTop: "4px", display: "block" }}>
                    Only equipment with Shareable flag enabled can be published.
                  </span>
                </div>
              ) : (
                <div style={{ marginBottom: "16px", padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>Equipment:</div>
                  <div style={{ fontWeight: 700, color: "#0f172a", fontSize: "15px" }}>{editingListing.equipmentName}</div>
                  <div style={{ fontSize: "12px", color: "#64748b" }}>Asset #{editingListing.assetTag}</div>
                </div>
              )}

              {/* Grid 2 Columns: Rate & External Share % */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                    Fee Per Hour (₹) *
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={listingForm.feePerHour}
                    onChange={(e) => setListingForm({ ...listingForm, feePerHour: e.target.value })}
                    required
                    style={{
                      width: "100%",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      border: "1px solid #cbd5e1",
                      fontSize: "14px",
                    }}
                  />
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Set 0 for free academic sharing</span>
                </div>

                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                    External Share Limit (%) *
                  </label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    value={listingForm.externalSharePercentage}
                    onChange={(e) => setListingForm({ ...listingForm, externalSharePercentage: e.target.value })}
                    required
                    style={{
                      width: "100%",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      border: "1px solid #cbd5e1",
                      fontSize: "14px",
                    }}
                  />
                  <span style={{ fontSize: "12px", color: "#64748b" }}>Max weekly capacity for partners</span>
                </div>
              </div>

              {/* Grid 2 Columns: Min Notice & Max Duration */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                    Min Notice Period (Hours) *
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={listingForm.minNoticeHours}
                    onChange={(e) => setListingForm({ ...listingForm, minNoticeHours: e.target.value })}
                    required
                    style={{
                      width: "100%",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      border: "1px solid #cbd5e1",
                      fontSize: "14px",
                    }}
                  />
                </div>

                <div>
                  <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                    Max Booking Duration (Hours) *
                  </label>
                  <input
                    type="number"
                    min="1"
                    value={listingForm.maxBookingDurationHours}
                    onChange={(e) => setListingForm({ ...listingForm, maxBookingDurationHours: e.target.value })}
                    required
                    style={{
                      width: "100%",
                      padding: "10px 12px",
                      borderRadius: "8px",
                      border: "1px solid #cbd5e1",
                      fontSize: "14px",
                    }}
                  />
                </div>
              </div>

              {/* Terms and conditions */}
              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                  Sharing Terms & Guidelines
                </label>
                <textarea
                  rows="4"
                  maxLength="3000"
                  value={listingForm.sharingTerms}
                  onChange={(e) => setListingForm({ ...listingForm, sharingTerms: e.target.value })}
                  placeholder="Enter lab rules, prerequisite training, safety gear requirements..."
                  style={{
                    width: "100%",
                    padding: "10px 12px",
                    borderRadius: "8px",
                    border: "1px solid #cbd5e1",
                    fontSize: "13px",
                    fontFamily: "inherit",
                    resize: "vertical",
                  }}
                />
              </div>

              {/* Active Toggle */}
              <div style={{ marginBottom: "24px", display: "flex", alignItems: "center", gap: "10px" }}>
                <input
                  type="checkbox"
                  id="activeCheck"
                  checked={listingForm.active}
                  onChange={(e) => setListingForm({ ...listingForm, active: e.target.checked })}
                  style={{ width: "18px", height: "18px", cursor: "pointer" }}
                />
                <label htmlFor="activeCheck" style={{ fontSize: "14px", fontWeight: 600, color: "#1e293b", cursor: "pointer" }}>
                  Publish as Active (visible in inter-institutional catalog immediately)
                </label>
              </div>

              {/* Actions */}
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "12px" }}>
                <button
                  type="button"
                  onClick={() => setListingModalOpen(false)}
                  style={{ padding: "10px 16px", borderRadius: "8px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={listingSubmitting}
                  style={{
                    padding: "10px 20px",
                    borderRadius: "8px",
                    border: "none",
                    background: "#2563eb",
                    color: "#fff",
                    fontWeight: 600,
                    cursor: "pointer",
                    boxShadow: "0 2px 4px rgba(37,99,235,0.2)",
                  }}
                >
                  {listingSubmitting ? "Saving..." : editingListing ? "Update Listing" : "Publish Listing"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: INSPECT GRANTS (for Agreement)                         */}
      {/* ============================================================== */}
      {grantsModalOpen && selectedAgreementForGrants && (
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
              maxWidth: "750px",
              width: "100%",
              maxHeight: "90vh",
              overflowY: "auto",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
            }}
          >
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center", background: "#f8fafc" }}>
              <div>
                <h2 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                  Authorized Researcher Access Grants
                </h2>
                <span style={{ fontSize: "13px", color: "#64748b" }}>
                  Agreement #{selectedAgreementForGrants.id} – {selectedAgreementForGrants.equipmentName} ({selectedAgreementForGrants.partnerInstitutionName})
                </span>
              </div>
              <button
                type="button"
                onClick={() => setGrantsModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <div style={{ padding: "24px" }}>
              {grantsLoading ? (
                <div style={{ textAlign: "center", padding: "32px", color: "#64748b" }}>
                  Loading grants...
                </div>
              ) : grantsList.length === 0 ? (
                <div style={{ textAlign: "center", padding: "32px", color: "#64748b" }}>
                  No active researcher access grants found for this agreement.
                </div>
              ) : (
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                  <thead>
                    <tr style={{ background: "#f1f5f9", textAlign: "left", color: "#475569" }}>
                      <th style={{ padding: "10px 12px" }}>Researcher</th>
                      <th style={{ padding: "10px 12px" }}>Email</th>
                      <th style={{ padding: "10px 12px" }}>Granted At</th>
                      <th style={{ padding: "10px 12px" }}>Status</th>
                      <th style={{ padding: "10px 12px", textAlign: "right" }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {grantsList.map((g) => (
                      <tr key={g.id} style={{ borderBottom: "1px solid #e2e8f0" }}>
                        <td style={{ padding: "12px", fontWeight: 600, color: "#0f172a" }}>{g.userName}</td>
                        <td style={{ padding: "12px", color: "#475569" }}>{g.userEmail}</td>
                        <td style={{ padding: "12px", color: "#64748b" }}>{fmtDateTime(g.grantedAt)}</td>
                        <td style={{ padding: "12px" }}>
                          <span
                            style={{
                              padding: "3px 8px",
                              borderRadius: "10px",
                              fontSize: "11px",
                              fontWeight: 700,
                              background: g.status === "ACTIVE" ? "#dcfce7" : "#fee2e2",
                              color: g.status === "ACTIVE" ? "#15803d" : "#b91c1c",
                            }}
                          >
                            {g.status}
                          </span>
                        </td>
                        <td style={{ padding: "12px", textAlign: "right" }}>
                          {g.status === "ACTIVE" && canManageListings && (
                            <button
                              type="button"
                              onClick={() => handleOpenRevokeGrant(g)}
                              style={{
                                padding: "4px 8px",
                                background: "#fee2e2",
                                color: "#b91c1c",
                                border: "1px solid #fecaca",
                                borderRadius: "4px",
                                fontSize: "12px",
                                fontWeight: 600,
                                cursor: "pointer",
                              }}
                            >
                              Revoke Grant
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              <div style={{ marginTop: "24px", textAlign: "right" }}>
                <button
                  type="button"
                  onClick={() => setGrantsModalOpen(false)}
                  style={{ padding: "8px 16px", borderRadius: "6px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: INSPECT USAGE FEES                                      */}
      {/* ============================================================== */}
      {usageFeesModalOpen && (
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
              maxWidth: "800px",
              width: "100%",
              maxHeight: "90vh",
              overflowY: "auto",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #e2e8f0",
            }}
          >
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #e2e8f0", display: "flex", justifyContent: "space-between", alignItems: "center", background: "#f8fafc" }}>
              <h2 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                Accrued Usage Fees & Booking Items
              </h2>
              <button
                type="button"
                onClick={() => setUsageFeesModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <div style={{ padding: "24px" }}>
              {usageFeesLoading ? (
                <div style={{ textAlign: "center", padding: "32px", color: "#64748b" }}>
                  Calculating usage fees...
                </div>
              ) : !usageFeesData ? (
                <div style={{ textAlign: "center", padding: "32px", color: "#64748b" }}>
                  No fee records found.
                </div>
              ) : (
                <>
                  {/* Summary metric cards */}
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "12px", marginBottom: "20px" }}>
                    <div style={{ padding: "14px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>Booked Time</div>
                      <div style={{ fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                        {(usageFeesData.totalBookedMinutes / 60).toFixed(1)} hrs
                      </div>
                    </div>

                    <div style={{ padding: "14px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                      <div style={{ fontSize: "12px", color: "#64748b" }}>Actual Used Time</div>
                      <div style={{ fontSize: "18px", fontWeight: 700, color: "#0f172a" }}>
                        {(usageFeesData.totalUsedMinutes / 60).toFixed(1)} hrs
                      </div>
                    </div>

                    <div style={{ padding: "14px", background: "#f0fdf4", borderRadius: "8px", border: "1px solid #bbf7d0" }}>
                      <div style={{ fontSize: "12px", color: "#166534" }}>Total Actual Fee</div>
                      <div style={{ fontSize: "18px", fontWeight: 700, color: "#15803d" }}>
                        {fmtCurrency(usageFeesData.totalActualFee)}
                      </div>
                    </div>

                    <div style={{ padding: "14px", background: "#eff6ff", borderRadius: "8px", border: "1px solid #bfdbfe" }}>
                      <div style={{ fontSize: "12px", color: "#1e40af" }}>Total Estimated Fee</div>
                      <div style={{ fontSize: "18px", fontWeight: 700, color: "#2563eb" }}>
                        {fmtCurrency(usageFeesData.totalEstimatedFee)}
                      </div>
                    </div>
                  </div>

                  {/* Booking items list */}
                  <h4 style={{ margin: "16px 0 8px 0", fontSize: "14px", color: "#334155" }}>
                    Booking Fee Breakdown ({usageFeesData.items?.length || 0} sessions)
                  </h4>
                  {usageFeesData.items?.length === 0 ? (
                    <div style={{ fontSize: "13px", color: "#64748b", fontStyle: "italic" }}>
                      No individual booking sessions recorded under this agreement.
                    </div>
                  ) : (
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
                      <thead>
                        <tr style={{ background: "#f1f5f9", textAlign: "left", color: "#475569" }}>
                          <th style={{ padding: "8px 10px" }}>Booking #</th>
                          <th style={{ padding: "8px 10px" }}>Start – End</th>
                          <th style={{ padding: "8px 10px" }}>Used (Mins)</th>
                          <th style={{ padding: "8px 10px" }}>Rate</th>
                          <th style={{ padding: "8px 10px" }}>Fee Charged</th>
                          <th style={{ padding: "8px 10px" }}>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {usageFeesData.items.map((it) => (
                          <tr key={it.bookingId} style={{ borderBottom: "1px solid #e2e8f0" }}>
                            <td style={{ padding: "10px", fontWeight: 600 }}>#{it.bookingId}</td>
                            <td style={{ padding: "10px", color: "#475569" }}>
                              {fmtDateTime(it.startTime)}
                            </td>
                            <td style={{ padding: "10px" }}>{it.usedMinutes || it.bookedMinutes}m</td>
                            <td style={{ padding: "10px" }}>{fmtCurrency(it.feePerHour)}/hr</td>
                            <td style={{ padding: "10px", fontWeight: 700, color: "#0f172a" }}>
                              {fmtCurrency(it.actualFee || it.estimatedFee)}
                            </td>
                            <td style={{ padding: "10px" }}>
                              <span style={{ fontSize: "11px", fontWeight: 600, padding: "2px 6px", borderRadius: "8px", background: "#f1f5f9", color: "#334155" }}>
                                {it.status}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </>
              )}

              <div style={{ marginTop: "24px", textAlign: "right" }}>
                <button
                  type="button"
                  onClick={() => setUsageFeesModalOpen(false)}
                  style={{ padding: "8px 16px", borderRadius: "6px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL: REVOKE CONFIRMATION (Agreement or Grant)                */}
      {/* ============================================================== */}
      {revokeModalOpen && revokeTarget && (
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
              maxWidth: "500px",
              width: "100%",
              boxShadow: "0 20px 25px -5px rgba(0,0,0,0.2)",
              border: "1px solid #fecaca",
            }}
          >
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #fee2e2", background: "#fef2f2", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <span style={{ fontSize: "20px" }}>⚠️</span>
                <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#991b1b" }}>
                  Confirm Revocation
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setRevokeModalOpen(false)}
                style={{ background: "none", border: "none", fontSize: "20px", color: "#64748b", cursor: "pointer" }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmitRevocation} style={{ padding: "24px" }}>
              <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "#334155", lineHeight: 1.5 }}>
                You are about to revoke <strong>{revokeTarget.title}</strong>.
              </p>

              {revokeTarget.type === "agreement" && (
                <div style={{ padding: "12px", background: "#fff7ed", border: "1px solid #fed7aa", borderRadius: "8px", fontSize: "13px", color: "#9a3412", marginBottom: "16px" }}>
                  ⚠️ All future bookings scheduled under this agreement will be automatically cancelled, and time slots will be released to the waitlist queue.
                </div>
              )}

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: 600, color: "#334155", marginBottom: "6px" }}>
                  Mandatory Revocation Reason *
                </label>
                <textarea
                  rows="3"
                  required
                  value={revokeReason}
                  onChange={(e) => setRevokeReason(e.target.value)}
                  placeholder="State the audit reason for revocation (e.g., protocol violation, maintenance overhaul, contract expired)..."
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
                  onClick={() => setRevokeModalOpen(false)}
                  style={{ padding: "10px 16px", borderRadius: "8px", border: "1px solid #cbd5e1", background: "#fff", color: "#475569", fontWeight: 600, cursor: "pointer" }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={revokeSubmitting}
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
                  {revokeSubmitting ? "Revoking..." : "Confirm Revoke"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

