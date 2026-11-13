import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";
import EquipmentAvailabilityModal from "../../components/EquipmentAvailabilityModal";
import EditEquipmentModal from "../../components/EditEquipmentModal";
import {
  FiFolder,
  FiTag,
  FiCpu,
  FiMapPin,
  FiClock,
  FiShield,
  FiCalendar,
  FiEdit3,
  FiTrash2,
  FiPlus,
} from "react-icons/fi";

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
  const { searchTerm, setSearchPlaceholder } = useSearch();

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
  const [categoryFilter, setCategoryFilter] = useState("");
  const [updatingId, setUpdatingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [selectedEquipmentForAvail, setSelectedEquipmentForAvail] = useState(null);
  const [editingEquipment, setEditingEquipment] = useState(null);

  useEffect(() => {
    setSearchPlaceholder("Search by instrument name, model, specs, tags...");
  }, [setSearchPlaceholder]);

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

  const refreshEquipmentsQuietly = useCallback(async () => {
    try {
      const res = await api.get("/api/equipment");
      setEquipments(res.data || []);
    } catch {
      // background sync
    }
  }, []);

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

    const interval = setInterval(refreshEquipmentsQuietly, 10000);
    let ws = null;
    try {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      ws = new WebSocket(`${protocol}//${window.location.hostname}:8080/ws/utilization`);
      ws.onmessage = () => {
        refreshEquipmentsQuietly();
      };
    } catch (e) {}

    return () => {
      clearInterval(interval);
      if (ws) ws.close();
    };
  }, [loadColleges, loadEquipments, refreshEquipmentsQuietly]);

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
      {/* Page Header with Actions and Filter Dropdown */}
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

        <div className="admin-header-actions" style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <FilterDropdown
            colleges={colleges}
            selectedCollegeId={selectedCollegeId}
            onCollegeChange={setSelectedCollegeId}
            categories={categories}
            selectedCategory={categoryFilter}
            onCategoryChange={setCategoryFilter}
            showCollege={!isStaff && colleges.length > 0}
            showCategory={categories.length > 0}
          />

          <button
            type="button"
            className="admin-btn-primary"
            onClick={() => navigate("/dashboard/equipment/create")}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
          >
            <FiPlus size={16} /> Register Equipment
          </button>
        </div>
      </div>

      {/* Equipment Cards Grid */}
      {loading ? (
        <div className="admin-loading-state">Loading equipment catalog...</div>
      ) : filteredEquipments.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No equipment found</h3>
          <p>
            {isStaff || selectedCollegeId
              ? `No instruments found matching your criteria in ${currentCollegeName}.`
              : "No equipment records match your search criteria."}
          </p>
        </div>
      ) : (
        <div className="student-equipment-grid">
          {filteredEquipments.map((item) => {
            const itemCollegeId = item.collegeId || item.institutionId;
            const itemCollegeName = isStaff
              ? user?.collegeName || "Your College"
              : itemCollegeId
                ? collegeMap[itemCollegeId] || "Affiliated College"
                : "All Colleges";

            const statusConf = STATUS_CONFIG[item.status] || {
              bg: "#f3f4f6",
              text: "#374151",
              label: item.status,
            };

            const isUpdating = updatingId === item.id;
            const isDeleting = deletingId === item.id;

            return (
              <article key={item.id} className="student-equipment-card">
                <div className="student-card-header">
                  <span className="admin-badge-college">{itemCollegeName}</span>
                  <select
                    className="equipment-status-select"
                    value={item.status}
                    disabled={isUpdating}
                    style={{
                      backgroundColor: statusConf.bg,
                      color: statusConf.text,
                      borderColor: statusConf.text,
                    }}
                    onChange={(e) => handleStatusChange(item, e.target.value)}
                  >
                    <option value="AVAILABLE">Available</option>
                    <option value="IN_USE">In Use</option>
                    <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                    <option value="DECOMMISSIONED">Decommissioned</option>
                  </select>
                </div>

                <div className="student-card-body">
                  <h3 className="student-equipment-title">{item.name}</h3>

                  <div className="student-equipment-meta">
                    {item.category && (
                      <span className="student-meta-item">
                        <FiFolder style={{ marginRight: "4px" }} />
                        <strong>Category:</strong> {item.category}
                      </span>
                    )}
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
                    <span className="student-feature-pill">
                      <FiClock style={{ marginRight: "4px" }} /> Max {item.maxBookingHours || 8} hrs/session
                    </span>
                    {item.requiresApproval && (
                      <span className="student-feature-pill approval">
                        <FiShield style={{ marginRight: "4px" }} /> Manager Approval Required
                      </span>
                    )}
                  </div>
                </div>

                <div className="student-card-footer">
                  <button
                    type="button"
                    className="student-availability-btn"
                    onClick={() => setSelectedEquipmentForAvail(item)}
                    title="View occupied time slots and availability"
                    style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}
                  >
                    <FiCalendar size={15} /> View Slots &amp; Availability
                  </button>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
                    <button
                      type="button"
                      className="student-book-btn"
                      style={{
                        backgroundColor: "#2563eb",
                        borderColor: "#2563eb",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                        gap: "6px",
                      }}
                      onClick={() => setEditingEquipment(item)}
                      title="Edit equipment specifications and settings"
                    >
                      <FiEdit3 size={15} /> Edit
                    </button>

                    <button
                      type="button"
                      className="student-book-btn"
                      style={{
                        backgroundColor: "#ef4444",
                        borderColor: "#ef4444",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                        gap: "6px",
                      }}
                      disabled={isDeleting}
                      onClick={() => handleDeleteEquipment(item.id, item.name)}
                      title="Delete equipment from catalog"
                    >
                      <FiTrash2 size={15} /> {isDeleting ? "Deleting..." : "Delete"}
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

      {/* Edit Equipment Modal */}
      {editingEquipment && (
        <EditEquipmentModal
          equipment={editingEquipment}
          onClose={() => setEditingEquipment(null)}
          onSuccess={(updated) => {
            setEquipments((prev) =>
              prev.map((e) => (e.id === updated.id ? { ...e, ...updated } : e))
            );
          }}
        />
      )}
    </div>
  );
}
