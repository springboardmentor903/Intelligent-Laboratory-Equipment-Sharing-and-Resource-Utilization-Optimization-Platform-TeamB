import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";
import EditEquipmentModal from "../../components/EditEquipmentModal";
import EquipmentCard from "../../components/EquipmentCard";
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
  FiInbox,
  FiGrid,
  FiList,
} from "react-icons/fi";

const STATUS_CONFIG = {
  AVAILABLE: { bg: "#dcfce7", text: "#15803d", label: "Available", dot: "#16a34a" },
  IN_USE: { bg: "#e0f2fe", text: "#0369a1", label: "In Use", dot: "#0369a1" },
  UNDER_MAINTENANCE: { bg: "#fef3c7", text: "#b45309", label: "Under Maintenance", dot: "#d97706" },
  DECOMMISSIONED: { bg: "#fee2e2", text: "#b91c1c", label: "Decommissioned", dot: "#dc2626" },
};

const STATUS_FILTER_OPTIONS = [
  { key: "", label: "All" },
  { key: "AVAILABLE", label: "Available" },
  { key: "IN_USE", label: "In Use" },
  { key: "UNDER_MAINTENANCE", label: "Maintenance" },
  { key: "DECOMMISSIONED", label: "Decommissioned" },
];

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
  const [statusFilter, setStatusFilter] = useState("");
  const [updatingId, setUpdatingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [editingEquipment, setEditingEquipment] = useState(null);
  const [viewMode, setViewMode] = useState("cards"); // "cards" | "table"

  useEffect(() => {
    setSearchPlaceholder("Search by instrument name, model, specs, tags...");
  }, [setSearchPlaceholder]);

  const loadColleges = useCallback(async () => {
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

  const categories = Array.from(
    new Set(equipments.map((e) => e.category).filter(Boolean))
  );

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = isStaff
    ? user?.collegeName || "Your College"
    : selectedCollegeId
      ? collegeMap[selectedCollegeId] || "Selected College"
      : "All Colleges";

  const filteredEquipments = equipments.filter((item) => {
    if (!isStaff && selectedCollegeId) {
      const itemCollegeId = item.collegeId || item.institutionId;
      if (String(itemCollegeId) !== String(selectedCollegeId)) return false;
    }
    if (categoryFilter && item.category !== categoryFilter) return false;
    if (statusFilter && item.status !== statusFilter) return false;
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

        <div className="admin-header-actions" style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {/* View Toggle */}
          <div style={{ display: "flex", background: "#f1f5f9", padding: "3px", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
            <button
              type="button"
              className={`btn-view-toggle ${viewMode === "cards" ? "active" : ""}`}
              onClick={() => setViewMode("cards")}
              title="Cards View"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "5px",
                padding: "6px 12px",
                borderRadius: "6px",
                border: "none",
                background: viewMode === "cards" ? "#ffffff" : "transparent",
                color: viewMode === "cards" ? "#0f172a" : "#64748b",
                fontWeight: viewMode === "cards" ? 600 : 500,
                fontSize: "0.8rem",
                cursor: "pointer",
                boxShadow: viewMode === "cards" ? "0 1px 3px rgba(0,0,0,0.08)" : "none",
              }}
            >
              <FiGrid size={14} /> Cards
            </button>
            <button
              type="button"
              className={`btn-view-toggle ${viewMode === "table" ? "active" : ""}`}
              onClick={() => setViewMode("table")}
              title="Table View"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "5px",
                padding: "6px 12px",
                borderRadius: "6px",
                border: "none",
                background: viewMode === "table" ? "#ffffff" : "transparent",
                color: viewMode === "table" ? "#0f172a" : "#64748b",
                fontWeight: viewMode === "table" ? 600 : 500,
                fontSize: "0.8rem",
                cursor: "pointer",
                boxShadow: viewMode === "table" ? "0 1px 3px rgba(0,0,0,0.08)" : "none",
              }}
            >
              <FiList size={14} /> Table
            </button>
          </div>

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

      {/* Status Filter Chips */}
      <div className="eq-status-filter-bar">
        <span className="eq-status-filter-label">Status:</span>
        {STATUS_FILTER_OPTIONS.map((opt) => {
          const conf = opt.key ? STATUS_CONFIG[opt.key] : null;
          const isActive = statusFilter === opt.key;
          return (
            <button
              key={opt.key}
              type="button"
              className={`eq-status-chip${isActive ? " active" : ""}`}
              onClick={() => setStatusFilter(opt.key)}
            >
              {conf && <span className="chip-dot" style={{ background: isActive ? "#fff" : conf.dot }} />}
              {opt.label}
            </button>
          );
        })}
      </div>

      {/* Content */}
      {loading ? (
        <div className="admin-loading-state">Loading equipment catalog...</div>
      ) : filteredEquipments.length === 0 ? (
        <div className="admin-empty-card">
          <FiInbox size={36} style={{ color: "#9ca3af", marginBottom: "12px" }} />
          <h3>No equipment found</h3>
          <p>
            {isStaff || selectedCollegeId
              ? `No instruments found matching your criteria in ${currentCollegeName}.`
              : "No equipment records match your search criteria."}
          </p>
        </div>
      ) : (
        <>
          <p className="eq-count-badge">
            Showing <strong>{filteredEquipments.length}</strong> instrument{filteredEquipments.length !== 1 ? "s" : ""}
          </p>

          {/* Cards View (Default) */}
          {viewMode === "cards" ? (
            <div className="eq-card-list">
              {filteredEquipments.map((item, idx) => (
                <EquipmentCard
                  key={item.id}
                  equipment={item}
                  mode="staff"
                  collegeMap={collegeMap}
                  index={idx}
                  isUpdating={updatingId === item.id}
                  isDeleting={deletingId === item.id}
                  onStatusChange={handleStatusChange}
                  onEdit={(eq) => setEditingEquipment(eq)}
                  onDelete={(eq) => handleDeleteEquipment(eq.id, eq.name)}
                  onViewSchedule={(eq) => navigate(`/dashboard/equipment/${eq.id}/schedule`)}
                />
              ))}
            </div>
          ) : (
            /* Table View */
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Instrument Name</th>
                    <th>Category / Model</th>
                    <th>Location</th>
                    <th>Max Hours</th>
                    <th>Change Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredEquipments.map((item) => {
                    const itemCollegeId = item.collegeId || item.institutionId;
                    const itemCollegeName = isStaff
                      ? user?.collegeName || "Your College"
                      : itemCollegeId
                        ? collegeMap[itemCollegeId] || "Affiliated College"
                        : "All Colleges";

                    const statusConf = STATUS_CONFIG[item.status] || {
                      bg: "#f3f4f6", text: "#374151", label: item.status, dot: "#9ca3af",
                    };
                    const isUpdating = updatingId === item.id;
                    const isDeleting = deletingId === item.id;

                    return (
                      <tr key={item.id}>
                        <td>
                          <span className="eq-table-status">
                            <span className="eq-table-status-dot" style={{ background: statusConf.dot }} />
                            <span style={{ color: statusConf.text, fontWeight: 500 }}>{statusConf.label}</span>
                          </span>
                        </td>
                        <td>
                          <strong style={{ display: "block", color: "var(--text-primary)" }}>{item.name}</strong>
                          <span style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>{itemCollegeName}</span>
                          {item.requiresApproval && (
                            <span style={{ display: "inline-flex", alignItems: "center", gap: "3px", fontSize: "0.73rem", color: "#b45309", marginTop: "2px" }}>
                              <FiShield size={11} /> Approval required
                            </span>
                          )}
                        </td>
                        <td>
                          <div style={{ lineHeight: 1.4 }}>
                            {item.category && (
                              <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.82rem", color: "var(--text-secondary)" }}>
                                <FiFolder size={13} /> {item.category}
                              </span>
                            )}
                            {item.model && (
                              <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.78rem", color: "var(--text-muted)" }}>
                                <FiTag size={11} /> {item.model}
                              </span>
                            )}
                            {item.manufacturer && (
                              <span style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.75rem", color: "var(--text-muted)" }}>
                                <FiCpu size={11} /> {item.manufacturer}
                              </span>
                            )}
                          </div>
                        </td>
                        <td>
                          {item.location ? (
                            <span style={{ display: "inline-flex", alignItems: "center", gap: "4px", fontSize: "0.82rem", color: "var(--text-secondary)" }}>
                              <FiMapPin size={13} /> {item.location}
                            </span>
                          ) : <span style={{ color: "var(--text-muted)" }}>—</span>}
                        </td>
                        <td>
                          <span style={{ display: "inline-flex", alignItems: "center", gap: "4px", fontSize: "0.82rem", color: "var(--text-secondary)" }}>
                            <FiClock size={13} /> {item.maxBookingHours || 8}h
                          </span>
                        </td>
                        <td>
                          <select
                            className="equipment-status-select"
                            value={item.status}
                            disabled={isUpdating}
                            style={{
                              backgroundColor: statusConf.bg,
                              color: statusConf.text,
                              borderColor: statusConf.text,
                              fontSize: "0.78rem",
                              padding: "4px 8px",
                              borderRadius: "6px",
                              border: "1.5px solid",
                              cursor: "pointer",
                            }}
                            onChange={(e) => handleStatusChange(item, e.target.value)}
                          >
                            <option value="AVAILABLE">Available</option>
                            <option value="IN_USE">In Use</option>
                            <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                            <option value="DECOMMISSIONED">Decommissioned</option>
                          </select>
                        </td>
                        <td>
                          <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                            <button
                              type="button"
                              className="admin-btn-secondary"
                              onClick={() => navigate(`/dashboard/equipment/${item.id}/schedule`)}
                              title="View slots & availability"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px", padding: "5px 10px", fontSize: "0.78rem" }}
                            >
                              <FiCalendar size={13} /> Slots
                            </button>
                            <button
                              type="button"
                              className="admin-btn-primary"
                              onClick={() => setEditingEquipment(item)}
                              title="Edit equipment specifications"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px", padding: "5px 10px", fontSize: "0.78rem" }}
                            >
                              <FiEdit3 size={13} /> Edit
                            </button>
                            <button
                              type="button"
                              className="admin-btn-danger"
                              disabled={isDeleting}
                              onClick={() => handleDeleteEquipment(item.id, item.name)}
                              title="Delete equipment from catalog"
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px", padding: "5px 10px", fontSize: "0.78rem" }}
                            >
                              <FiTrash2 size={13} /> {isDeleting ? "..." : "Delete"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
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
