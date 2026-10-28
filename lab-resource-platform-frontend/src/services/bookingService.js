import api from "./api";

export const BOOKING_STATUSES = [
  { value: "PENDING_APPROVAL", label: "Pending Approval", badgeClass: "badge-pending" },
  { value: "CONFIRMED", label: "Confirmed", badgeClass: "badge-confirmed" },
  { value: "IN_USE", label: "In Use", badgeClass: "badge-in-use" },
  { value: "COMPLETED", label: "Completed", badgeClass: "badge-completed" },
  { value: "CANCELLED", label: "Cancelled", badgeClass: "badge-cancelled" },
  { value: "NO_SHOW", label: "No Show", badgeClass: "badge-no-show" },
];

export function getBookingStatusMeta(status) {
  const match = BOOKING_STATUSES.find((s) => s.value === status);
  if (match) return match;
  return {
    value: status || "UNKNOWN",
    label: (status || "Unknown").replaceAll("_", " "),
    badgeClass: "badge-unknown",
  };
}

export function canApproveBookings(role) {
  if (!role) return false;
  const normalized = role.toUpperCase();
  return [
    "LAB_MANAGER",
    "DEPARTMENT_HEAD",
    "INSTITUTION_ADMINISTRATOR",
    "INSTITUTION_ADMIN",
    "SYSTEM_ADMINISTRATOR",
    "SYSTEM_ADMIN",
  ].includes(normalized);
}

// Fetch occupied availability slots for an equipment item within a date range
export async function getEquipmentAvailability(equipmentId, startDate, endDate) {
  const response = await api.get(`/api/equipment/${equipmentId}/availability`, {
    params: { startDate, endDate },
  });
  return response.data;
}

// Create a new booking reservation
export async function createBooking(payload) {
  const response = await api.post("/api/bookings", payload);
  return response.data;
}

// Get current authenticated user's bookings with optional pagination and status filter
export async function getMyBookings({
  status = null,
  page = 0,
  size = 20,
  sortBy = "startTime",
  direction = "desc",
} = {}) {
  const params = { page, size, sortBy, direction };
  if (status && status !== "ALL") {
    params.status = status;
  }
  const response = await api.get("/api/bookings/my", { params });
  return response.data;
}

// Get details of a single booking by ID
export async function getBookingById(id) {
  const response = await api.get(`/api/bookings/${id}`);
  return response.data;
}

// Cancel an active reservation with a mandatory reason
export async function cancelBooking(id, reason) {
  const response = await api.post(`/api/bookings/${id}/cancel`, { reason });
  return response.data;
}

// Approve a pending reservation request (Manager/Admin only)
export async function approveBooking(id) {
  const response = await api.post(`/api/bookings/${id}/approve`);
  return response.data;
}

// Reject a pending reservation request with a mandatory reason (Manager/Admin only)
export async function rejectBooking(id, reason) {
  const response = await api.post(`/api/bookings/${id}/reject`, { reason });
  return response.data;
}

// Retrieve the status change audit history of a booking
export async function getBookingHistory(id) {
  const response = await api.get(`/api/bookings/${id}/history`);
  return response.data;
}

// Get bookings awaiting approval or assigned to manager's jurisdiction
export async function getBookingsForApproval({
  status = "PENDING_APPROVAL",
  page = 0,
  size = 20,
  sortBy = "createdAt",
  direction = "desc",
} = {}) {
  const params = { page, size, sortBy, direction };
  if (status && status !== "ALL") {
    params.status = status;
  }
  const response = await api.get("/api/bookings/approvals", { params });
  return response.data;
}
