import { useEffect, useState, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  getEquipmentList,
  getAllEquipmentTags,
  canCreateEquipment,
  canManageEquipment,
  EQUIPMENT_STATUSES,
} from "../../services/equipmentService";
import "./equipment.css";

export default function EquipmentCatalogPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [equipmentList, setEquipmentList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Search & Filter State
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("ALL");
  const [selectedCategory, setSelectedCategory] = useState("ALL");
  const [selectedTag, setSelectedTag] = useState("ALL");
  const [approvalFilter, setApprovalFilter] = useState("ALL");
  const [tagOptions, setTagOptions] = useState([]);
  const [viewMode, setViewMode] = useState("grid"); // "grid" | "table"

  const userCanCreate = canCreateEquipment(user?.role);
  const userCanManage = canManageEquipment(user?.role);

  function reloadEquipment() {
    setLoading(true);
    setError(null);
    getEquipmentList()
      .then((data) => {
        setEquipmentList(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        console.error("Failed to fetch equipment catalog:", err);
        const message =
          err.response?.data?.message ||
          err.response?.data?.error ||
          "Unable to load equipment catalog. Please check your connection and try again.";
        setError(message);
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    let ignore = false;

    getEquipmentList()
      .then((data) => {
        if (!ignore) {
          setEquipmentList(Array.isArray(data) ? data : []);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to fetch equipment catalog:", err);
          const message =
            err.response?.data?.message ||
            err.response?.data?.error ||
            "Unable to load equipment catalog. Please check your connection and try again.";
          setError(message);
          setLoading(false);
        }
      });

    // Also fetch distinct tags from the API
    getAllEquipmentTags()
      .then((tags) => {
        if (!ignore && Array.isArray(tags)) {
          setTagOptions(tags);
        }
      })
      .catch(() => {
        // Fallback gracefully to tags from equipmentList
      });

    return () => {
      ignore = true;
    };
  }, []);

  // Dynamically extract unique categories from the equipment data
  const availableCategories = useMemo(() => {
    const set = new Set();
    equipmentList.forEach((item) => {
      if (item.category && item.category.trim()) {
        set.add(item.category.trim());
      }
    });
    return Array.from(set).sort();
  }, [equipmentList]);

  // Dynamically extract unique tags from tagOptions and equipmentList
  const availableTags = useMemo(() => {
    const set = new Set(tagOptions);
    equipmentList.forEach((item) => {
      if (Array.isArray(item.tags)) {
        item.tags.forEach((t) => {
          if (t && t.trim()) set.add(t.trim());
        });
      }
    });
    return Array.from(set).sort();
  }, [tagOptions, equipmentList]);

  // Client-side filtering over the equipment records
  const filteredEquipment = useMemo(() => {
    return equipmentList.filter((item) => {
      // 1. Search Query (name, category, model, manufacturer, and tags)
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const nameMatch = (item.name || "").toLowerCase().includes(query);
        const categoryMatch = (item.category || "").toLowerCase().includes(query);
        const modelMatch = (item.model || "").toLowerCase().includes(query);
        const manufacturerMatch = (item.manufacturer || "").toLowerCase().includes(query);
        const tagMatch =
          Array.isArray(item.tags) &&
          item.tags.some((t) => (t || "").toLowerCase().includes(query));

        if (!nameMatch && !categoryMatch && !modelMatch && !manufacturerMatch && !tagMatch) {
          return false;
        }
      }

      // 2. Status filter
      if (selectedStatus !== "ALL" && item.status !== selectedStatus) {
        return false;
      }

      // 3. Category filter
      if (
        selectedCategory !== "ALL" &&
        (item.category || "").trim().toLowerCase() !== selectedCategory.toLowerCase()
      ) {
        return false;
      }

      // 4. Tag filter
      if (selectedTag !== "ALL") {
        const hasTag =
          Array.isArray(item.tags) &&
          item.tags.some((t) => (t || "").toLowerCase() === selectedTag.toLowerCase());
        if (!hasTag) {
          return false;
        }
      }

      // 5. Approval policy filter
      if (approvalFilter === "REQUIRES_APPROVAL" && !item.requiresApproval) {
        return false;
      }
      if (approvalFilter === "DIRECT_BOOKING" && item.requiresApproval) {
        return false;
      }

      return true;
    });
  }, [equipmentList, searchQuery, selectedStatus, selectedCategory, selectedTag, approvalFilter]);

  const hasActiveFilters =
    searchQuery.trim() !== "" ||
    selectedStatus !== "ALL" ||
    selectedCategory !== "ALL" ||
    selectedTag !== "ALL" ||
    approvalFilter !== "ALL";

  function clearFilters() {
    setSearchQuery("");
    setSelectedStatus("ALL");
    setSelectedCategory("ALL");
    setSelectedTag("ALL");
    setApprovalFilter("ALL");
  }

  function getStatusBadge(status) {
    const match = EQUIPMENT_STATUSES.find((s) => s.value === status);
    const badgeClass = match ? match.badgeClass : "status-retired";
    const label = match ? match.label : status || "Unknown";
    return <span className={`status-badge ${badgeClass}`}>{label}</span>;
  }

  return (
    <div className="equipment-page-container">
      {/* Breadcrumb */}
      <nav className="equipment-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <span className="current">Equipment Catalog</span>
      </nav>

      {/* Header */}
      <header className="equipment-header">
        <div>
          <h1>Laboratory Equipment Catalog</h1>
          <p>
            Browse, search, and inspect laboratory equipment and instrument records.
          </p>
        </div>

        {userCanCreate && (
          <div className="equipment-header-actions">
            <Link to="/dashboard/equipment/new" className="btn-primary">
              <span aria-hidden="true">➕</span>
              <span>Add Equipment</span>
            </Link>
          </div>
        )}
      </header>

      {/* Search & Filter Toolbar */}
      <section className="equipment-toolbar" aria-label="Catalog search and filters">
        <div className="equipment-search-row">
          <div className="equipment-search-box">
            <span className="equipment-search-icon" aria-hidden="true">🔍</span>
            <input
              type="text"
              className="equipment-search-input"
              placeholder="Search by equipment name, category, model, or manufacturer..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Search equipment"
            />
          </div>
        </div>

        <div className="equipment-filters-row">
          {/* Status Filter */}
          <select
            className="equipment-select"
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value)}
            aria-label="Filter by equipment status"
          >
            <option value="ALL">All Statuses</option>
            {EQUIPMENT_STATUSES.map((st) => (
              <option key={st.value} value={st.value}>
                {st.label}
              </option>
            ))}
          </select>

          {/* Category Filter */}
          <select
            className="equipment-select"
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            aria-label="Filter by category"
          >
            <option value="ALL">All Categories</option>
            {availableCategories.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>

          {/* Tag Filter */}
          <select
            className="equipment-select"
            value={selectedTag}
            onChange={(e) => setSelectedTag(e.target.value)}
            aria-label="Filter by tag"
          >
            <option value="ALL">All Tags</option>
            {availableTags.map((tag) => (
              <option key={tag} value={tag}>
                #{tag}
              </option>
            ))}
          </select>

          {/* Approval Filter */}
          <select
            className="equipment-select"
            value={approvalFilter}
            onChange={(e) => setApprovalFilter(e.target.value)}
            aria-label="Filter by approval requirement"
          >
            <option value="ALL">All Approval Policies</option>
            <option value="REQUIRES_APPROVAL">Requires Manager Approval</option>
            <option value="DIRECT_BOOKING">Direct Confirmation</option>
          </select>

          {hasActiveFilters && (
            <button
              type="button"
              className="btn-clear-filters"
              onClick={clearFilters}
            >
              Reset Filters ✕
            </button>
          )}
        </div>

        <div className="equipment-toolbar-meta">
          <span>
            Showing <strong>{filteredEquipment.length}</strong> of{" "}
            <strong>{equipmentList.length}</strong> equipment items
          </span>

          <div className="view-mode-toggle" role="group" aria-label="View mode">
            <button
              type="button"
              className={`view-mode-btn ${viewMode === "grid" ? "active" : ""}`}
              onClick={() => setViewMode("grid")}
              aria-pressed={viewMode === "grid"}
            >
              ⊞ Grid
            </button>
            <button
              type="button"
              className={`view-mode-btn ${viewMode === "table" ? "active" : ""}`}
              onClick={() => setViewMode("table")}
              aria-pressed={viewMode === "table"}
            >
              ☰ Table
            </button>
          </div>
        </div>
      </section>

      {/* Main Content Area */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading equipment catalog...</p>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Equipment</h3>
          <p>{error}</p>
          <button type="button" className="btn-primary" onClick={reloadEquipment}>
            Retry Loading
          </button>
        </div>
      ) : equipmentList.length === 0 ? (
        <div className="equipment-empty-state">
          <span className="state-icon" aria-hidden="true">🔬</span>
          <h3>No Equipment Registered Yet</h3>
          <p>
            The laboratory equipment inventory is currently empty.
            {userCanCreate
              ? " As an authorized staff member, you can add the first equipment record."
              : " Equipment will appear here once registered by laboratory managers."}
          </p>
          {userCanCreate && (
            <Link to="/dashboard/equipment/new" className="btn-primary">
              Add First Equipment
            </Link>
          )}
        </div>
      ) : filteredEquipment.length === 0 ? (
        <div className="equipment-empty-state">
          <span className="state-icon" aria-hidden="true">🔎</span>
          <h3>No Equipment Matches Your Filters</h3>
          <p>Try adjusting your search query, status filter, or category selection.</p>
          <button type="button" className="btn-secondary" onClick={clearFilters}>
            Clear All Filters
          </button>
        </div>
      ) : viewMode === "grid" ? (
        <div className="equipment-grid">
          {filteredEquipment.map((item) => (
            <article className="equipment-card" key={item.id}>
              <div>
                <div className="equipment-card-header">
                  {item.category ? (
                    <span className="category-pill">{item.category}</span>
                  ) : (
                    <span className="category-pill" style={{ opacity: 0.6 }}>Uncategorized</span>
                  )}
                  {getStatusBadge(item.status)}
                </div>

                <h3 className="equipment-card-title">{item.name}</h3>

                <div className="equipment-card-meta">
                  {(item.model || item.manufacturer) && (
                    <div className="equipment-spec-row">
                      <span className="equipment-spec-label">Model:</span>
                      <span className="equipment-spec-value">
                        {[item.manufacturer, item.model].filter(Boolean).join(" • ")}
                      </span>
                    </div>
                  )}

                  {item.institutionId != null && (
                    <div className="equipment-spec-row">
                      <span className="equipment-spec-label">Institution:</span>
                      <span className="equipment-spec-value">#{item.institutionId}</span>
                    </div>
                  )}

                  {item.departmentId != null && (
                    <div className="equipment-spec-row">
                      <span className="equipment-spec-label">Department:</span>
                      <span className="equipment-spec-value">#{item.departmentId}</span>
                    </div>
                  )}

                  <div className="equipment-badges-row">
                    <span className="spec-badge">
                      ⏱ Max {item.maxBookingHours || 8}h
                    </span>
                    {item.requiresApproval ? (
                      <span className="spec-badge approval-required">
                        🛡 Manager Approval
                      </span>
                    ) : (
                      <span className="spec-badge">
                        ⚡ Direct Booking
                      </span>
                    )}
                  </div>

                  {Array.isArray(item.tags) && item.tags.length > 0 && (
                    <div className="equipment-tags-container">
                      {item.tags.map((tag) => (
                        <span key={tag} className="equipment-tag-pill">
                          #{tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="equipment-card-footer">
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => navigate(`/dashboard/equipment/${item.id}`)}
                >
                  View Details →
                </button>

                {userCanManage && (
                  <button
                    type="button"
                    className="btn-outline-primary"
                    onClick={() => navigate(`/dashboard/equipment/${item.id}/edit`)}
                    aria-label={`Edit ${item.name}`}
                  >
                    ✏️ Edit
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="equipment-table-container">
          <table className="equipment-table">
            <thead>
              <tr>
                <th>Equipment Name</th>
                <th>Category</th>
                <th>Model / Manufacturer</th>
                <th>Status</th>
                <th>Max Hours</th>
                <th>Approval</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredEquipment.map((item) => (
                <tr key={item.id}>
                  <td>
                    <Link
                      to={`/dashboard/equipment/${item.id}`}
                      className="equipment-table-title"
                    >
                      {item.name}
                    </Link>
                    {Array.isArray(item.tags) && item.tags.length > 0 && (
                      <div className="equipment-tags-container" style={{ marginTop: "4px" }}>
                        {item.tags.map((tag) => (
                          <span key={tag} className="equipment-tag-pill">
                            #{tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </td>
                  <td>
                    {item.category ? (
                      <span className="category-pill">{item.category}</span>
                    ) : (
                      <span style={{ color: "var(--text-muted)", fontSize: "12px" }}>—</span>
                    )}
                  </td>
                  <td>
                    {[item.manufacturer, item.model].filter(Boolean).join(" • ") || "—"}
                  </td>
                  <td>{getStatusBadge(item.status)}</td>
                  <td>{item.maxBookingHours || 8}h</td>
                  <td>
                    {item.requiresApproval ? (
                      <span className="spec-badge approval-required">Required</span>
                    ) : (
                      <span className="spec-badge">Auto</span>
                    )}
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "6px" }}>
                      <Link
                        to={`/dashboard/equipment/${item.id}`}
                        className="btn-secondary"
                        style={{ padding: "6px 12px", fontSize: "12px" }}
                      >
                        Details
                      </Link>
                      {userCanManage && (
                        <Link
                          to={`/dashboard/equipment/${item.id}/edit`}
                          className="btn-outline-primary"
                          style={{ padding: "6px 10px", fontSize: "12px" }}
                        >
                          Edit
                        </Link>
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
  );
}
