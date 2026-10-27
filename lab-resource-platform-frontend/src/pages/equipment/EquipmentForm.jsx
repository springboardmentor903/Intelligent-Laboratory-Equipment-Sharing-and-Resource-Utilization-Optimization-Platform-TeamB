import { useState } from "react";
import { Link } from "react-router-dom";
import {
  EQUIPMENT_STATUSES,
  COMMON_CATEGORIES,
} from "../../services/equipmentService";

export default function EquipmentForm({
  initialData = {},
  onSubmit,
  isSubmitting = false,
  mode = "create",
  serverError = "",
}) {
  const [formData, setFormData] = useState({
    name: initialData.name || "",
    category: initialData.category || "",
    model: initialData.model || "",
    manufacturer: initialData.manufacturer || "",
    status: initialData.status || "AVAILABLE",
    institutionId:
      initialData.institutionId !== undefined && initialData.institutionId !== null
        ? String(initialData.institutionId)
        : "",
    departmentId:
      initialData.departmentId !== undefined && initialData.departmentId !== null
        ? String(initialData.departmentId)
        : "",
    requiresApproval: Boolean(initialData.requiresApproval),
    maxBookingHours:
      initialData.maxBookingHours !== undefined && initialData.maxBookingHours !== null
        ? Number(initialData.maxBookingHours)
        : 8,
    tagsInput:
      initialData.tags && Array.isArray(initialData.tags)
        ? initialData.tags.join(", ")
        : "",
  });

  const [errors, setErrors] = useState({});

  function handleChange(e) {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));

    if (errors[name]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
    }
  }

  function validate() {
    const newErrors = {};

    // Name validation
    if (!formData.name.trim()) {
      newErrors.name = "Equipment name is required.";
    } else if (formData.name.trim().length > 150) {
      newErrors.name = "Equipment name cannot exceed 150 characters.";
    }

    // Category validation
    if (formData.category && formData.category.length > 100) {
      newErrors.category = "Category cannot exceed 100 characters.";
    }

    // Model validation
    if (formData.model && formData.model.length > 100) {
      newErrors.model = "Model cannot exceed 100 characters.";
    }

    // Manufacturer validation
    if (formData.manufacturer && formData.manufacturer.length > 100) {
      newErrors.manufacturer = "Manufacturer cannot exceed 100 characters.";
    }

    // Max booking hours validation
    const hours = Number(formData.maxBookingHours);
    if (isNaN(hours) || hours <= 0 || !Number.isInteger(hours)) {
      newErrors.maxBookingHours = "Max booking hours must be a positive whole number.";
    } else if (hours > 168) {
      newErrors.maxBookingHours = "Max booking hours cannot exceed 168 hours (7 days).";
    }

    // Institution ID validation (optional numeric)
    if (formData.institutionId !== "") {
      const instId = Number(formData.institutionId);
      if (isNaN(instId) || instId < 0 || !Number.isInteger(instId)) {
        newErrors.institutionId = "Institution ID must be a valid non-negative integer.";
      }
    }

    // Department ID validation (optional numeric)
    if (formData.departmentId !== "") {
      const deptId = Number(formData.departmentId);
      if (isNaN(deptId) || deptId < 0 || !Number.isInteger(deptId)) {
        newErrors.departmentId = "Department ID must be a valid non-negative integer.";
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!validate()) return;

    const payload = {
      name: formData.name.trim(),
      category: formData.category.trim() || null,
      model: formData.model.trim() || null,
      manufacturer: formData.manufacturer.trim() || null,
      status: formData.status,
      maxBookingHours: Number(formData.maxBookingHours),
      requiresApproval: Boolean(formData.requiresApproval),
      institutionId:
        formData.institutionId !== "" ? Number(formData.institutionId) : null,
      departmentId:
        formData.departmentId !== "" ? Number(formData.departmentId) : null,
      tags: formData.tagsInput
        ? Array.from(
            new Set(
              formData.tagsInput
                .split(",")
                .map((t) => t.trim())
                .filter(Boolean)
            )
          )
        : [],
    };

    await onSubmit(payload);
  }

  return (
    <form className="equipment-form-card" onSubmit={handleSubmit} noValidate>
      {serverError && (
        <div className="alert-error" role="alert">
          <span aria-hidden="true">⚠️</span>
          <span>{serverError}</span>
        </div>
      )}

      <div className="form-grid">
        {/* Name (Required) */}
        <div className="form-group span-2">
          <label className="form-label" htmlFor="equipment-name">
            Equipment Name <span className="required-star">*</span>
          </label>
          <input
            id="equipment-name"
            name="name"
            type="text"
            className={`form-input ${errors.name ? "has-error" : ""}`}
            placeholder="e.g. Confocal Laser Scanning Microscope (LSM 980)"
            value={formData.name}
            onChange={handleChange}
            maxLength={150}
            disabled={isSubmitting}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? "name-error" : undefined}
            required
          />
          {errors.name && (
            <span className="form-error-message" id="name-error">
              {errors.name}
            </span>
          )}
        </div>

        {/* Category */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-category">
            Category
          </label>
          <input
            id="equipment-category"
            name="category"
            type="text"
            list="common-categories-list"
            className={`form-input ${errors.category ? "has-error" : ""}`}
            placeholder="e.g. Microscopy, Spectroscopy"
            value={formData.category}
            onChange={handleChange}
            maxLength={100}
            disabled={isSubmitting}
          />
          <datalist id="common-categories-list">
            {COMMON_CATEGORIES.map((cat) => (
              <option key={cat} value={cat} />
            ))}
          </datalist>
          {errors.category && (
            <span className="form-error-message">{errors.category}</span>
          )}
        </div>

        {/* Status */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-status">
            Operational Status <span className="required-star">*</span>
          </label>
          <select
            id="equipment-status"
            name="status"
            className="form-select"
            value={formData.status}
            onChange={handleChange}
            disabled={isSubmitting}
          >
            {EQUIPMENT_STATUSES.map((st) => (
              <option key={st.value} value={st.value}>
                {st.label}
              </option>
            ))}
          </select>
        </div>

        {/* Model */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-model">
            Model
          </label>
          <input
            id="equipment-model"
            name="model"
            type="text"
            className={`form-input ${errors.model ? "has-error" : ""}`}
            placeholder="e.g. LSM 980 with Airyscan 2"
            value={formData.model}
            onChange={handleChange}
            maxLength={100}
            disabled={isSubmitting}
          />
          {errors.model && (
            <span className="form-error-message">{errors.model}</span>
          )}
        </div>

        {/* Manufacturer */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-manufacturer">
            Manufacturer
          </label>
          <input
            id="equipment-manufacturer"
            name="manufacturer"
            type="text"
            className={`form-input ${errors.manufacturer ? "has-error" : ""}`}
            placeholder="e.g. Carl Zeiss AG"
            value={formData.manufacturer}
            onChange={handleChange}
            maxLength={100}
            disabled={isSubmitting}
          />
          {errors.manufacturer && (
            <span className="form-error-message">{errors.manufacturer}</span>
          )}
        </div>

        {/* Max Booking Hours */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-max-hours">
            Max Booking Duration (Hours) <span className="required-star">*</span>
          </label>
          <input
            id="equipment-max-hours"
            name="maxBookingHours"
            type="number"
            min={1}
            max={168}
            className={`form-input ${errors.maxBookingHours ? "has-error" : ""}`}
            value={formData.maxBookingHours}
            onChange={handleChange}
            disabled={isSubmitting}
            required
          />
          {errors.maxBookingHours && (
            <span className="form-error-message">{errors.maxBookingHours}</span>
          )}
        </div>

        {/* Tags */}
        <div className="form-group span-2">
          <label className="form-label" htmlFor="equipment-tags">
            Tags & Keywords <small style={{ color: "var(--text-muted)" }}>(Comma-separated, e.g. Laser, High-Res, Optics, Fluorescence)</small>
          </label>
          <input
            id="equipment-tags"
            name="tagsInput"
            type="text"
            className="form-input"
            placeholder="e.g. Microscopy, Airyscan, Laser, Live-Cell"
            value={formData.tagsInput}
            onChange={handleChange}
            disabled={isSubmitting}
          />
        </div>

        {/* Requires Approval */}
        <div className="form-group span-2">
          <label className="form-checkbox-label" htmlFor="equipment-requires-approval">
            <input
              id="equipment-requires-approval"
              name="requiresApproval"
              type="checkbox"
              checked={formData.requiresApproval}
              onChange={handleChange}
              disabled={isSubmitting}
            />
            <div>
              <strong>Require Manager Approval for Reservations</strong>
              <p style={{ margin: "2px 0 0", fontSize: "12px", color: "var(--text-secondary)" }}>
                When enabled, student and researcher bookings remain in PENDING_APPROVAL status until confirmed by a lab manager.
              </p>
            </div>
          </label>
        </div>

        {/* Institution ID */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-institution-id">
            Institution ID <small style={{ color: "var(--text-muted)" }}>(Optional)</small>
          </label>
          <input
            id="equipment-institution-id"
            name="institutionId"
            type="number"
            min={0}
            className={`form-input ${errors.institutionId ? "has-error" : ""}`}
            placeholder="e.g. 1"
            value={formData.institutionId}
            onChange={handleChange}
            disabled={isSubmitting}
          />
          {errors.institutionId && (
            <span className="form-error-message">{errors.institutionId}</span>
          )}
        </div>

        {/* Department ID */}
        <div className="form-group">
          <label className="form-label" htmlFor="equipment-department-id">
            Department ID <small style={{ color: "var(--text-muted)" }}>(Optional)</small>
          </label>
          <input
            id="equipment-department-id"
            name="departmentId"
            type="number"
            min={0}
            className={`form-input ${errors.departmentId ? "has-error" : ""}`}
            placeholder="e.g. 1"
            value={formData.departmentId}
            onChange={handleChange}
            disabled={isSubmitting}
          />
          {errors.departmentId && (
            <span className="form-error-message">{errors.departmentId}</span>
          )}
        </div>
      </div>

      <div className="form-actions">
        <Link
          to={initialData.id ? `/dashboard/equipment/${initialData.id}` : "/dashboard/equipment"}
          className="btn-secondary"
        >
          Cancel
        </Link>
        <button
          type="submit"
          className="btn-primary"
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <>
              <span className="equipment-loading-spinner" style={{ width: 16, height: 16, margin: 0, borderWidth: 2 }} />
              <span>Saving...</span>
            </>
          ) : mode === "create" ? (
            "Create Equipment"
          ) : (
            "Save Changes"
          )}
        </button>
      </div>
    </form>
  );
}
