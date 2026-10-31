import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

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
    collegeId: isStaff && user?.collegeId ? String(user.collegeId) : "",
    category: "",
    model: "",
    manufacturer: "",
    status: "AVAILABLE",
    maxBookingHours: 8,
    specifications: "",
    description: "",
  });

  useEffect(() => {
    if (!isStaff) {
      api
        .get("/api/colleges")
        .then((res) => {
          setColleges(res.data || []);
          if (res.data && res.data.length > 0) {
            setFormData((prev) => ({ ...prev, collegeId: String(res.data[0].id) }));
          }
        })
        .catch(() => toast.error("Could not load colleges list."))
        .finally(() => setLoadingColleges(false));
    } else {
      setLoadingColleges(false);
      if (user?.collegeId) {
        setFormData((prev) => ({ ...prev, collegeId: String(user.collegeId) }));
      }
    }
  }, [isStaff, user?.collegeId, toast]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.error("Equipment Name is required.");
      return;
    }

    const targetCollegeId = isStaff ? user?.collegeId : formData.collegeId;
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
      >
        ← Back to Equipments
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
                {isStaff ? (
                  <input
                    id="eq-college"
                    type="text"
                    className="booking-input"
                    readOnly
                    disabled
                    value={`${user?.collegeName || "Assigned College"} (Locked to your institution)`}
                    style={{ backgroundColor: "#f3f4f6", cursor: "not-allowed", fontWeight: 600, color: "#1e3a8a" }}
                  />
                ) : (
                  <select
                    id="eq-college"
                    required
                    className="booking-input"
                    value={formData.collegeId}
                    onChange={(e) => setFormData({ ...formData, collegeId: e.target.value })}
                    disabled={loadingColleges}
                  >
                    <option value="">Select College</option>
                    {colleges.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.code || "COL"})
                      </option>
                    ))}
                  </select>
                )}
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

            <div className="approval-default-badge">
              🛡️ <strong>Default Rule:</strong> Student bookings on this equipment always require administrative approval from the Lab Manager, Department Head, or Institution Administrator before confirmation.
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
