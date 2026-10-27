import { getBookingStatusMeta } from "../../services/bookingService";

export default function BookingStatusBadge({ status }) {
  const meta = getBookingStatusMeta(status);

  return (
    <span
      className={`booking-status-badge ${meta.badgeClass}`}
      role="status"
      aria-label={`Booking status: ${meta.label}`}
    >
      {meta.label}
    </span>
  );
}
