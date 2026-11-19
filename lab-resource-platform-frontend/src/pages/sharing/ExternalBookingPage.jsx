import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
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

function fmtTime(timeStr) {
  if (!timeStr) return "";
  try {
    const parts = timeStr.split(":");
    const h = parseInt(parts[0], 10);
    const m = parts[1] || "00";
    const ampm = h >= 12 ? "PM" : "AM";
    const h12 = h % 12 || 12;
    return `${h12}:${m} ${ampm}`;
  } catch {
    return timeStr;
  }
}

export default function ExternalBookingPage() {
  const { id: routeEquipmentId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { searchTerm, setSearchPlaceholder } = useSearch();
  const [searchParams] = useSearchParams();

  // Catalog state
  const [listings, setListings] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [selectedInstitutionId, setSelectedInstitutionId] = useState(searchParams.get("institutionId") || "");
  const [selectedCategory, setSelectedCategory] = useState(searchParams.get("category") || "");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  // Active booking modal / drawer state
  const [bookingListing, setBookingListing] = useState(null);
  const [sharedSchedule, setSharedSchedule] = useState(null);
  const [loadingSchedule, setLoadingSchedule] = useState(false);

  // Booking Form State
  const [startDate, setStartDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endDate, setEndDate] = useState("");
  const [endTime, setEndTime] = useState("");
  const [purpose, setPurpose] = useState("");
  const [formError, setFormError] = useState(null);
  const [submittingBooking, setSubmittingBooking] = useState(false);

  // Access Request Modal State (delegates to T11 AccessRequestModal)
  const [requestModalListing, setRequestModalListing] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search shared instruments, partner institutions, specs...");
  }, [setSearchPlaceholder]);

  // Load Colleges / Institutions for filtering
  useEffect(() => {
    api
      .get("/api/colleges")
      .then((res) => setColleges(res.data || []))
      .catch((err) => console.error("Could not fetch colleges:", err));
  }, []);

  // Fetch Available Shared Listings
  const fetchListings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        page,
        size: 12,
      };
      if (selectedInstitutionId) params.institutionId = selectedInstitutionId;
      if (searchTerm) params.keyword = searchTerm;

      const res = await api.get("/api/sharing/discovery", { params });
      const data = res.data;
      if (data?.content) {
        setListings(data.content);
        setTotalPages(data.totalPages || 1);
      } else if (Array.isArray(data)) {
        setListings(data);
        setTotalPages(1);
      } else {
        setListings([]);
      }
    } catch (err) {
      setError(err.response?.data?.message || "Failed to load shared laboratory equipment catalog.");
    } finally {
      setLoading(false);
    }
  }, [page, selectedInstitutionId, searchTerm]);

  useEffect(() => {
    fetchListings();
  }, [fetchListings]);

  // Fetch Shared Schedule for a selected equipment
  const fetchSharedSchedule = useCallback(
    async (equipmentId) => {
      setLoadingSchedule(true);
      try {
        const today = new Date().toISOString().split("T")[0];
        const nextWeek = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];
        const res = await api.get(`/api/equipment/${equipmentId}/shared-schedule`, {
          params: { startDate: today, endDate: nextWeek },
        });
        setSharedSchedule(res.data);
      } catch (err) {
        console.error("Could not load shared schedule:", err);
        setSharedSchedule(null);
      } finally {
        setLoadingSchedule(false);
      }
    },
    []
  );

  // If URL has routeEquipmentId, auto-open booking modal for that equipment
  useEffect(() => {
    if (routeEquipmentId && listings.length > 0) {
      const target = listings.find((l) => String(l.equipmentId) === String(routeEquipmentId));
      if (target) {
        handleOpenBooking(target);
      }
    }
  }, [routeEquipmentId, listings]);

  // Open Booking Modal
  const handleOpenBooking = (listing) => {
    setBookingListing(listing);
    setFormError(null);
    setStartDate(new Date().toISOString().split("T")[0]);
    setEndDate(new Date().toISOString().split("T")[0]);
    setStartTime("09:00");
    setEndTime("11:00");
    setPurpose("");
    fetchSharedSchedule(listing.equipmentId);
  };

  // Close Booking Modal
  const handleCloseBooking = () => {
    setBookingListing(null);
    setSharedSchedule(null);
    setFormError(null);
  };

  // Open Access Request Modal
  const handleOpenRequest = (listing) => {
    setRequestModalListing(listing);
    const today = new Date().toISOString().split("T")[0];
    const nextMonth = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];
    setReqStartDate(today);
    setReqEndDate(nextMonth);
    setReqPurpose("");
  };

  // Close Access Request Modal
  const handleCloseRequest = () => {
    setRequestModalListing(null);
  };

  // Calculate Duration and Estimated Cost in real-time
  const calculatedCost = useMemo(() => {
    if (!startDate || !startTime || !endDate || !endTime || !bookingListing) {
      return { hours: 0, cost: 0, isValid: false };
    }
    const start = new Date(`${startDate}T${startTime}:00`);
    const end = new Date(`${endDate}T${endTime}:00`);
    if (isNaN(start.getTime()) || isNaN(end.getTime()) || end <= start) {
      return { hours: 0, cost: 0, isValid: false };
    }
    const durationHours = (end - start) / (1000 * 60 * 60);
    const rate = bookingListing.userStatus?.agreementFeePerHour != null
      ? parseFloat(bookingListing.userStatus.agreementFeePerHour)
      : parseFloat(bookingListing.feePerHour || 0);

    return {
      hours: durationHours,
      cost: durationHours * rate,
      isValid: true,
    };
  }, [startDate, startTime, endDate, endTime, bookingListing]);

  // Submit External Booking
  const handleBookingSubmit = async (e) => {
    e.preventDefault();
    setFormError(null);

    if (!startDate || !startTime || !endDate || !endTime) {
      setFormError("Please select both start and end date/time.");
      return;
    }
    const start = new Date(`${startDate}T${startTime}:00`);
    const end = new Date(`${endDate}T${endTime}:00`);

    if (end <= start) {
      setFormError("End time must be after start time.");
      return;
    }
    if (start < new Date()) {
      setFormError("Booking start time cannot be in the past.");
      return;
    }
    if (!purpose.trim()) {
      setFormError("Please state the research purpose for this booking.");
      return;
    }

    // Check listing max duration
    if (bookingListing.maxBookingDurationHours && calculatedCost.hours > bookingListing.maxBookingDurationHours) {
      setFormError(
        `Booking duration (${calculatedCost.hours.toFixed(1)} hrs) exceeds listing maximum limit of ${
          bookingListing.maxBookingDurationHours
        } hours.`
      );
      return;
    }

    // Check allowed operating window if agreement is present
    if (sharedSchedule?.allowedBookingWindow) {
      const { allowedStartTime, allowedEndTime } = sharedSchedule.allowedBookingWindow;
      if (allowedStartTime && allowedEndTime) {
        if (startTime < allowedStartTime || endTime > allowedEndTime) {
          setFormError(
            `Selected time violates agreement operating window (${fmtTime(allowedStartTime)} – ${fmtTime(
              allowedEndTime
            )}).`
          );
          return;
        }
      }
    }

    // Check occupied slot overlap
    if (sharedSchedule?.occupiedSlots && sharedSchedule.occupiedSlots.length > 0) {
      const hasOverlap = sharedSchedule.occupiedSlots.some((slot) => {
        const sStart = new Date(slot.startTime);
        const sEnd = new Date(slot.endTime);
        return start < sEnd && end > sStart;
      });
      if (hasOverlap) {
        setFormError("The requested time slot overlaps with an existing reserved slot. Please pick another time.");
        return;
      }
    }

    setSubmittingBooking(true);
    try {
      const res = await api.post("/api/bookings", {
        equipmentId: bookingListing.equipmentId,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        purpose: purpose.trim(),
      });

      toast.success(
        `External booking submitted successfully! Status: ${res.data?.status || "PENDING_APPROVAL"}. Estimated fee: ${fmtCurrency(
          res.data?.estimatedFee || calculatedCost.cost
        )}.`
      );
      handleCloseBooking();
      navigate("/dashboard/student/bookings");
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || "External booking submission failed.";
      setFormError(typeof msg === "string" ? msg : "Booking request rejected by partner rules.");
      toast.error("Failed to complete external booking.");
    } finally {
      setSubmittingBooking(false);
    }
  };

  // Categories list
  const categories = useMemo(() => {
    return Array.from(new Set(listings.map((l) => l.categoryName).filter(Boolean)));
  }, [listings]);

  // Filter listings by category
  const filteredListings = useMemo(() => {
    if (!selectedCategory) return listings;
    return listings.filter((l) => l.categoryName === selectedCategory);
  }, [listings, selectedCategory]);

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span className="admin-eyebrow">INTER-INSTITUTION PLATFORM</span>
            <span
              style={{
                background: "#dcfce7",
                color: "#15803d",
                fontSize: "11px",
                fontWeight: 700,
                padding: "3px 8px",
                borderRadius: "12px",
              }}
            >
              Task T12
            </span>
          </div>
          <h1 className="admin-title">🌐 External Equipment Booking &amp; Sharing Catalog</h1>
          <p className="admin-subtitle">
            Browse scientific instruments made available by partner universities and institutions. Reserve approved external slots or request research access.
          </p>
        </div>

        {/* Filter Controls */}
        <div style={{ display: "flex", gap: "12px", alignItems: "center", flexWrap: "wrap" }}>
          {colleges.length > 0 && (
            <select
              className="admin-filter-select"
              value={selectedInstitutionId}
              onChange={(e) => {
                setSelectedInstitutionId(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All Partner Institutions</option>
              {colleges.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}

          {categories.length > 0 && (
            <select
              className="admin-filter-select"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {/* Main Content Area */}
      {loading ? (
        <div className="admin-loading-state">
          <div className="booking-spinner" />
          <p style={{ marginTop: "12px", color: "#64748b" }}>Loading external shared equipment catalog…</p>
        </div>
      ) : error ? (
        <div className="admin-empty-card" style={{ borderColor: "#fca5a5" }}>
          <h3 style={{ color: "#b91c1c" }}>Unable to Load Shared Catalog</h3>
          <p>{error}</p>
          <button type="button" className="primary-button" style={{ marginTop: "12px" }} onClick={fetchListings}>
            Retry
          </button>
        </div>
      ) : filteredListings.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No Shared Instruments Found</h3>
          <p>
            {searchTerm || selectedInstitutionId
              ? "No partner equipment matches your search filter. Try clearing filters."
              : "No partner institutions have published active equipment sharing listings at this time."}
          </p>
        </div>
      ) : (
        <div className="student-equipment-grid">
          {filteredListings.map((listing) => {
            const userStatus = listing.userStatus;
            const hasAgreement = userStatus?.hasActiveAgreement === true;
            const hasPendingRequest =
              userStatus?.latestRequestStatus === "SUBMITTED" ||
              userStatus?.latestRequestStatus === "FORWARDED";

            return (
              <article key={listing.listingId} className="student-equipment-card" style={{ display: "flex", flexDirection: "column" }}>
                {/* Card Header */}
                <div className="student-card-header">
                  <span className="admin-badge-college" title="Owner Institution">
                    🏛️ {listing.ownerInstitutionName || "Partner Institution"}
                  </span>
                  <span
                    className="admin-status-pill"
                    style={{
                      background: hasAgreement ? "#dcfce7" : hasPendingRequest ? "#fef3c7" : "#f1f5f9",
                      color: hasAgreement ? "#15803d" : hasPendingRequest ? "#b45309" : "#475569",
                    }}
                  >
                    {hasAgreement ? "Active Agreement" : hasPendingRequest ? "Request Pending" : "Access Required"}
                  </span>
                </div>

                {/* Card Body */}
                <div className="student-card-body" style={{ flex: 1 }}>
                  <h3 className="student-equipment-title">{listing.equipmentName}</h3>

                  <div className="student-equipment-meta">
                    {listing.categoryName && (
                      <span className="student-meta-item">
                        📁 <strong>Category:</strong> {listing.categoryName}
                      </span>
                    )}
                    {listing.assetTag && (
                      <span className="student-meta-item">
                        🏷️ <strong>Asset:</strong> {listing.assetTag}
                      </span>
                    )}
                  </div>

                  {listing.description && (
                    <p className="student-equipment-desc" style={{ WebkitLineClamp: 2 }}>
                      {listing.description}
                    </p>
                  )}

                  {/* Sharing Terms Box */}
                  <div
                    style={{
                      background: "#f8fafc",
                      border: "1px solid #e2e8f0",
                      borderRadius: "8px",
                      padding: "10px 12px",
                      margin: "12px 0",
                      fontSize: "12px",
                      display: "grid",
                      gridTemplateColumns: "1fr 1fr",
                      gap: "8px",
                    }}
                  >
                    <div>
                      <span style={{ color: "#64748b" }}>Rate:</span>{" "}
                      <strong style={{ color: "#15803d", fontSize: "14px" }}>
                        {fmtCurrency(listing.feePerHour)}/hr
                      </strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b" }}>External Quota:</span>{" "}
                      <strong>{listing.externalSharePercentage || 0}%</strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b" }}>Min Notice:</span>{" "}
                      <strong>{listing.minNoticeHours || 0} hrs</strong>
                    </div>
                    <div>
                      <span style={{ color: "#64748b" }}>Max Session:</span>{" "}
                      <strong>{listing.maxBookingDurationHours || 8} hrs</strong>
                    </div>
                  </div>

                  {/* Active Agreement Window Badge (if user has agreement) */}
                  {hasAgreement && (
                    <div
                      style={{
                        background: "#f0fdf4",
                        border: "1px solid #bbf7d0",
                        borderRadius: "6px",
                        padding: "8px 10px",
                        fontSize: "11px",
                        color: "#166534",
                        marginBottom: "10px",
                      }}
                    >
                      <div>
                        ✅ <strong>Agreement #{userStatus.activeAgreementId} Active</strong>
                      </div>
                      <div>Valid: {fmtDate(userStatus.agreementStartDate)} – {fmtDate(userStatus.agreementEndDate)}</div>
                      {userStatus.allowedStartTime && (
                        <div>
                          Window: {fmtTime(userStatus.allowedStartTime)} – {fmtTime(userStatus.allowedEndTime)}
                        </div>
                      )}
                    </div>
                  )}

                  {listing.sharingTerms && (
                    <div style={{ fontSize: "11px", color: "#64748b", fontStyle: "italic", marginBottom: "8px" }}>
                      Terms: {listing.sharingTerms}
                    </div>
                  )}
                </div>

                {/* Card Footer Actions */}
                <div className="student-card-footer" style={{ borderTop: "1px solid #e2e8f0", paddingTop: "12px" }}>
                  {hasAgreement ? (
                    <button
                      type="button"
                      className="student-book-btn"
                      onClick={() => handleOpenBooking(listing)}
                    >
                      📅 Book External Slot
                    </button>
                  ) : hasPendingRequest ? (
                    <button
                      type="button"
                      className="student-book-btn"
                      style={{ background: "#94a3b8", cursor: "default" }}
                      disabled
                    >
                      ⏳ Access Under Review
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="student-book-btn"
                      style={{ background: "#2563eb" }}
                      onClick={() => handleOpenRequest(listing)}
                    >
                      📝 Request Research Access
                    </button>
                  )}
                </div>
              </article>
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
              borderRadius: "8px",
              border: "1px solid #cbd5e1",
              background: page === 0 ? "#f1f5f9" : "#fff",
              cursor: page === 0 ? "not-allowed" : "pointer",
              fontSize: "14px",
              fontWeight: 500,
            }}
          >
            ← Previous
          </button>
          <span style={{ fontSize: "14px", color: "#475569" }}>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            style={{
              padding: "8px 16px",
              borderRadius: "8px",
              border: "1px solid #cbd5e1",
              background: page >= totalPages - 1 ? "#f1f5f9" : "#fff",
              cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
              fontSize: "14px",
              fontWeight: 500,
            }}
          >
            Next →
          </button>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL 1: EXTERNAL EQUIPMENT BOOKING & SHARED SCHEDULING                   */}
      {/* ========================================================================= */}
      {bookingListing && (
        <div className="modal-backdrop" onClick={handleCloseBooking}>
          <div
            className="modal-card"
            style={{ maxWidth: "800px", width: "95%", maxHeight: "90vh", overflowY: "auto" }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "16px" }}>
              <div>
                <span className="admin-eyebrow">EXTERNAL RESERVATION</span>
                <h2 style={{ margin: "4px 0 0 0", fontSize: "20px", color: "#0f172a" }}>
                  Book {bookingListing.equipmentName}
                </h2>
                <p style={{ margin: "2px 0 0 0", fontSize: "13px", color: "#64748b" }}>
                  Partner Institution: <strong>{bookingListing.ownerInstitutionName}</strong>
                </p>
              </div>
              <button
                type="button"
                style={{ background: "none", border: "none", fontSize: "20px", cursor: "pointer", color: "#64748b" }}
                onClick={handleCloseBooking}
              >
                ✕
              </button>
            </div>

            {/* Shared Schedule & Capacity Status Banner */}
            {loadingSchedule ? (
              <div style={{ textAlign: "center", padding: "20px", background: "#f8fafc", borderRadius: "8px", marginBottom: "16px" }}>
                <div className="booking-spinner" />
                <p style={{ fontSize: "12px", color: "#64748b", marginTop: "8px" }}>Loading partner capacity and schedule…</p>
              </div>
            ) : sharedSchedule ? (
              <div
                style={{
                  background: "#f8fafc",
                  border: "1px solid #e2e8f0",
                  borderRadius: "8px",
                  padding: "14px",
                  marginBottom: "20px",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px", flexWrap: "wrap", gap: "8px" }}>
                  <span style={{ fontSize: "13px", fontWeight: 700, color: "#1e293b" }}>
                    📊 Partner Capacity &amp; Booking Window
                  </span>
                  {sharedSchedule.allowedBookingWindow && (
                    <span style={{ fontSize: "12px", color: "#15803d", background: "#dcfce7", padding: "2px 8px", borderRadius: "12px", fontWeight: 600 }}>
                      Window: {fmtTime(sharedSchedule.allowedBookingWindow.allowedStartTime)} – {fmtTime(sharedSchedule.allowedBookingWindow.allowedEndTime)}
                    </span>
                  )}
                </div>

                {/* Quota Progress */}
                {sharedSchedule.weeklyExternalShareLimitMinutes && (
                  <div style={{ marginBottom: "12px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "11px", color: "#64748b", marginBottom: "4px" }}>
                      <span>External Capacity Allocated This Week:</span>
                      <span>
                        {(sharedSchedule.weeklyExternalMinutesUsed / 60).toFixed(1)}h / {(sharedSchedule.weeklyExternalShareLimitMinutes / 60).toFixed(1)}h limit
                      </span>
                    </div>
                    <div className="mini-progress-bar" style={{ width: "100%", height: "8px" }}>
                      <div
                        className="mini-progress-fill med"
                        style={{
                          width: `${Math.min(
                            100,
                            (sharedSchedule.weeklyExternalMinutesUsed / Math.max(1, sharedSchedule.weeklyExternalShareLimitMinutes)) * 100
                          )}%`,
                        }}
                      />
                    </div>
                  </div>
                )}

                {/* Occupied slots summary */}
                <div style={{ fontSize: "12px", color: "#475569" }}>
                  <strong>Reserved / Blocked Slots This Week:</strong>
                  {sharedSchedule.occupiedSlots?.length === 0 ? (
                    <span style={{ color: "#15803d", marginLeft: "6px" }}>No conflicting bookings. All operating hours currently open.</span>
                  ) : (
                    <div style={{ display: "flex", gap: "6px", flexWrap: "wrap", marginTop: "6px" }}>
                      {sharedSchedule.occupiedSlots.map((slot) => (
                        <span
                          key={slot.slotId}
                          style={{
                            background: slot.type === "INTERNAL" ? "#fee2e2" : "#fef3c7",
                            color: slot.type === "INTERNAL" ? "#b91c1c" : "#b45309",
                            padding: "3px 8px",
                            borderRadius: "4px",
                            fontSize: "11px",
                            fontFamily: "monospace",
                          }}
                        >
                          ⛔ {new Date(slot.startTime).toLocaleDateString("en-IN", { month: "short", day: "numeric" })}{" "}
                          {fmtTime(new Date(slot.startTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false }))} -{" "}
                          {fmtTime(new Date(slot.endTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false }))}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ) : null}

            {/* Booking Form */}
            <form onSubmit={handleBookingSubmit}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
                <div>
                  <label className="booking-form-label" htmlFor="ext-start-date">Start Date</label>
                  <input
                    id="ext-start-date"
                    type="date"
                    className="booking-form-input"
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="booking-form-label" htmlFor="ext-start-time">Start Time (IST)</label>
                  <input
                    id="ext-start-time"
                    type="time"
                    className="booking-form-input"
                    value={startTime}
                    onChange={(e) => setStartTime(e.target.value)}
                    required
                  />
                </div>

                <div>
                  <label className="booking-form-label" htmlFor="ext-end-date">End Date</label>
                  <input
                    id="ext-end-date"
                    type="date"
                    className="booking-form-input"
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label className="booking-form-label" htmlFor="ext-end-time">End Time (IST)</label>
                  <input
                    id="ext-end-time"
                    type="time"
                    className="booking-form-input"
                    value={endTime}
                    onChange={(e) => setEndTime(e.target.value)}
                    required
                  />
                </div>
              </div>

              {/* Research Purpose */}
              <div style={{ marginBottom: "16px" }}>
                <label className="booking-form-label" htmlFor="ext-purpose">Research Project &amp; Purpose</label>
                <textarea
                  id="ext-purpose"
                  className="booking-form-textarea"
                  rows={3}
                  placeholder="Detail your experiments, research objective, and required sample specifications..."
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  required
                />
              </div>

              {/* Live Cost Calculation Card */}
              <div
                style={{
                  background: "#f0fdf4",
                  border: "1px solid #bbf7d0",
                  borderRadius: "8px",
                  padding: "14px 16px",
                  marginBottom: "20px",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  flexWrap: "wrap",
                  gap: "12px",
                }}
              >
                <div>
                  <div style={{ fontSize: "12px", color: "#166534", fontWeight: 600 }}>APPLICABLE SHARING TARIFF</div>
                  <div style={{ fontSize: "13px", color: "#374151", marginTop: "2px" }}>
                    Agreed Rate: <strong>{fmtCurrency(bookingListing.userStatus?.agreementFeePerHour || bookingListing.feePerHour)}/hr</strong> ×{" "}
                    <strong>{calculatedCost.hours.toFixed(1)} hrs</strong> duration
                  </div>
                </div>

                <div style={{ textAlign: "right" }}>
                  <div style={{ fontSize: "11px", color: "#166534", fontWeight: 700 }}>ESTIMATED SETTLEMENT FEE</div>
                  <div style={{ fontSize: "24px", fontWeight: 800, color: "#15803d" }}>
                    {fmtCurrency(calculatedCost.cost)}
                  </div>
                </div>
              </div>

              {formError && (
                <div className="analytics-error-box" style={{ marginBottom: "16px" }}>
                  <span>⚠️ {formError}</span>
                </div>
              )}

              {/* Actions */}
              <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                <button type="button" className="secondary-button" onClick={handleCloseBooking}>
                  Cancel
                </button>
                <button type="submit" className="primary-button" disabled={submittingBooking || !calculatedCost.isValid}>
                  {submittingBooking ? "Submitting Booking…" : "Confirm External Booking"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* T11 SEPARATED COMPONENT: INTER-INSTITUTION ACCESS REQUEST MODAL          */}
      {/* ========================================================================= */}
      {requestModalListing && (
        <AccessRequestModal
          listing={requestModalListing}
          onClose={handleCloseRequest}
          onSuccess={fetchListings}
        />
      )}
    </div>
  );
}

