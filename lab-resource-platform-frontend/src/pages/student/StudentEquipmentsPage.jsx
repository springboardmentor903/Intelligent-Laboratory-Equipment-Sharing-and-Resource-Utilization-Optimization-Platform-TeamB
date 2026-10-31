import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";
import EquipmentAvailabilityModal from "../../components/EquipmentAvailabilityModal";

const STATUS_CONFIG = {
  AVAILABLE: { bg: "#dcfce7", text: "#15803d", label: "Available" },
  IN_USE: { bg: "#e0f2fe", text: "#0369a1", label: "In Use" },
  UNDER_MAINTENANCE: { bg: "#fef3c7", text: "#b45309", label: "Under Maintenance" },
  DECOMMISSIONED: { bg: "#fee2e2", text: "#b91c1c", label: "Decommissioned" },
};

export default function StudentEquipmentsPage() {
  const navigate = useNavigate();
  const toast = useToast();

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [equipments, setEquipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [selectedEquipmentForAvail, setSelectedEquipmentForAvail] = useState(null);

  const loadColleges = useCallback(async () => {
    try {
      const res = await api.get("/api/colleges");
      setColleges(res.data || []);
    } catch (err) {
      console.error("Could not load colleges", err);
    }
  }, []);

  const loadEquipments = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/equipment");
      setEquipments(res.data || []);
    } catch (err) {
      toast.error("Failed to load equipment catalog.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadColleges();
    loadEquipments();
  }, [loadColleges, loadEquipments]);

  // Unique categories for filtering
  const categories = Array.from(
    new Set(equipments.map((e) => e.category).filter(Boolean))
  );

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  // Filter equipments by college, search, and category
  const filteredEquipments = equipments.filter((item) => {
    const itemColId = item.collegeId || item.institutionId;
    if (selectedCollegeId) {
      if (String(itemColId) !== String(selectedCollegeId)) {
        return false;
      }
    }

    if (categoryFilter && item.category !== categoryFilter) {
      return false;
    }

    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchName = item.name?.toLowerCase().includes(q);
      const matchCat = item.category?.toLowerCase().includes(q);
      const matchModel = item.model?.toLowerCase().includes(q);
      const matchMfr = item.manufacturer?.toLowerCase().includes(q);
      const matchSpecs = item.specifications?.toLowerCase().includes(q);
      const matchTags = item.tags?.toLowerCase().includes(q);
      const matchDesc = item.description?.toLowerCase().includes(q);
      if (!matchName && !matchCat && !matchModel && !matchMfr && !matchSpecs && !matchTags && !matchDesc) {
        return false;
      }
    }

    return true;
  });

  return (
    <div className="admin-page-container">
      {/* Page Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">EQUIPMENT CATALOG</p>
          <h1 className="admin-title">Colleges Laboratory Equipment</h1>
          <p className="admin-subtitle">
            Browse and reserve scientific instruments across partner institutions. Select a college to explore its dedicated lab equipment.
          </p>
        </div>
      </div>

      {/* Filter Toolbar with College Dropdown */}
      <div className="admin-filter-bar">
        <div className="admin-filter-group">
          <label htmlFor="studentCollegeSelect" className="admin-filter-label">
            Filter by College:
          </label>
          <select
            id="studentCollegeSelect"
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

        {categories.length > 0 && (
          <div className="admin-filter-group">
            <label htmlFor="categorySelect" className="admin-filter-label">
              Category:
            </label>
            <select
              id="categorySelect"
              className="admin-select"
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            >
              <option value="">All Categories</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>
        )}

        <div className="admin-filter-group admin-search-group">
          <input
            type="text"
            className="admin-input"
            placeholder="Search by instrument name, model, specs, tags..."
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

      {/* Active College Indicator */}
      <div className="admin-active-filter-badge">
        Showing equipment for: <strong>{currentCollegeName}</strong> (
        {filteredEquipments.length} {filteredEquipments.length === 1 ? "instrument" : "instruments"})
      </div>

      {/* Equipments Cards Grid */}
      {loading ? (
        <div className="admin-loading-state">Loading equipment catalog...</div>
      ) : filteredEquipments.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No equipment available</h3>
          <p>
            {selectedCollegeId
              ? `There is no equipment cataloged under ${currentCollegeName}. Try selecting "All Colleges" or choose another institution.`
              : "No equipment records match your search criteria."}
          </p>
        </div>
      ) : (
        <div className="student-equipment-grid">
          {filteredEquipments.map((item) => {
            const itemColId = item.collegeId || item.institutionId;
            const colName = itemColId ? collegeMap[itemColId] || "Affiliated College" : "General / Inter-College";
            const statusConf = STATUS_CONFIG[item.status] || {
              bg: "#f3f4f6",
              text: "#374151",
              label: item.status,
            };
            const isBookable = item.status === "AVAILABLE" || item.status === "IN_USE";

            return (
              <article key={item.id} className="student-equipment-card">
                <div className="student-card-header">
                  <span className="admin-badge-college">{colName}</span>
                  <span
                    className="admin-status-pill"
                    style={{ backgroundColor: statusConf.bg, color: statusConf.text }}
                  >
                    {statusConf.label}
                  </span>
                </div>

                <div className="student-card-body">
                  <h3 className="student-equipment-title">{item.name}</h3>

                  <div className="student-equipment-meta">
                    {item.category && (
                      <span className="student-meta-item">
                        📁 <strong>Category:</strong> {item.category}
                      </span>
                    )}
                    {item.model && (
                      <span className="student-meta-item">
                        🏷️ <strong>Model:</strong> {item.model}
                      </span>
                    )}
                    {item.manufacturer && (
                      <span className="student-meta-item">
                        🏭 <strong>Mfr:</strong> {item.manufacturer}
                      </span>
                    )}
                    {item.location && (
                      <span className="student-meta-item">
                        📍 <strong>Location:</strong> {item.location}
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
                    <span className="student-feature-pill">
                      ⏱️ Max {item.maxBookingHours || 8} hrs/session
                    </span>
                    {item.requiresApproval && (
                      <span className="student-feature-pill approval">
                        🛡️ Manager Approval Required
                      </span>
                    )}
                  </div>
                </div>

                <div className="student-card-footer">
                  <button
                    type="button"
                    className="student-book-btn"
                    disabled={!isBookable}
                    onClick={() => navigate(`/dashboard/student/book/${item.id}`)}
                  >
                    {isBookable ? "📅 Book This Equipment" : "Unavailable for Booking"}
                  </button>

                  <button
                    type="button"
                    className="student-availability-btn"
                    onClick={() => setSelectedEquipmentForAvail(item)}
                    title="View occupied time slots and availability"
                  >
                    🗓️ View Schedule &amp; Slots
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Equipment Availability Modal */}
      {selectedEquipmentForAvail && (
        <EquipmentAvailabilityModal
          equipmentId={selectedEquipmentForAvail.id}
          equipmentName={selectedEquipmentForAvail.name}
          onClose={() => setSelectedEquipmentForAvail(null)}
        />
      )}
    </div>
  );
}
