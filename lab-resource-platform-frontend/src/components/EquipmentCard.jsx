import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  FiCheckCircle,
  FiClock,
  FiMapPin,
  FiCalendar,
  FiBookmark,
  FiMessageSquare,
  FiMail,
  FiMoreVertical,
  FiCheck,
  FiActivity,
  FiCpu,
  FiCompass,
  FiEye,
  FiShield,
  FiEdit3,
  FiTrash2,
  FiSend,
  FiChevronDown,
} from "react-icons/fi";
import { useToast } from "../context/ToastContext";

// Distinct background styles for icon avatars
const AVATAR_PALETTES = [
  { bg: "#7c2d12", iconColor: "#fdba74" }, // amber/rust
  { bg: "#3b0764", iconColor: "#d8b4fe" }, // deep purple
  { bg: "#064e3b", iconColor: "#6ee7b7" }, // deep emerald
  { bg: "#1e3a8a", iconColor: "#93c5fd" }, // deep blue
  { bg: "#831843", iconColor: "#f9a8d4" }, // deep rose
  { bg: "#134e4a", iconColor: "#5eead4" }, // deep teal
];

// Pick an appropriate icon based on category or name
function getEquipmentIcon(name = "", category = "") {
  const text = `${name} ${category}`.toLowerCase();
  if (text.includes("oscilloscope") || text.includes("signal") || text.includes("wave")) {
    return <FiActivity size={22} />;
  }
  if (text.includes("gpu") || text.includes("cluster") || text.includes("compute") || text.includes("server") || text.includes("cpu")) {
    return <FiCpu size={22} />;
  }
  if (text.includes("microscope") || text.includes("dna") || text.includes("sequencer") || text.includes("optics") || text.includes("tem") || text.includes("sem")) {
    return <FiEye size={22} />;
  }
  return <FiCompass size={22} />;
}

export default function EquipmentCard({
  equipment,
  mode = "student", // "student" | "staff" | "discovery"
  collegeMap = {},
  onBook,
  onViewSchedule,
  onRequestAccess,
  onEdit,
  onDelete,
  onStatusChange,
  isUpdating = false,
  isDeleting = false,
  index = 0,
}) {
  const toast = useToast();
  const navigate = useNavigate();
  const [bookmarked, setBookmarked] = useState(false);
  const [showStatusMenu, setShowStatusMenu] = useState(false);

  function handleScheduleNavigation() {
    if (onViewSchedule) {
      onViewSchedule(equipment);
    } else {
      navigate(`/dashboard/equipment/${id}/schedule`);
    }
  }

  // Normalize fields across student/staff/discovery data schemas
  const id = equipment.id || equipment.listingId;
  const name = equipment.equipmentName || equipment.name || "Laboratory Instrument";
  const collegeId = equipment.ownerInstitutionId || equipment.institutionId || equipment.collegeId;
  const collegeName =
    equipment.ownerInstitutionName ||
    equipment.institutionName ||
    equipment.collegeName ||
    (collegeId && collegeMap[collegeId]) ||
    "Affiliated Institution";

  const category = equipment.categoryName || equipment.category || "";
  const model = equipment.model || "";
  const manufacturer = equipment.manufacturer || "";
  const location = equipment.location || "";
  const description = equipment.description || "";
  const specifications = equipment.specifications || "";
  const tags = equipment.tags || "";
  const status = equipment.status || (equipment.isAvailable ? "AVAILABLE" : "AVAILABLE");
  const maxHours = equipment.maxBookingDurationHours || equipment.maxBookingHours || equipment.maxUsageHours || 8;
  const hourlyRate = equipment.feePerHour ?? equipment.hourlyFee ?? equipment.hourlyCost ?? 250;
  const requiresApproval = equipment.requiresApproval ?? true;

  const isBookable = status === "AVAILABLE" || status === "IN_USE";

  // Avatar color palette
  const avatarStyle = AVATAR_PALETTES[index % AVATAR_PALETTES.length];

  // Parse tags / specifications into checkmark pills
  let specPills = [];
  if (specifications) {
    specPills = specifications
      .split(/[,;•\n]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && s.length <= 40)
      .slice(0, 4);
  }
  if (specPills.length === 0 && tags) {
    specPills = tags
      .split(/[,;•\n]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && s.length <= 40)
      .slice(0, 4);
  }
  // Fallbacks if no specs or tags
  if (specPills.length === 0) {
    if (category) specPills.push(category);
    if (model) specPills.push(`Model: ${model}`);
    if (manufacturer) specPills.push(`Mfr: ${manufacturer}`);
  }

  // Tag color rotation
  const tagColorClasses = ["green", "amber", "blue", "purple"];

  function handleBookmarkToggle() {
    setBookmarked(!bookmarked);
    toast.success(!bookmarked ? `"${name}" saved to bookmarks` : `Removed "${name}" from bookmarks`);
  }

  function handleContact() {
    toast.info(`Contacting laboratory administrator for ${name}...`);
  }

  function handleMail() {
    const subject = encodeURIComponent(`Inquiry regarding ${name}`);
    const body = encodeURIComponent(`Hello,\n\nI am inquiring about laboratory equipment "${name}" (ID #${id}) at ${collegeName}.\n\nThank you.`);
    window.location.href = `mailto:lab-support@platform.edu?subject=${subject}&body=${body}`;
  }

  return (
    <article className="eq-card" data-equipment-id={id}>
      {/* ── Top Header Section ── */}
      <div className="eq-card-top">
        {/* Left: Icon Avatar */}
        <div
          className="eq-card-icon"
          style={{
            backgroundColor: avatarStyle.bg,
            color: avatarStyle.iconColor,
            borderRadius: "50%",
            boxShadow: "0 2px 8px rgba(0,0,0,0.12)",
          }}
          title={category || name}
        >
          {getEquipmentIcon(name, category)}
        </div>

        {/* Center: Identity & Status */}
        <div className="eq-card-identity">
          <div className="eq-card-name-row">
            <h3 className="eq-card-name">{name}</h3>
            <span title="Verified Institution Equipment" style={{ display: "inline-flex", color: "#16a34a" }}>
              <FiCheckCircle size={16} />
            </span>
          </div>

          <div className="eq-card-meta-line">
            <strong>{collegeName}</strong>
            {category && <span> · {category}</span>}
            {model && <span> · {model}</span>}
          </div>

          <div
            className={`eq-card-avail-line ${
              status === "AVAILABLE"
                ? ""
                : status === "IN_USE"
                ? "inuse"
                : status === "UNDER_MAINTENANCE"
                ? "unavailable"
                : "decommissioned"
            }`}
          >
            <span
              style={{
                display: "inline-block",
                width: 7,
                height: 7,
                borderRadius: "50%",
                backgroundColor:
                  status === "AVAILABLE"
                    ? "#16a34a"
                    : status === "IN_USE"
                    ? "#0284c7"
                    : status === "UNDER_MAINTENANCE"
                    ? "#d97706"
                    : "#dc2626",
              }}
            />
            {mode === "discovery"
              ? "Available for sharing"
              : status === "AVAILABLE"
              ? "Available for booking"
              : status === "IN_USE"
              ? "Currently In Use"
              : status === "UNDER_MAINTENANCE"
              ? "Under Maintenance"
              : "Decommissioned"}
          </div>
        </div>

        {/* Right: Rate, Session Duration & Quick Icon Actions */}
        <div className="eq-card-right">
          <div className="eq-card-rate-block">
            <span className="eq-card-rate-label">{maxHours} hrs max per session</span>
            <div className="eq-card-rate-value">₹{hourlyRate}<span style={{ fontSize: "0.75rem", fontWeight: 500, color: "#64748b" }}>/hr</span></div>
          </div>

          <div className="eq-card-icon-actions">
            {location && (
              <button
                type="button"
                className="eq-card-icon-btn"
                title={`Location: ${location}`}
                onClick={() => toast.info(`Location: ${location} (${collegeName})`)}
              >
                <FiMapPin size={15} />
              </button>
            )}
            <button
              type="button"
              className="eq-card-icon-btn"
              title="View Schedule & Slots Calendar"
              onClick={handleScheduleNavigation}
            >
              <FiCalendar size={15} />
            </button>
          </div>
        </div>
      </div>

      {/* ── Feature Tag Chips ── */}
      {specPills.length > 0 && (
        <div className="eq-card-tags">
          {specPills.map((pill, idx) => {
            const colorClass = tagColorClasses[idx % tagColorClasses.length];
            return (
              <span key={idx} className={`eq-card-tag ${colorClass}`}>
                <FiCheck size={12} /> {pill}
              </span>
            );
          })}
          {requiresApproval && mode !== "discovery" && (
            <span className="eq-card-approval-badge">
              <FiShield size={12} /> Manager Approval Required
            </span>
          )}
        </div>
      )}

      {/* ── Divider ── */}
      <div className="eq-card-divider" />

      {/* ── About This Resource ── */}
      <div className="eq-card-about">
        <h4 className="eq-card-about-label">About this resource</h4>
        <p className="eq-card-about-text">
          {description ||
            `High-precision laboratory equipment maintained by ${collegeName}. Available for academic research, coursework validation, and cross-disciplinary experimentation.`}
        </p>
      </div>

      {/* ── Footer Actions ── */}
      <div className="eq-card-footer">
        {/* Left: Bookmark & Message Icons */}
        <div className="eq-card-footer-left">
          <button
            type="button"
            className="eq-card-icon-btn"
            style={{ color: bookmarked ? "#2563eb" : undefined, borderColor: bookmarked ? "#93c5fd" : undefined }}
            title={bookmarked ? "Remove Bookmark" : "Bookmark this instrument"}
            onClick={handleBookmarkToggle}
          >
            <FiBookmark size={15} style={{ fill: bookmarked ? "#2563eb" : "none" }} />
          </button>
          <button
            type="button"
            className="eq-card-icon-btn"
            title="Ask a question about this equipment"
            onClick={handleContact}
          >
            <FiMessageSquare size={15} />
          </button>
        </div>

        {/* Right: Actions */}
        <div className="eq-card-footer-right">
          {/* Staff specific status modifier */}
          {mode === "staff" && (
            <select
              className="eq-card-status-select"
              value={status}
              disabled={isUpdating}
              style={{
                backgroundColor:
                  status === "AVAILABLE"
                    ? "#dcfce7"
                    : status === "IN_USE"
                    ? "#e0f2fe"
                    : status === "UNDER_MAINTENANCE"
                    ? "#fef3c7"
                    : "#fee2e2",
                color:
                  status === "AVAILABLE"
                    ? "#15803d"
                    : status === "IN_USE"
                    ? "#0369a1"
                    : status === "UNDER_MAINTENANCE"
                    ? "#b45309"
                    : "#b91c1c",
                borderColor: "currentColor",
              }}
              onChange={(e) => onStatusChange && onStatusChange(equipment, e.target.value)}
            >
              <option value="AVAILABLE">Available</option>
              <option value="IN_USE">In Use</option>
              <option value="UNDER_MAINTENANCE">Under Maintenance</option>
              <option value="DECOMMISSIONED">Decommissioned</option>
            </select>
          )}

          {/* Discovery mode: Request and book */}
          {mode === "discovery" && (
            <button
              type="button"
              className="eq-btn-primary"
              onClick={() => onRequestAccess && onRequestAccess(equipment)}
            >
              <FiSend size={14} /> Request and book
            </button>
          )}

          {/* Student mode: Book this equipment */}
          {mode === "student" && (
            <button
              type="button"
              className="eq-btn-primary"
              disabled={!isBookable}
              onClick={() => onBook && onBook(equipment)}
            >
              <FiCalendar size={14} /> {isBookable ? "Book This Equipment" : "Unavailable"}
            </button>
          )}

          {/* View Schedule & Availability Calendar */}
          <button
            type="button"
            className="eq-btn-secondary"
            onClick={handleScheduleNavigation}
          >
            View schedule
          </button>

          {/* Staff Edit & Delete */}
          {mode === "staff" && (
            <>
              <button
                type="button"
                className="eq-btn-secondary"
                title="Edit equipment specifications"
                onClick={() => onEdit && onEdit(equipment)}
              >
                <FiEdit3 size={14} /> Edit
              </button>
              <button
                type="button"
                className="eq-btn-danger"
                disabled={isDeleting}
                title="Delete equipment from catalog"
                onClick={() => onDelete && onDelete(equipment)}
              >
                <FiTrash2 size={14} /> {isDeleting ? "..." : "Delete"}
              </button>
            </>
          )}

          {/* Extra utility icons */}
          <button
            type="button"
            className="eq-card-icon-btn"
            title="Email lab coordinator"
            onClick={handleMail}
          >
            <FiMail size={15} />
          </button>
          <button
            type="button"
            className="eq-card-icon-btn"
            title="More Options"
            onClick={() => toast.info(`Instrument ID: #${id} · Cataloged under ${collegeName}`)}
          >
            <FiMoreVertical size={15} />
          </button>
        </div>
      </div>
    </article>
  );
}
