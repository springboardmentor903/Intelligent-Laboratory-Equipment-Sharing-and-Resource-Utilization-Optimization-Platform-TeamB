import EmptyRoleDashboard from "../staff/EmptyRoleDashboard";

export default function LabManagerDashboard() {
  return (
    <EmptyRoleDashboard
      roleTitle="Lab Manager"
      roleSubtitle="Coordinate laboratory assets, college equipment, and student booking requests."
      equipmentsPath="/dashboard/lab-manager/equipments"
      bookingsPath="/dashboard/lab-manager/bookings"
    />
  );
}
