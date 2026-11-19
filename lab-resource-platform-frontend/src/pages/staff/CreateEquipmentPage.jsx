import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { FiShield, FiArrowLeft } from "react-icons/fi";

export default function CreateEquipmentPage() {
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
  const [loadingColleges, setLoadingColleges] = useState(true);
  const [saving, setSaving] = useState(false);

  const [formData, setFormData] = useState({
    name: "",
    collegeId: user?.collegeId ? String(user.collegeId) : "",
    category: "",
    model: "",
    manufacturer: "",
    status: "AVAILABLE",
    maxBookingHours: 8,
    hourlyCost: 250,
    isShareable: true,
    specifications: "",
    description: "",
  });

  useEffect(() => {
    api
      .get("/api/colleges")
      .then((res) => {
        const list = res.data || [];
        setColleges(list);
        if (list.length > 0) {
          const matched = list.find((c) => String(c.id) === String(user?.collegeId));
          setFormData((prev) => ({
            ...prev,
            collegeId: prev.collegeId || (matched ? String(matched.id) : String(list[0].id)),
          }));
        }
      })
      .catch(() => toast.error("Could not load colleges list."))
      .finally(() => setLoadingColleges(false));
  }, [user?.collegeId, toast]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.error("Equipment Name is required.");
      return;
    }

    const targetCollegeId = formData.collegeId || user?.collegeId;
    if (!targetCollegeId) {
      toast.error("Please assign a college / institution.");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        name: formData.name.trim(),
        category: formData.category.trim() || null,
        model: formData.model.trim() || null,
        manufacturer: formData.manufacturer.trim() || null,
        status: formData.status,
        collegeId: Number(targetCollegeId),
        institutionId: Number(targetCollegeId),
        isShareable: Boolean(formData.isShareable),
        hourlyCost: Number(formData.hourlyCost) || 250,
        // By default Approval Required
        requiresApproval: true,
        maxBookingHours: Number(formData.maxBookingHours) || 8,
        specifications: formData.specifications.trim() || null,
        description: formData.description.trim() || null,
      };

      await api.post("/api/equipment", payload);
      toast.success("Equipment registered successfully!");
      navigate(-1);
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to create equipment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-page-container">
      {/* Back button */}
      <button
        type="button"
        className="booking-back-link"
        onClick={() => navigate(-1)}
        style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
      >
        <FiArrowLeft size={16} /> Back to Equipments
      </button>

      <div className="create-equipment-wrapper">
        <header className="create-equipment-header">
          <p className="admin-eyebrow">EQUIPMENT ONBOARDING</p>
          <h1 className="admin-title">Register New Equipment</h1>
          <p className="admin-subtitle">
            {isStaff
              ? `Catalog laboratory instruments for ${user?.collegeName || "your college"}. The instrument will be strictly managed by your institution.`
              : "Catalog laboratory instruments for research equipment sharing across affiliated colleges."}
          </p>
        </header>

        <form onSubmit={handleSubmit} className="create-equipment-form">
          <div className="create-form-section">
            <h3 className="create-section-title">Basic Information</h3>

            <div className="booking-field">
              <label htmlFor="eq-name" className="booking-label">
                Equipment Name <span className="booking-required">*</span>
              </label>
              <input
                id="eq-name"
                type="text"
                required
                className="booking-input"
                placeholder="e.g. Scanning Electron Microscope, Digital Oscilloscope"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              />
            </div>

            <div className="booking-datetime-row">
              <div className="booking-field">
                <label htmlFor="eq-college" className="booking-label">
                  Assigned College / Institution <span className="booking-required">*</span>
                </label>
                <select
                  id="eq-college"
                  required
                  className="booking-input"
                  value={formData.collegeId}
                  onChange={(e) => setFormData({ ...formData, collegeId: e.target.value })}
                  disabled={loadingColleges}
                  style={{ fontWeight: 600, color: "#1e293b" }}
                >
                  <option value="">Select College</option>
                  {colleges.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.code || "COL"})
                      {String(c.id) === String(user?.collegeId) ? " — (Your College)" : ""}
                    </option>
                  ))}
                </select>
                <span style={{ fontSize: "0.78rem", color: "#64748b", marginTop: "4px", display: "block" }}>
                  Select your college or a partner college to catalog this equipment.
                </span>
              </div>

              <div className="booking-field">
                <label htmlFor="eq-category" className="booking-label">
                  Category
                </label>
                <input
                  id="eq-category"
                  type="text"
                  className="booking-input"
                  placeholder="e.g. Optics, Electronics, Chemistry"
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                />
              </div>
            </div>

            <div className="booking-datetime-row">
              <div className="booking-field">
                <label htmlFor="eq-model" className="booking-label">
                  Model
                </label>
                <input
                  id="eq-model"
                  type="text"
                  className="booking-input"
                  placeholder="e.g. CX23, TBS1102C"
                  value={formData.model}
                  onChange={(e) => setFormData({ ...formData, model: e.target.value })}
                />
              </div>

              <div className="booking-field">
                <label htmlFor="eq-mfr" className="booking-label">
                  Manufacturer
                </label>
                <input
                  id="eq-mfr"
                  type="text"
                  className="booking-input"
                  placeholder="e.g. Olympus, Tektronix"
                  value={formData.manufacturer}
                  onChange={(e) => setFormData({ ...formData, manufacturer: e.target.value })}
                />
              </div>
            </div>
          </div>

          <div className="create-form-section">
            <h3 className="create-section-title">Availability &amp; Reservation Limits</h3>

            <div className="booking-datetime-row">
              <div className="booking-field">
                <label htmlFor="eq-status" className="booking-label">
                  Initial Status <span className="booking-required">*</span>
                </label>
                <select
                  id="eq-status"
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
                <label htmlFor="eq-hours" className="booking-label">
                  Max Booking Hours per Session <span className="booking-required">*</span>
                </label>
                <input
                  id="eq-hours"
                  type="number"
                  min="1"
                  max="168"
                  className="booking-input"
                  value={formData.maxBookingHours}
                  onChange={(e) => setFormData({ ...formData, maxBookingHours: e.target.value })}
                />
              </div>
            </div>

            <div className="approval-default-badge" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <FiShield size={16} /> <span><strong>Default Rule:</strong> Student bookings on this equipment always require administrative approval from the Lab Manager, Department Head, or Institution Administrator before confirmation.</span>
            </div>
          </div>

          <div className="create-form-section">
            <h3 className="create-section-title">Inter-Institution Sharing Settings</h3>

            <div style={{ display: "flex", flexDirection: "column", gap: "12px", background: "#f0fdf4", padding: "16px", borderRadius: "8px", border: "1px solid #bbf7d0" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "10px", cursor: "pointer", fontWeight: 600, color: "#166534" }}>
                <input
                  type="checkbox"
                  checked={formData.isShareable}
                  onChange={(e) => setFormData({ ...formData, isShareable: e.target.checked })}
                  style={{ width: "18px", height: "18px", cursor: "pointer" }}
                />
                <span>Enable Cross-Institution Equipment Sharing</span>
              </label>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "#15803d" }}>
                When enabled, this equipment will be published to <strong>Cross-Institution Equipment Discovery</strong> so researchers and department heads from other colleges can request access.
              </p>

              {formData.isShareable && (
                <div className="booking-datetime-row" style={{ marginTop: "6px" }}>
                  <div className="booking-field" style={{ maxWidth: "260px" }}>
                    <label htmlFor="eq-hourly-cost" className="booking-label">
                      Hourly Sharing Rate (₹/hr)
                    </label>
                    <input
                      id="eq-hourly-cost"
                      type="number"
                      min="0"
                      step="10"
                      className="booking-input"
                      placeholder="250"
                      value={formData.hourlyCost}
                      onChange={(e) => setFormData({ ...formData, hourlyCost: e.target.value })}
                    />
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="create-form-section">
            <h3 className="create-section-title">Specifications &amp; Documentation</h3>

            <div className="booking-field">
              <label htmlFor="eq-specs" className="booking-label">
                Specifications &amp; Operating Parameters
              </label>
              <textarea
                id="eq-specs"
                className="booking-textarea"
                rows={3}
                placeholder="Key specifications, power requirements, precision details..."
                value={formData.specifications}
                onChange={(e) => setFormData({ ...formData, specifications: e.target.value })}
              />
            </div>

            <div className="booking-field">
              <label htmlFor="eq-desc" className="booking-label">
                Equipment Description &amp; Usage Instructions
              </label>
              <textarea
                id="eq-desc"
                className="booking-textarea"
                rows={3}
                placeholder="General laboratory usage notes, safety precautions..."
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              />
            </div>
          </div>

          <div className="booking-form-actions" style={{ marginTop: 12 }}>
            <button
              type="button"
              className="booking-cancel-btn"
              onClick={() => navigate(-1)}
              disabled={saving}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="booking-submit-btn"
              disabled={saving}
            >
              {saving ? "Registering..." : "Register Equipment"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
