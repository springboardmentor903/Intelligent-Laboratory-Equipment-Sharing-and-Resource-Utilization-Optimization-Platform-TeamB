import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import EquipmentAvailabilityModal from "../../components/EquipmentAvailabilityModal";

const STATUS_CONFIG = {
  AVAILABLE: { bg: "#dcfce7", text: "#15803d", label: "Available" },
  IN_USE: { bg: "#e0f2fe", text: "#0369a1", label: "In Use" },
  UNDER_MAINTENANCE: { bg: "#fef3c7", text: "#b45309", label: "Under Maintenance" },
  DECOMMISSIONED: { bg: "#fee2e2", text: "#b91c1c", label: "Decommissioned" },
};

export default function StaffEquipmentsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();

  const isStaff = [
    "INSTITUTION_ADMINISTRATOR",
    "DEPARTMENT_HEAD",
    "LAB_MANAGER",
    "LAB_TECHNICIAN",
  ].includes(user?.role);

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [equipments, setEquipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [updatingId, setUpdatingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [selectedEquipmentForAvail, setSelectedEquipmentForAvail] = useState(null);

  const loadColleges = useCallback(async () => {
    // Only System Admin needs full list of colleges to filter
    if (!isStaff) {
      try {
        const res = await api.get("/api/colleges");
        setColleges(res.data || []);
      } catch (err) {
        console.error("Failed to load colleges", err);
      }
    }
  }, [isStaff]);

  const loadEquipments = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/equipment");
      setEquipments(res.data || []);
    } catch (err) {
      toast.error("Could not load equipment catalog.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadColleges();
    loadEquipments();
  }, [loadColleges, loadEquipments]);

  async function handleStatusChange(item, newStatus) {
    if (!newStatus || newStatus === item.status) return;
    setUpdatingId(item.id);
    try {
      await api.patch(`/api/equipment/${item.id}/status`, { status: newStatus });
      setEquipments((prev) =>
        prev.map((e) => (e.id === item.id ? { ...e, status: newStatus } : e))
      );
      toast.success(`"${item.name}" status updated to ${STATUS_CONFIG[newStatus]?.label || newStatus}.`);
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to update equipment status.");
    } finally {
      setUpdatingId(null);
    }
  }

  async function handleDeleteEquipment(id, name) {
    if (!window.confirm(`Are you sure you want to delete "${name}"?`)) {
      return;
    }
    setDeletingId(id);
    try {
      await api.delete(`/api/equipment/${id}`);
      setEquipments((prev) => prev.filter((item) => item.id !== id));
      toast.success(`"${name}" was deleted successfully.`);
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not delete equipment.");
    } finally {
      setDeletingId(null);
    }
  }

  // Unique categories for filtering
  const categories = Array.from(
    new Set(equipments.map((e) => e.category).filter(Boolean))
  );

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = isStaff
    ? user?.collegeName || "Your College"
    : selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  // Filter equipments by selected college (for System Admin), category, and search term
  const filteredEquipments = equipments.filter((item) => {
    if (!isStaff && selectedCollegeId) {
      const itemCollegeId = item.collegeId || item.institutionId;
      if (String(itemCollegeId) !== String(selectedCollegeId)) {
        return false;
      }
    }
    if (categoryFilter && item.category !== categoryFilter) {
      return false;
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchName = item.name?.toLowerCase().includes(q);
      const matchCategory = item.category?.toLowerCase().includes(q);
      const matchModel = item.model?.toLowerCase().includes(q);
      const matchMfr = item.manufacturer?.toLowerCase().includes(q);
      const matchSpecs = item.specifications?.toLowerCase().includes(q);
      const matchTags = item.tags?.toLowerCase().includes(q);
      const matchDesc = item.description?.toLowerCase().includes(q);
      if (!matchName && !matchCategory && !matchModel && !matchMfr && !matchSpecs && !matchTags && !matchDesc) {
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
          <p className="admin-eyebrow">LABORATORY ASSETS &amp; CATALOG</p>
          <h1 className="admin-title">
            {isStaff ? `${currentCollegeName} Laboratory Equipment` : "Colleges Laboratory Equipment"}
          </h1>
          <p className="admin-subtitle">
            {isStaff
              ? `Browse, catalog, and manage laboratory equipment for ${currentCollegeName}. All equipment and operations are locked to your institution.`
              : "Browse and manage laboratory equipment across all affiliated colleges with full system administrator oversight."}
          </p>
        </div>

        <button
          type="button"
          className="admin-btn-primary"
          onClick={() => navigate("/dashboard/equipment/create")}
        >
          + Register Equipment
        </button>
      </div>

      {/* Filter Toolbar */}
      <div className="admin-filter-bar">
        {/* Only System Admin sees the College Dropdown to switch colleges */}
        {!isStaff && (
          <div className="admin-filter-group">
            <label htmlFor="staffCollegeSelect" className="admin-filter-label">
              Filter by College:
            </label>
            <select
              id="staffCollegeSelect"
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
        )}

        {categories.length > 0 && (
          <div className="admin-filter-group">
            <label htmlFor="staffCategorySelect" className="admin-filter-label">
              Category:
            </label>
            <select
              id="staffCategorySelect"
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

      {/* Equipment Cards Grid */}
      {loading ? (
        <div className="admin-loading-state">Loading equipment catalog...</div>
      ) : filteredEquipments.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No equipment available</h3>
          <p>
            {isStaff || selectedCollegeId
              ? `There is no equipment cataloged under ${currentCollegeName}. Click "+ Register Equipment" to add instruments.`
              : "No equipment records match your search criteria."}
          </p>
        </div>
      ) : (
        <div className="student-equipment-grid">
          {filteredEquipments.map((item) => {
            const itemColId = item.collegeId || item.institutionId;
            const colName = isStaff
              ? user?.collegeName || "Affiliated College"
              : itemColId
              ? collegeMap[itemColId] || "Affiliated College"
              : "General / Inter-College";

            const statusConf = STATUS_CONFIG[item.status] || {
              bg: "#f3f4f6",
              text: "#374151",
              label: item.status,
            };

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
                      ⏱ Max {item.maxBookingHours || 8} hrs/session
                    </span>
                    <span className="student-feature-pill approval">
                      🛡️ Approval Required (By Default)
                    </span>
                  </div>
                </div>

                {/* Card Footer: Status Dropdown, Schedule & Delete Option */}
                <div className="student-card-footer staff-card-footer">
                  <div className="staff-card-status-control">
                    <label className="staff-status-select-label" htmlFor={`status-select-${item.id}`}>
                      Status:
                    </label>
                    <select
                      id={`status-select-${item.id}`}
                      className="staff-status-select"
                      value={item.status}
                      disabled={updatingId === item.id}
                      onChange={(e) => handleStatusChange(item, e.target.value)}
                    >
                      <option value="AVAILABLE">Available</option>
                      <option value="IN_USE">In Use</option>
                      <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                      <option value="DECOMMISSIONED">Decommissioned</option>
                    </select>
                  </div>

                  <div className="staff-card-btn-group">
                    <button
                      type="button"
                      className="staff-availability-btn"
                      onClick={() => setSelectedEquipmentForAvail(item)}
                      title="View occupied slots and schedule"
                    >
                      🗓️ Schedule
                    </button>

                    <button
                      type="button"
                      className="staff-delete-card-btn"
                      disabled={deletingId === item.id}
                      onClick={() => handleDeleteEquipment(item.id, item.name)}
                      title="Delete equipment"
                    >
                      🗑️ Delete
                    </button>
                  </div>
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
