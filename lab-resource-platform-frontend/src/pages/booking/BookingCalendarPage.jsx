import { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { getEquipmentList } from "../../services/equipmentService";
import AvailabilityCalendar from "../../components/booking/AvailabilityCalendar";
import "./booking.css";

export default function BookingCalendarPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialEquipmentId = searchParams.get("equipmentId") || "";

  const [equipmentList, setEquipmentList] = useState([]);
  const [selectedEquipmentId, setSelectedEquipmentId] = useState(initialEquipmentId);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    getEquipmentList()
      .then((data) => {
        if (!ignore) {
          const list = Array.isArray(data) ? data : [];
          setEquipmentList(list);
          const currentParam = searchParams.get("equipmentId");
          if (!currentParam && list.length > 0) {
            const firstId = String(list[0].id);
            setSelectedEquipmentId(firstId);
            setSearchParams({ equipmentId: firstId });
          }
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Failed to fetch equipment for calendar:", err);
          setError("Failed to load laboratory equipment records.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [searchParams, setSearchParams]);

  const currentEquipment = equipmentList.find(
    (item) => String(item.id) === String(selectedEquipmentId)
  );

  function handleEquipmentChange(e) {
    const id = e.target.value;
    setSelectedEquipmentId(id);
    setSearchParams(id ? { equipmentId: id } : {});
  }

  function handleSelectSlot(slotInfo) {
    if (!selectedEquipmentId) return;

    // Convert to local datetime-local format: YYYY-MM-DDTHH:mm
    const toLocalISO = (d) => {
      const date = new Date(d);
      const pad = (n) => String(n).padStart(2, "0");
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    };

    const startLocal = toLocalISO(slotInfo.start);
    const endLocal = toLocalISO(slotInfo.end);

    navigate(
      `/dashboard/bookings/new?equipmentId=${selectedEquipmentId}&start=${encodeURIComponent(startLocal)}&end=${encodeURIComponent(endLocal)}`
    );
  }

  return (
    <div className="booking-page-container">
      {/* Breadcrumb */}
      <nav className="booking-breadcrumb" aria-label="Breadcrumb">
        <Link to="/dashboard">Dashboard</Link>
        <span className="separator">/</span>
        <span className="current">Equipment Availability Calendar</span>
      </nav>

      {/* Header */}
      <header className="booking-header">
        <div>
          <h1>Equipment Availability Calendar</h1>
          <p>
            Inspect real-time equipment reservations and availability. Click on any open slot to reserve equipment.
          </p>
        </div>

        <div className="booking-header-actions">
          <Link
            to={
              selectedEquipmentId
                ? `/dashboard/bookings/new?equipmentId=${selectedEquipmentId}`
                : "/dashboard/bookings/new"
            }
            className="btn-primary"
          >
            <span>➕ Book Equipment</span>
          </Link>
        </div>
      </header>

      {/* Equipment Selector Toolbar */}
      <section className="equipment-selector-card">
        <div className="equipment-select-header">
          <div className="equipment-dropdown-group">
            <label htmlFor="calendar-equipment-select">Active Instrument:</label>
            <select
              id="calendar-equipment-select"
              className="equipment-dropdown"
              value={selectedEquipmentId}
              onChange={handleEquipmentChange}
              disabled={loading}
            >
              {equipmentList.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} ({item.category || "Uncategorized"})
                </option>
              ))}
            </select>
          </div>

          {currentEquipment && (
            <div className="equipment-summary-pillbox">
              <span className="summary-pill">
                Status: <strong>{currentEquipment.status}</strong>
              </span>
              <span className="summary-pill">
                Max Duration: <strong>{currentEquipment.maxBookingHours || 8}h</strong>
              </span>
              {currentEquipment.requiresApproval ? (
                <span className="summary-pill approval-badge">
                  🛡 Manager Approval Required
                </span>
              ) : (
                <span className="summary-pill" style={{ color: "#047857", borderColor: "#A7F3D0" }}>
                  ⚡ Direct Confirmation
                </span>
              )}
            </div>
          )}
        </div>
      </section>

      {/* FullCalendar Component */}
      {loading ? (
        <div className="equipment-loading-state" role="status">
          <div className="equipment-loading-spinner" />
          <p>Loading availability schedule...</p>
        </div>
      ) : error ? (
        <div className="equipment-error-state" role="alert">
          <span className="state-icon" aria-hidden="true">⚠️</span>
          <h3>Error Loading Equipment</h3>
          <p>{error}</p>
        </div>
      ) : selectedEquipmentId ? (
        <AvailabilityCalendar
          equipmentId={selectedEquipmentId}
          onSelectSlot={handleSelectSlot}
        />
      ) : (
        <div className="equipment-empty-state">
          <span className="state-icon" aria-hidden="true">🔬</span>
          <h3>No Equipment Available</h3>
          <p>No laboratory equipment is registered in the catalog yet.</p>
        </div>
      )}
    </div>
  );
}
