import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import {
  getEquipmentById,
  updateEquipment,
  canManageEquipment,
} from "../../services/equipmentService";
import EquipmentForm from "./EquipmentForm";
import "./equipment.css";

export default function EditEquipmentPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [equipment, setEquipment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isNotFound, setIsNotFound] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState("");

  const isAuthorized = canManageEquipment(user?.role);

  useEffect(() => {
    let ignore = false;

    if (!id) return;

    getEquipmentById(id)
      .then((data) => {
        if (!ignore) {
          setEquipment(data);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to load equipment for edit:", err);
          if (err.response?.status === 404) {
            setIsNotFound(true);
          } else {
            setServerError(
              err.response?.data?.message ||
              err.response?.data?.error ||
              "Unable to retrieve equipment record for editing."
            );
          }
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [id]);

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
            have permission to modify existing laboratory equipment records. Equipment updates are
            restricted to Lab Managers and Administrators.
          </p>
          <Link to={`/dashboard/equipment/${id}`} className="btn-primary">
            ← Return to Equipment Details
          </Link>
        </div>
      </div>
    );
  }

  async function handleUpdate(payload) {
    setIsSubmitting(true);
    setServerError("");
    try {
      const updated = await updateEquipment(id, payload);
      toast.success(`Equipment "${updated.name}" updated successfully!`);
      navigate(`/dashboard/equipment/${id}`);
    } catch (err) {
      console.error("Failed to update equipment:", err);
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to update equipment record. Please verify inputs and try again.";
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
        <Link to={`/dashboard/equipment/${id}`}>
          {loading ? "Loading..." : equipment?.name || `Equipment #${id}`}
        </Link>
        <span className="separator">/</span>
        <span className="current">Edit</span>
      </nav>

      {/* Loading & Error States */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading equipment specifications for editing...</p>
        </div>
      ) : isNotFound ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">🔍</span>
          <h3>Equipment Not Found</h3>
          <p>The equipment item requested for editing (ID #{id}) does not exist.</p>
          <Link to="/dashboard/equipment" className="btn-primary">
            ← Return to Equipment Catalog
          </Link>
        </div>
      ) : equipment ? (
        <>
          <header className="equipment-header" style={{ maxWidth: 800, margin: "0 auto 24px" }}>
            <div>
              <h1>Edit Equipment Specifications</h1>
              <p>
                Update operational status, technical models, and reservation parameters for{" "}
                <strong>{equipment.name}</strong>.
              </p>
            </div>
            <div>
              <Link to={`/dashboard/equipment/${id}`} className="btn-secondary">
                ← Cancel
              </Link>
            </div>
          </header>

          <EquipmentForm
            key={equipment.id}
            initialData={equipment}
            onSubmit={handleUpdate}
            isSubmitting={isSubmitting}
            mode="edit"
            serverError={serverError}
          />
        </>
      ) : (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Equipment</h3>
          <p>{serverError || "An unexpected error occurred."}</p>
          <Link to="/dashboard/equipment" className="btn-primary">
            Back to Catalog
          </Link>
        </div>
      )}
    </div>
  );
}
