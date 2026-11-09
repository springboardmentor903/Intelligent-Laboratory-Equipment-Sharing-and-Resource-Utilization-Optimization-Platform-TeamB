import { useState } from "react";
import api from "../services/api";
import { useToast } from "../context/ToastContext";

export default function EditEquipmentModal({ equipment, onClose, onSuccess }) {
  const toast = useToast();
  const [saving, setSaving] = useState(false);

  const [formData, setFormData] = useState({
    name: equipment.name || "",
    category: equipment.category || "",
    model: equipment.model || "",
    manufacturer: equipment.manufacturer || "",
    location: equipment.location || "",
    status: equipment.status || "AVAILABLE",
    maxBookingHours: equipment.maxBookingHours || 8,
    specifications: equipment.specifications || "",
    description: equipment.description || "",
  });

  async function handleSubmit(e) {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.error("Equipment Name is required.");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        ...equipment,
        name: formData.name.trim(),
        category: formData.category.trim() || null,
        model: formData.model.trim() || null,
        manufacturer: formData.manufacturer.trim() || null,
        location: formData.location.trim() || null,
        status: formData.status,
        maxBookingHours: Number(formData.maxBookingHours) || 8,
        specifications: formData.specifications.trim() || null,
        description: formData.description.trim() || null,
      };

      const res = await api.put(`/api/equipment/${equipment.id}`, payload);
      toast.success(`"${payload.name}" updated successfully!`);
      if (onSuccess) {
        onSuccess(res.data || payload);
      }
      onClose();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to update equipment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="avail-modal-overlay" onClick={onClose}>
      <div
        className="avail-modal-container"
        style={{ maxWidth: "680px", maxHeight: "90vh", overflowY: "auto" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="avail-modal-header">
          <div>
            <h2 className="avail-modal-title">✏️ Edit Equipment Details</h2>
            <p className="avail-modal-subtitle">
              Modify catalog specifications, reservation limits, and active status for #{equipment.id}
            </p>
          </div>
          <button
            type="button"
            className="avail-modal-close"
            onClick={onClose}
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="avail-modal-body" style={{ padding: "20px 24px" }}>
          <div className="booking-field" style={{ marginBottom: "16px" }}>
            <label className="booking-label">
              Equipment Name <span className="booking-required">*</span>
            </label>
            <input
              type="text"
              required
              className="booking-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="e.g. FDM 3D Printer, Digital Oscilloscope"
            />
          </div>

          <div className="booking-datetime-row" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div className="booking-field">
              <label className="booking-label">Category</label>
              <input
                type="text"
                className="booking-input"
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                placeholder="e.g. Manufacturing, Electronics, Optics"
              />
            </div>

            <div className="booking-field">
              <label className="booking-label">Model</label>
              <input
                type="text"
                className="booking-input"
                value={formData.model}
                onChange={(e) => setFormData({ ...formData, model: e.target.value })}
                placeholder="e.g. Ender-3 V3, TBS1102C"
              />
            </div>
          </div>

          <div className="booking-datetime-row" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div className="booking-field">
              <label className="booking-label">Manufacturer</label>
              <input
                type="text"
                className="booking-input"
                value={formData.manufacturer}
                onChange={(e) => setFormData({ ...formData, manufacturer: e.target.value })}
                placeholder="e.g. Creality, Tektronix, Olympus"
              />
            </div>

            <div className="booking-field">
              <label className="booking-label">Laboratory Location</label>
              <input
                type="text"
                className="booking-input"
                value={formData.location}
                onChange={(e) => setFormData({ ...formData, location: e.target.value })}
                placeholder="e.g. Innovation Lab, Room 105"
              />
            </div>
          </div>

          <div className="booking-datetime-row" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "16px" }}>
            <div className="booking-field">
              <label className="booking-label">Status</label>
              <select
                className="booking-input"
                value={formData.status}
                onChange={(e) => setFormData({ ...formData, status: e.target.value })}
              >
                <option value="AVAILABLE">Available</option>
                <option value="IN_USE">In Use</option>
                <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                <option value="DECOMMISSIONED">Decommissioned</option>
              </select>
            </div>

            <div className="booking-field">
              <label className="booking-label">Max Booking Hours / Session</label>
              <input
                type="number"
                min="1"
                max="168"
                className="booking-input"
                value={formData.maxBookingHours}
                onChange={(e) => setFormData({ ...formData, maxBookingHours: e.target.value })}
              />
            </div>
          </div>

          <div className="booking-field" style={{ marginBottom: "16px" }}>
            <label className="booking-label">Specifications</label>
            <textarea
              className="booking-textarea"
              rows={2}
              value={formData.specifications}
              onChange={(e) => setFormData({ ...formData, specifications: e.target.value })}
              placeholder="e.g. 220x220x250mm build volume, direct drive extruder..."
            />
          </div>

          <div className="booking-field" style={{ marginBottom: "20px" }}>
            <label className="booking-label">Description</label>
            <textarea
              className="booking-textarea"
              rows={3}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="Overview and operating instructions for researchers and students..."
            />
          </div>

          <div className="avail-modal-footer" style={{ display: "flex", justifyContent: "flex-end", gap: "12px", paddingTop: "16px", borderTop: "1px solid #f1f5f9" }}>
            <button
              type="button"
              className="student-availability-btn"
              style={{ width: "auto", padding: "8px 18px" }}
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </button>

            <button
              type="submit"
              className="student-book-btn"
              style={{ width: "auto", padding: "8px 24px" }}
              disabled={saving}
            >
              {saving ? "Saving Changes..." : "💾 Save Changes"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

