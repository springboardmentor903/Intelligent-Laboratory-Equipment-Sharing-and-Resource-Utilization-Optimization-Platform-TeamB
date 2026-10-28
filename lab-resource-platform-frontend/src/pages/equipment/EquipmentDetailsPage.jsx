import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import {
  getEquipmentById,
  canManageEquipment,
  EQUIPMENT_STATUSES,
} from "../../services/equipmentService";
import "./equipment.css";

export default function EquipmentDetailsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [equipment, setEquipment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isNotFound, setIsNotFound] = useState(false);

  const userCanManage = canManageEquipment(user?.role);

  function reloadDetails() {
    setLoading(true);
    setError(null);
    setIsNotFound(false);
    getEquipmentById(id)
      .then((data) => {
        setEquipment(data);
      })
      .catch((err) => {
        console.error("Failed to fetch equipment details:", err);
        if (err.response?.status === 404) {
          setIsNotFound(true);
        } else {
          const msg =
            err.response?.data?.message ||
            err.response?.data?.error ||
            "Unable to load equipment details. Please try again.";
          setError(msg);
        }
      })
      .finally(() => {
        setLoading(false);
      });
  }

  useEffect(() => {
    let ignore = false;

    getEquipmentById(id)
      .then((data) => {
        if (!ignore) {
          setEquipment(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to fetch equipment details:", err);
          if (err.response?.status === 404) {
            setIsNotFound(true);
          } else {
            const msg =
              err.response?.data?.message ||
              err.response?.data?.error ||
              "Unable to load equipment details. Please try again.";
            setError(msg);
          }
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id]);

  function getStatusBadge(status) {
    const match = EQUIPMENT_STATUSES.find((s) => s.value === status);
    const badgeClass = match ? match.badgeClass : "status-retired";
    const label = match ? match.label : status || "Unknown";
    return <span className={`status-badge ${badgeClass}`}>{label}</span>;
  }

  function formatDate(isoString) {
    if (!isoString) return "Not recorded";
    try {
      return new Date(isoString).toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return isoString;
    }
  }

  return (
    <div className="equipment-page-container">
      {/* Breadcrumb */}
      <nav className="equipment-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <Link to="/dashboard/equipment">Equipment Catalog</Link>
        <span className="separator">/</span>
        <span className="current">
          {loading ? "Loading..." : equipment?.name || `Equipment #${id}`}
        </span>
      </nav>

      {/* Loading State */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading equipment specifications...</p>
        </div>
      ) : isNotFound ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">🔍</span>
          <h3>Equipment Not Found</h3>
          <p>
            No laboratory equipment was found with ID <strong>#{id}</strong>. The item
            may have been removed or the link is incorrect.
          </p>
          <Link to="/dashboard/equipment" className="btn-primary">
            ← Return to Equipment Catalog
          </Link>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Details</h3>
          <p>{error}</p>
          <div style={{ display: "flex", gap: "10px", justifyContent: "center" }}>
            <button type="button" className="btn-primary" onClick={reloadDetails}>
              Retry Loading
            </button>
            <Link to="/dashboard/equipment" className="btn-secondary">
              Back to Catalog
            </Link>
          </div>
        </div>
      ) : equipment ? (
        <>
          {/* Hero Section */}
          <section className="equipment-detail-hero">
            <div className="detail-hero-top">
              <div>
                <div className="detail-hero-tags">
                  {equipment.category && (
                    <span className="category-pill">{equipment.category}</span>
                  )}
                  {getStatusBadge(equipment.status)}
                  {Array.isArray(equipment.tags) &&
                    equipment.tags.map((tag) => (
                      <span key={tag} className="equipment-tag-pill">
                        #{tag}
                      </span>
                    ))}
                </div>

                <h1 className="detail-hero-title">{equipment.name}</h1>

                <p className="detail-hero-subtitle">
                  {[equipment.manufacturer, equipment.model].filter(Boolean).join(" • ") ||
                    "Laboratory Instrument"}
                </p>
              </div>

              <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
                <Link to="/dashboard/equipment" className="btn-secondary">
                  ← Back to Catalog
                </Link>

                <Link
                  to={`/dashboard/bookings/calendar?equipmentId=${equipment.id}`}
                  className="btn-secondary"
                >
                  📅 Availability Calendar
                </Link>

                <Link
                  to={`/dashboard/bookings/new?equipmentId=${equipment.id}`}
                  className="btn-primary"
                >
                  ⚡ Book Equipment
                </Link>

                {userCanManage && (
                  <button
                    type="button"
                    className="btn-outline-primary"
                    onClick={() => navigate(`/dashboard/equipment/${equipment.id}/edit`)}
                  >
                    ✏️ Edit
                  </button>
                )}
              </div>
            </div>
          </section>

          {/* Details Specifications Grid */}
          <div className="equipment-detail-grid">
            {/* General Specifications */}
            <article className="detail-section-card">
              <h2 className="detail-section-title">
                <span aria-hidden="true">🔬</span>
                <span>General Specifications</span>
              </h2>

              <div className="detail-item-list">
                <div className="detail-item">
                  <span className="detail-item-label">Equipment ID</span>
                  <span className="detail-item-value">#{equipment.id}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Item Name</span>
                  <span className="detail-item-value">{equipment.name}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Category</span>
                  <span className="detail-item-value">{equipment.category || "Uncategorized"}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Manufacturer</span>
                  <span className="detail-item-value">{equipment.manufacturer || "Not specified"}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Model</span>
                  <span className="detail-item-value">{equipment.model || "Not specified"}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Operational Status</span>
                  <span className="detail-item-value">{getStatusBadge(equipment.status)}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Tags</span>
                  <span className="detail-item-value">
                    {Array.isArray(equipment.tags) && equipment.tags.length > 0 ? (
                      <div className="equipment-tags-container" style={{ marginTop: 0 }}>
                        {equipment.tags.map((tag) => (
                          <span key={tag} className="equipment-tag-pill">
                            #{tag}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span style={{ color: "var(--text-muted)" }}>None</span>
                    )}
                  </span>
                </div>
              </div>
            </article>

            {/* Booking & Scheduling Policy */}
            <article className="detail-section-card">
              <h2 className="detail-section-title">
                <span aria-hidden="true">⏱️</span>
                <span>Reservation & Booking Policy</span>
              </h2>

              <div className="detail-item-list">
                <div className="detail-item">
                  <span className="detail-item-label">Max Booking Duration</span>
                  <span className="detail-item-value">
                    {equipment.maxBookingHours || 8} hours per reservation
                  </span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Approval Policy</span>
                  <span className="detail-item-value">
                    {equipment.requiresApproval ? (
                      <span className="spec-badge approval-required">
                        Manager Approval Required
                      </span>
                    ) : (
                      <span className="spec-badge">
                        Direct Auto-Confirmation
                      </span>
                    )}
                  </span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Institution ID</span>
                  <span className="detail-item-value">
                    {equipment.institutionId != null ? `#${equipment.institutionId}` : "None (Global)"}
                  </span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Department ID</span>
                  <span className="detail-item-value">
                    {equipment.departmentId != null ? `#${equipment.departmentId}` : "Not assigned"}
                  </span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Created At</span>
                  <span className="detail-item-value">{formatDate(equipment.createdAt)}</span>
                </div>

                <div className="detail-item">
                  <span className="detail-item-label">Last Updated</span>
                  <span className="detail-item-value">{formatDate(equipment.updatedAt)}</span>
                </div>
              </div>
            </article>
          </div>

          {/* Module Integration Notice */}
          <aside className="equipment-detail-notice" role="note" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "12px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
              <span aria-hidden="true" style={{ fontSize: 24 }}>📅</span>
              <div>
                <strong>Equipment Scheduling & Reservations</strong>
                <p style={{ margin: "2px 0 0", fontSize: "13px" }}>
                  Inspect live availability slots on the interactive calendar or book a reservation.
                </p>
              </div>
            </div>
            <div>
              <Link
                to={`/dashboard/bookings/calendar?equipmentId=${equipment.id}`}
                className="btn-primary"
                style={{ padding: "8px 16px", fontSize: "13px" }}
              >
                Open Calendar →
              </Link>
            </div>
          </aside>
        </>
      ) : null}
    </div>
  );
}
