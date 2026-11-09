import EmptyRoleDashboard from "../staff/EmptyRoleDashboard";

export default function LabTechnicianDashboard() {
  return (
    <EmptyRoleDashboard
      roleTitle="Lab Technician"
      roleSubtitle="Manage lab equipment, register instruments, and track student reservation lifecycles."
      equipmentsPath="/dashboard/technician/equipments"
      bookingsPath="/dashboard/technician/bookings"
    />
  );
}
