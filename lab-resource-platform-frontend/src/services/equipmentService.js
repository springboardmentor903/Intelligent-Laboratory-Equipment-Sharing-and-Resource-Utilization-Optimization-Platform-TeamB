import api from "./api";

/**
 * Service to interact with the backend Equipment APIs:
 * - GET    /api/equipment          (List all equipment)
 * - GET    /api/equipment/{id}     (Get equipment details)
 * - POST   /api/equipment          (Create equipment)
 * - PUT    /api/equipment/{id}     (Update equipment)
 * - DELETE /api/equipment/{id}     (Delete equipment)
 */

export const EQUIPMENT_STATUSES = [
  { value: "AVAILABLE", label: "Available", badgeClass: "status-available" },
  { value: "UNDER_MAINTENANCE", label: "Under Maintenance", badgeClass: "status-maintenance" },
  { value: "OUT_OF_SERVICE", label: "Out of Service", badgeClass: "status-out-of-service" },
  { value: "RETIRED", label: "Retired", badgeClass: "status-retired" },
];

export const COMMON_CATEGORIES = [
  "Microscopy",
  "Spectroscopy",
  "Chromatography",
  "Centrifuges",
  "Thermal Cyclers & PCR",
  "Incubators & Ovens",
  "Analyzers & Meters",
  "Autoclaves & Sterilizers",
  "Electrophysiology",
  "General Laboratory Equipment",
];

export function canCreateEquipment(role) {
  if (!role) return false;
  const cleanRole = String(role).replace(/^ROLE_/, "");
  return [
    "LAB_TECHNICIAN",
    "LAB_MANAGER",
    "INSTITUTION_ADMINISTRATOR",
    "SYSTEM_ADMINISTRATOR",
  ].includes(cleanRole);
}

export function canManageEquipment(role) {
  if (!role) return false;
  const cleanRole = String(role).replace(/^ROLE_/, "");
  return [
    "LAB_MANAGER",
    "INSTITUTION_ADMINISTRATOR",
    "SYSTEM_ADMINISTRATOR",
  ].includes(cleanRole);
}

export async function getEquipmentList() {
  const response = await api.get("/api/equipment");
  return response.data;
}

export async function getEquipmentById(id) {
  const response = await api.get(`/api/equipment/${id}`);
  return response.data;
}

export async function createEquipment(payload) {
  const response = await api.post("/api/equipment", payload);
  return response.data;
}

export async function updateEquipment(id, payload) {
  const response = await api.put(`/api/equipment/${id}`, payload);
  return response.data;
}

export async function deleteEquipment(id) {
  const response = await api.delete(`/api/equipment/${id}`);
  return response.data;
}

export async function getAllEquipmentTags() {
  const response = await api.get("/api/equipment/tags");
  return response.data;
}
