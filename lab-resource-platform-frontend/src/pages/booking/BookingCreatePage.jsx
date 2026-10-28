import { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useToast } from "../../context/ToastContext";
import { getEquipmentList } from "../../services/equipmentService";
import { createBooking } from "../../services/bookingService";
import BookingForm from "../../components/booking/BookingForm";
import "./booking.css";

export default function BookingCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const toast = useToast();

  const preselectedEquipmentId = searchParams.get("equipmentId") || "";
  const initialStart = searchParams.get("start") || "";
  const initialEnd = searchParams.get("end") || "";

  const [equipmentList, setEquipmentList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState("");

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
          console.error("Failed to load equipment list:", err);
          setServerError("Unable to load laboratory equipment options.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const preselectedEquipment = equipmentList.find(
    (item) => String(item.id) === String(preselectedEquipmentId)
  );

  async function handleCreateBooking(payload) {
    setIsSubmitting(true);
    setServerError("");
    try {
      const response = await createBooking(payload);
      const isPending = response.status === "PENDING_APPROVAL";

      toast.success(
        isPending
          ? `Booking requested for "${response.equipmentName || "Equipment"}". Awaiting manager approval.`
          : `Booking confirmed for "${response.equipmentName || "Equipment"}".`
      );

      navigate("/dashboard/bookings/my");
    } catch (err) {
      console.error("Failed to create booking:", err);
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Failed to create reservation. Please verify the selected time slot and try again.";
      setServerError(message);
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="booking-page-container">
      {/* Breadcrumb */}
      <nav className="booking-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <Link to="/dashboard/bookings/calendar">Availability Calendar</Link>
        <span className="separator">/</span>
        <span className="current">Create Reservation</span>
      </nav>

      {/* Header */}
      <header className="booking-header" style={{ maxWidth: 760, margin: "0 auto 24px" }}>
        <div>
          <h1>Reserve Laboratory Equipment</h1>
          <p>Schedule research time and confirm instrument reservations.</p>
        </div>
        <div>
          <Link to="/dashboard/bookings/calendar" className="btn-secondary">
            ← Back to Calendar
          </Link>
        </div>
      </header>

      {/* Booking Form Card */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading equipment specifications...</p>
        </div>
      ) : (
        <BookingForm
          equipmentList={equipmentList}
          selectedEquipment={preselectedEquipment}
          initialStart={initialStart}
          initialEnd={initialEnd}
          onSubmit={handleCreateBooking}
          isSubmitting={isSubmitting}
          serverError={serverError}
        />
      )}
    </div>
  );
}
