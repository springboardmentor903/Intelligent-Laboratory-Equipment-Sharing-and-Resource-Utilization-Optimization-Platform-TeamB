import EmptyRoleDashboard from "../staff/EmptyRoleDashboard";

export default function InstitutionAdminDashboard() {
  return (
    <EmptyRoleDashboard
      roleTitle="Institution Administrator"
      roleSubtitle="Oversee institution-wide laboratory equipment and student booking lifecycles."
      equipmentsPath="/dashboard/institution-admin/equipments"
      bookingsPath="/dashboard/institution-admin/bookings"
    />
  );
}
