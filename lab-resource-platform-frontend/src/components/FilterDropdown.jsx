import { useState, useRef, useEffect } from "react";
import {
  FiSliders,
  FiHome,
  FiFolder,
  FiActivity,
  FiCheck,
  FiChevronDown,
  FiChevronUp,
} from "react-icons/fi";

export default function FilterDropdown({
  colleges = [],
  selectedCollegeId = "",
  onCollegeChange,
  categories = [],
  selectedCategory = "",
  onCategoryChange,
  statuses = [],
  selectedStatus = "",
  onStatusChange,
  showCollege = true,
  showCategory = true,
  showStatus = false,
  statusLabels = {},
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [activeSubmenu, setActiveSubmenu] = useState(null); // 'college' | 'category' | 'status' | null
  const dropdownRef = useRef(null);

  // Close when clicking outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
        setActiveSubmenu(null);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen]);

  // Calculate active filter count
  let activeFilterCount = 0;
  if (showCollege && selectedCollegeId) activeFilterCount++;
  if (showCategory && selectedCategory) activeFilterCount++;
  if (showStatus && selectedStatus && selectedStatus !== "ALL" && selectedStatus !== "") activeFilterCount++;

  const selectedCollegeName =
    colleges.find((c) => String(c.id) === String(selectedCollegeId))?.name || "All Colleges";
  const selectedCategoryName = selectedCategory || "All Categories";
  const selectedStatusName =
    selectedStatus && selectedStatus !== "ALL"
      ? statusLabels[selectedStatus]?.label || statusLabels[selectedStatus] || selectedStatus
      : "All Statuses";

  function handleReset() {
    if (onCollegeChange) onCollegeChange("");
    if (onCategoryChange) onCategoryChange("");
    if (onStatusChange) onStatusChange("");
    setActiveSubmenu(null);
  }

  return (
    <div className="filter-dropdown-container" ref={dropdownRef}>
      <button
        type="button"
        className={`filter-dropdown-trigger ${isOpen ? "active" : ""} ${activeFilterCount > 0 ? "has-active" : ""}`}
        onClick={() => {
          setIsOpen((prev) => !prev);
          if (isOpen) setActiveSubmenu(null);
        }}
        aria-expanded={isOpen}
        aria-haspopup="true"
        title="Open filter menu"
      >
        <span className="filter-icon" style={{ display: "inline-flex", alignItems: "center" }}>
          <FiSliders size={15} />
        </span>
        <span className="filter-label">Filters</span>
        {activeFilterCount > 0 && (
          <span className="filter-count-badge">{activeFilterCount}</span>
        )}
        <span className="filter-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
          {isOpen ? <FiChevronUp size={14} /> : <FiChevronDown size={14} />}
        </span>
      </button>

      {isOpen && (
        <div className="filter-dropdown-menu" role="menu">
          <div className="filter-dropdown-header">
            <span className="filter-dropdown-title">Filter Options</span>
            {activeFilterCount > 0 && (
              <button
                type="button"
                className="filter-reset-btn"
                onClick={handleReset}
              >
                Reset All
              </button>
            )}
          </div>

          <div className="filter-menu-body">
            {/* Filter by College */}
            {showCollege && colleges.length > 0 && (
              <div className="filter-nested-section">
                <button
                  type="button"
                  className={`filter-nested-trigger ${activeSubmenu === "college" ? "open" : ""} ${selectedCollegeId ? "active" : ""}`}
                  onClick={() =>
                    setActiveSubmenu((prev) => (prev === "college" ? null : "college"))
                  }
                >
                  <div className="nested-trigger-left">
                    <span className="nested-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                      <FiHome size={15} />
                    </span>
                    <span className="nested-title">Filter by College:</span>
                  </div>
                  <div className="nested-trigger-right">
                    <span className="nested-current-val">{selectedCollegeName}</span>
                    <span className="nested-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
                      {activeSubmenu === "college" ? <FiChevronUp size={13} /> : <FiChevronDown size={13} />}
                    </span>
                  </div>
                </button>

                {activeSubmenu === "college" && (
                  <div className="filter-nested-submenu">
                    <button
                      type="button"
                      className={`submenu-item ${!selectedCollegeId ? "selected" : ""}`}
                      onClick={() => {
                        onCollegeChange("");
                        setActiveSubmenu(null);
                      }}
                    >
                      <span>All Colleges ({colleges.length})</span>
                      {!selectedCollegeId && (
                        <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                          <FiCheck size={14} color="#2563eb" />
                        </span>
                      )}
                    </button>
                    {colleges.map((col) => {
                      const isSelected = String(col.id) === String(selectedCollegeId);
                      return (
                        <button
                          key={col.id}
                          type="button"
                          className={`submenu-item ${isSelected ? "selected" : ""}`}
                          onClick={() => {
                            onCollegeChange(String(col.id));
                            setActiveSubmenu(null);
                          }}
                        >
                          <span>
                            {col.name} {col.code ? `(${col.code})` : ""}
                          </span>
                          {isSelected && (
                            <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                              <FiCheck size={14} color="#2563eb" />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* Filter by Category */}
            {showCategory && categories.length > 0 && (
              <div className="filter-nested-section">
                <button
                  type="button"
                  className={`filter-nested-trigger ${activeSubmenu === "category" ? "open" : ""} ${selectedCategory ? "active" : ""}`}
                  onClick={() =>
                    setActiveSubmenu((prev) => (prev === "category" ? null : "category"))
                  }
                >
                  <div className="nested-trigger-left">
                    <span className="nested-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                      <FiFolder size={15} />
                    </span>
                    <span className="nested-title">Filter by Category:</span>
                  </div>
                  <div className="nested-trigger-right">
                    <span className="nested-current-val">{selectedCategoryName}</span>
                    <span className="nested-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
                      {activeSubmenu === "category" ? <FiChevronUp size={13} /> : <FiChevronDown size={13} />}
                    </span>
                  </div>
                </button>

                {activeSubmenu === "category" && (
                  <div className="filter-nested-submenu">
                    <button
                      type="button"
                      className={`submenu-item ${!selectedCategory ? "selected" : ""}`}
                      onClick={() => {
                        onCategoryChange("");
                        setActiveSubmenu(null);
                      }}
                    >
                      <span>All Categories ({categories.length})</span>
                      {!selectedCategory && (
                        <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                          <FiCheck size={14} color="#2563eb" />
                        </span>
                      )}
                    </button>
                    {categories.map((cat) => {
                      const isSelected = cat === selectedCategory;
                      return (
                        <button
                          key={cat}
                          type="button"
                          className={`submenu-item ${isSelected ? "selected" : ""}`}
                          onClick={() => {
                            onCategoryChange(cat);
                            setActiveSubmenu(null);
                          }}
                        >
                          <span>{cat}</span>
                          {isSelected && (
                            <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                              <FiCheck size={14} color="#2563eb" />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* Filter by Status (Optional for Bookings / Telemetry) */}
            {showStatus && statuses.length > 0 && (
              <div className="filter-nested-section">
                <button
                  type="button"
                  className={`filter-nested-trigger ${activeSubmenu === "status" ? "open" : ""} ${selectedStatus && selectedStatus !== "ALL" ? "active" : ""}`}
                  onClick={() =>
                    setActiveSubmenu((prev) => (prev === "status" ? null : "status"))
                  }
                >
                  <div className="nested-trigger-left">
                    <span className="nested-icon" style={{ display: "inline-flex", alignItems: "center" }}>
                      <FiActivity size={15} />
                    </span>
                    <span className="nested-title">Filter by Status:</span>
                  </div>
                  <div className="nested-trigger-right">
                    <span className="nested-current-val">{selectedStatusName}</span>
                    <span className="nested-arrow" style={{ display: "inline-flex", alignItems: "center" }}>
                      {activeSubmenu === "status" ? <FiChevronUp size={13} /> : <FiChevronDown size={13} />}
                    </span>
                  </div>
                </button>

                {activeSubmenu === "status" && (
                  <div className="filter-nested-submenu">
                    <button
                      type="button"
                      className={`submenu-item ${!selectedStatus || selectedStatus === "ALL" ? "selected" : ""}`}
                      onClick={() => {
                        onStatusChange("");
                        setActiveSubmenu(null);
                      }}
                    >
                      <span>All Statuses</span>
                      {(!selectedStatus || selectedStatus === "ALL") && (
                        <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                          <FiCheck size={14} color="#2563eb" />
                        </span>
                      )}
                    </button>
                    {statuses.map((st) => {
                      const isSelected = st === selectedStatus;
                      const label = statusLabels[st]?.label || statusLabels[st] || st;
                      return (
                        <button
                          key={st}
                          type="button"
                          className={`submenu-item ${isSelected ? "selected" : ""}`}
                          onClick={() => {
                            onStatusChange(st);
                            setActiveSubmenu(null);
                          }}
                        >
                          <span>{label}</span>
                          {isSelected && (
                            <span className="submenu-check" style={{ display: "inline-flex", alignItems: "center" }}>
                              <FiCheck size={14} color="#2563eb" />
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
