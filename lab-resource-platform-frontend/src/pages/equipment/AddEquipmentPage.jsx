import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import {
  createEquipment,
  canCreateEquipment,
} from "../../services/equipmentService";
import EquipmentForm from "./EquipmentForm";
import "./equipment.css";

export default function AddEquipmentPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState("");

  const isAuthorized = canCreateEquipment(user?.role);

  if (!isAuthorized) {
    return (
      <div className="equipment-page-container">
        <nav className="equipment-breadcrumb">
          <Link to="/dashboard">Dashboard</Link>
          <span className="separator">/</span>
          <Link to="/dashboard/equipment">Equipment Catalog</Link>
          <span className="separator">/</span>
          <span className="current">Access Restricted</span>
        </nav>

        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">🚫</span>
          <h3>Access Restricted</h3>
          <p>
            Your account role (<strong>{user?.role?.replaceAll("_", " ") || "User"}</strong>) does not
            have permission to register new laboratory equipment. This action is restricted to
            Laboratory Technicians, Lab Managers, and Administrators.
          </p>
          <Link to="/dashboard/equipment" className="btn-primary">
            ← Return to Equipment Catalog
          </Link>
        </div>
      </div>
    );
  }

  async function handleCreate(payload) {
    setIsSubmitting(true);
    setServerError("");
    try {
      const created = await createEquipment(payload);
      toast.success(`Equipment "${created.name}" created successfully!`);
      navigate(`/dashboard/equipment/${created.id}`);
    } catch (err) {
      console.error("Failed to create equipment:", err);
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to create equipment record. Please review form entries and try again.";
      setServerError(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
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
        <span className="current">Add Equipment</span>
      </nav>

      {/* Header */}
      <header className="equipment-header" style={{ maxWidth: 800, margin: "0 auto 24px" }}>
        <div>
          <h1>Register New Equipment</h1>
          <p>
            Enter laboratory equipment specifications, operational status, and reservation policies.
          </p>
        </div>
        <div>
          <Link to="/dashboard/equipment" className="btn-secondary">
            ← Cancel
          </Link>
        </div>
      </header>

      {/* Form Card */}
      <EquipmentForm
        onSubmit={handleCreate}
        isSubmitting={isSubmitting}
        mode="create"
        serverError={serverError}
      />
    </div>
  );
}
