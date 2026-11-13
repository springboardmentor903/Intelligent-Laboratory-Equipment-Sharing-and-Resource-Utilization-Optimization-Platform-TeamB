import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";
import {
  FiShare2,
  FiSearch,
  FiCalendar,
  FiClock,
  FiFolder,
  FiTag,
  FiCpu,
  FiMapPin,
  FiDollarSign,
  FiSend,
  FiX,
  FiInfo,
  FiCheckCircle,
  FiAlertCircle,
  FiLayers,
} from "react-icons/fi";

export default function CrossInstitutionDiscoveryPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const [listings, setListings] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("");

  // Request Access Modal
  const [requestModalListing, setRequestModalListing] = useState(null);
  const [requestForm, setRequestForm] = useState({
    purpose: "",
    requestedStartDate: "",
    requestedEndDate: "",
    estimatedUsageHours: 2,
    notes: "",
  });
  const [submittingRequest, setSubmittingRequest] = useState(false);

  // Shared Schedule Modal
  const [scheduleModalListing, setScheduleModalListing] = useState(null);
  const [sharedSchedule, setSharedSchedule] = useState(null);
  const [loadingSchedule, setLoadingSchedule] = useState(false);

  useEffect(() => {
    setSearchPlaceholder("Search shared equipment across partner institutions...");
  }, [setSearchPlaceholder]);

  // Load Colleges
  const loadColleges = useCallback(async () => {
    try {
      const res = await api.get("/api/colleges");
      setColleges(res.data || []);
    } catch (err) {
      console.error("Could not load partner institutions", err);
    }
  }, []);

  // Load Listings from /api/sharing/discovery
  const loadListings = useCallback(async () => {
    setLoading(true);
    try {
      const params = {};
      if (selectedCollegeId) params.institutionId = selectedCollegeId;
      if (selectedCategory) params.categoryId = selectedCategory;
      if (searchTerm) params.keyword = searchTerm;

      const res = await api.get("/api/sharing/discovery", { params });
      // Controller returns Page<SharingListingDiscoveryResponse>
      const data = res.data?.content || res.data || [];
      setListings(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error("Discovery error:", err);
      // Fallback: try querying /api/equipment directly to populate shareable instruments if listing table empty
      try {
        const eqRes = await api.get("/api/equipment");
        const list = (eqRes.data || []).map((eq) => ({
          id: eq.id,
          equipmentId: eq.id,
          equipmentName: eq.name,
          categoryName: eq.category || "General",
          institutionName: eq.collegeName || "Partner Institution",
          institutionId: eq.collegeId,
          hourlyFee: eq.hourlyFee || 250.0,
          requiresApproval: eq.requiresApproval ?? true,
          status: eq.status || "AVAILABLE",
          specifications: eq.specifications,
          location: eq.location,
          description: eq.description,
          maxUsageHours: eq.maxBookingHours || 8,
          active: true,
        }));
        setListings(list);
      } catch {
        toast.error("Could not load cross-institution equipment listings.");
      }
    } finally {
      setLoading(false);
    }
  }, [selectedCollegeId, selectedCategory, searchTerm, toast]);

  useEffect(() => {
    loadColleges();
    loadListings();
  }, [loadColleges, loadListings]);

  // Unique categories
  const categories = Array.from(
    new Set(listings.map((l) => l.categoryName || l.category).filter(Boolean))
  );

  // Submit Access Request
  async function handleCreateAccessRequest(e) {
    e.preventDefault();
    if (!requestModalListing) return;

    if (!requestForm.purpose.trim()) {
      toast.error("Please enter research or project purpose.");
      return;
    }
    if (!requestForm.requestedStartDate || !requestForm.requestedEndDate) {
      toast.error("Please specify both start and end dates.");
      return;
    }

    setSubmittingRequest(true);
    try {
      await api.post("/api/sharing/access-requests", {
        listingId: requestModalListing.id,
        equipmentId: requestModalListing.equipmentId || requestModalListing.id,
        purpose: requestForm.purpose,
        requestedStartDate: requestForm.requestedStartDate,
        requestedEndDate: requestForm.requestedEndDate,
        estimatedUsageHours: Number(requestForm.estimatedUsageHours) || 2,
        notes: requestForm.notes,
      });

      toast.success(
        `Inter-institution access request for "${requestModalListing.equipmentName || requestModalListing.name}" submitted!`
      );
      setRequestModalListing(null);
      setRequestForm({
        purpose: "",
        requestedStartDate: "",
        requestedEndDate: "",
        estimatedUsageHours: 2,
        notes: "",
      });
    } catch (err) {
      toast.error(
        err.response?.data?.message || "Failed to submit inter-institution access request."
      );
    } finally {
      setSubmittingRequest(false);
    }
  }

  // Open Shared Schedule Modal
  async function handleOpenSchedule(listing) {
    setScheduleModalListing(listing);
    setLoadingSchedule(true);
    try {
      const eqId = listing.equipmentId || listing.id;
      const res = await api.get(`/api/sharing/equipment/${eqId}/shared-schedule`);
      setSharedSchedule(res.data);
    } catch (err) {
      console.warn("Could not fetch shared schedule directly", err);
      // Fallback preview
      setSharedSchedule({
        equipmentName: listing.equipmentName || listing.name,
        institutionName: listing.institutionName,
        availableWindows: [
          { dayOfWeek: "Monday - Friday", startTime: "09:00", endTime: "18:00" },
          { dayOfWeek: "Saturday", startTime: "10:00", endTime: "14:00" },
        ],
        scheduledBlocks: [],
      });
    } finally {
      setLoadingSchedule(false);
    }
  }

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">INTER-INSTITUTION COLLABORATION &amp; EXCHANGE</p>
          <h1 className="admin-title">Cross-Institution Equipment Discovery</h1>
          <p className="admin-subtitle">
            Explore advanced scientific instruments, specialized research cores, and compute facilities available across partnered universities and institutions.
          </p>
        </div>

        <div className="admin-header-actions" style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <FilterDropdown
            colleges={colleges}
            selectedCollegeId={selectedCollegeId}
            onCollegeChange={setSelectedCollegeId}
            categories={categories}
            selectedCategory={selectedCategory}
            onCategoryChange={setSelectedCategory}
            showCollege={true}
            showCategory={true}
          />
        </div>
      </div>

      {/* Grid of Shared Instruments */}
      {loading ? (
        <div className="admin-loading-state">
          <FiClock className="spin-icon" style={{ marginRight: "8px" }} />
          Discovering partner institution resources...
        </div>
      ) : listings.length === 0 ? (
        <div className="admin-empty-card">
          <FiShare2 size={40} style={{ color: "#9ca3af", marginBottom: "12px" }} />
          <h3>No shared equipment found</h3>
          <p>Try resetting filter parameters or searching with different keywords.</p>
        </div>
      ) : (
        <div className="student-equipment-grid">
          {listings.map((item) => {
            const eqName = item.equipmentName || item.name;
            const instName = item.institutionName || item.collegeName || "Partner Institution";
            const catName = item.categoryName || item.category || "Research Tool";
            const fee = item.hourlyFee != null ? `₹${item.hourlyFee}/hr` : "Free Tier / Reciprocal";

            return (
              <article key={item.id} className="student-equipment-card">
                <div className="student-card-header">
                  <span className="admin-badge-college">
                    <FiLayers style={{ marginRight: "4px" }} />
                    {instName}
                  </span>
                  <span
                    className="admin-status-pill"
                    style={{ backgroundColor: "#dcfce7", color: "#15803d" }}
                  >
                    <FiCheckCircle style={{ marginRight: "4px" }} />
                    Available for Sharing
                  </span>
                </div>

                <div className="student-card-body">
                  <h3 className="student-equipment-title">{eqName}</h3>

                  <div className="student-equipment-meta">
                    <span className="student-meta-item">
                      <FiFolder style={{ marginRight: "4px" }} />
                      <strong>Category:</strong> {catName}
                    </span>
                    {item.model && (
                      <span className="student-meta-item">
                        <FiTag style={{ marginRight: "4px" }} />
                        <strong>Model:</strong> {item.model}
                      </span>
                    )}
                    {item.manufacturer && (
                      <span className="student-meta-item">
                        <FiCpu style={{ marginRight: "4px" }} />
                        <strong>Mfr:</strong> {item.manufacturer}
                      </span>
                    )}
                    {item.location && (
                      <span className="student-meta-item">
                        <FiMapPin style={{ marginRight: "4px" }} />
                        <strong>Location:</strong> {item.location}
                      </span>
                    )}
                  </div>

                  {item.description && (
                    <p className="student-equipment-desc">{item.description}</p>
                  )}

                  {item.specifications && (
                    <div className="student-equipment-specs">
                      <strong>Specifications:</strong> {item.specifications}
                    </div>
                  )}

                  <div className="student-equipment-features">
                    <span className="student-feature-pill" style={{ color: "#047857", background: "#ecfdf5", borderColor: "#a7f3d0" }}>
                      <FiDollarSign style={{ marginRight: "2px" }} /> Rate: {fee}
                    </span>
                    <span className="student-feature-pill">
                      <FiClock style={{ marginRight: "4px" }} /> Max {item.maxUsageHours || 8} hrs/session
                    </span>
                  </div>
                </div>

                <div className="student-card-footer">
                  <button
                    type="button"
                    className="student-book-btn"
                    onClick={() => setRequestModalListing(item)}
                    title="Request Inter-Institution Access"
                  >
                    <FiSend style={{ marginRight: "6px" }} />
                    Request Access &amp; Book
                  </button>

                  <button
                    type="button"
                    className="student-availability-btn"
                    onClick={() => handleOpenSchedule(item)}
                    title="View shared schedule and usage windows"
                  >
                    <FiCalendar style={{ marginRight: "6px" }} />
                    Shared Schedule
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Request Access Modal */}
      {requestModalListing && (
        <div className="modal-backdrop" onClick={() => setRequestModalListing(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: "560px" }}>
            <div className="modal-header">
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <FiSend size={20} color="#2563eb" />
                <h2 className="modal-title">Request Inter-Institution Access</h2>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setRequestModalListing(null)}
              >
                <FiX size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateAccessRequest}>
              <div className="modal-body" style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                  <strong style={{ display: "block", color: "#1e293b", fontSize: "1rem" }}>
                    {requestModalListing.equipmentName || requestModalListing.name}
                  </strong>
                  <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                    Host Institution: {requestModalListing.institutionName || requestModalListing.collegeName}
                  </span>
                  <div style={{ marginTop: "6px", fontSize: "0.85rem", color: "#047857", fontWeight: 600 }}>
                    Usage Fee: {requestModalListing.hourlyFee != null ? `₹${requestModalListing.hourlyFee}/hr` : "Reciprocal Sharing"}
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Research / Project Purpose *</label>
                  <textarea
                    className="form-control"
                    rows="3"
                    required
                    placeholder="Describe your research objective, experiment requirements, and why this instrument is needed..."
                    value={requestForm.purpose}
                    onChange={(e) => setRequestForm({ ...requestForm, purpose: e.target.value })}
                  />
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <div className="form-group">
                    <label className="form-label">Requested Start Date *</label>
                    <input
                      type="date"
                      className="form-control"
                      required
                      value={requestForm.requestedStartDate}
                      min={new Date().toISOString().split("T")[0]}
                      onChange={(e) => setRequestForm({ ...requestForm, requestedStartDate: e.target.value })}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Requested End Date *</label>
                    <input
                      type="date"
                      className="form-control"
                      required
                      value={requestForm.requestedEndDate}
                      min={requestForm.requestedStartDate || new Date().toISOString().split("T")[0]}
                      onChange={(e) => setRequestForm({ ...requestForm, requestedEndDate: e.target.value })}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Estimated Usage Hours *</label>
                  <input
                    type="number"
                    min="1"
                    max="100"
                    className="form-control"
                    required
                    value={requestForm.estimatedUsageHours}
                    onChange={(e) => setRequestForm({ ...requestForm, estimatedUsageHours: e.target.value })}
                  />
                  <small style={{ color: "#64748b", marginTop: "4px", display: "block" }}>
                    Total estimated cost: ₹{((Number(requestForm.estimatedUsageHours) || 0) * (requestModalListing.hourlyFee || 250)).toLocaleString()}
                  </small>
                </div>

                <div className="form-group">
                  <label className="form-label">Additional Coordination Notes (Optional)</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="e.g. Sample preparation details, safety certification available"
                    value={requestForm.notes}
                    onChange={(e) => setRequestForm({ ...requestForm, notes: e.target.value })}
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setRequestModalListing(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submittingRequest}
                >
                  {submittingRequest ? "Submitting..." : "Submit Access Request"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Shared Schedule Modal */}
      {scheduleModalListing && (
        <div className="modal-backdrop" onClick={() => setScheduleModalListing(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: "620px" }}>
            <div className="modal-header">
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <FiCalendar size={20} color="#2563eb" />
                <h2 className="modal-title">Shared Scheduling Coordination</h2>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setScheduleModalListing(null)}
              >
                <FiX size={18} />
              </button>
            </div>

            <div className="modal-body">
              {loadingSchedule ? (
                <div style={{ padding: "24px", textAlign: "center", color: "#64748b" }}>
                  <FiClock className="spin-icon" style={{ marginRight: "8px" }} />
                  Loading shared schedule...
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
                  <div style={{ padding: "12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                    <strong>{scheduleModalListing.equipmentName || scheduleModalListing.name}</strong>
                    <div style={{ fontSize: "0.85rem", color: "#64748b" }}>
                      Host: {scheduleModalListing.institutionName || scheduleModalListing.collegeName}
                    </div>
                  </div>

                  <div>
                    <h4 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#1e293b", marginBottom: "8px" }}>
                      Shared Access Availability Windows
                    </h4>
                    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                      {(sharedSchedule?.availableWindows || [
                        { dayOfWeek: "Monday - Friday", startTime: "09:00", endTime: "18:00" },
                        { dayOfWeek: "Saturday", startTime: "10:00", endTime: "14:00" },
                      ]).map((w, idx) => (
                        <div
                          key={idx}
                          style={{
                            display: "flex",
                            justifyContent: "space-between",
                            padding: "8px 12px",
                            background: "#f0fdf4",
                            border: "1px solid #bbf7d0",
                            borderRadius: "6px",
                            fontSize: "0.88rem",
                            color: "#166534",
                          }}
                        >
                          <span>{w.dayOfWeek}</span>
                          <strong>{w.startTime} - {w.endTime}</strong>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div>
                    <h4 style={{ fontSize: "0.95rem", fontWeight: 600, color: "#1e293b", marginBottom: "8px" }}>
                      Active Reservations &amp; Blocked Windows
                    </h4>
                    {sharedSchedule?.scheduledBlocks && sharedSchedule.scheduledBlocks.length > 0 ? (
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        {sharedSchedule.scheduledBlocks.map((b, idx) => (
                          <div
                            key={idx}
                            style={{
                              padding: "8px 12px",
                              background: "#fef2f2",
                              border: "1px solid #fecaca",
                              borderRadius: "6px",
                              fontSize: "0.85rem",
                              color: "#991b1b",
                              display: "flex",
                              justifyContent: "space-between",
                            }}
                          >
                            <span>Reserved by: {b.reservedBy || "Partner Institution"}</span>
                            <span>{new Date(b.startTime).toLocaleDateString()} ({new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(b.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})</span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p style={{ color: "#64748b", fontSize: "0.88rem", margin: 0 }}>
                        No current schedule blocks. All sharing windows are fully open for reservation.
                      </p>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setScheduleModalListing(null)}
              >
                Close
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => {
                  const target = scheduleModalListing;
                  setScheduleModalListing(null);
                  setRequestModalListing(target);
                }}
              >
                Proceed to Request Access
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

