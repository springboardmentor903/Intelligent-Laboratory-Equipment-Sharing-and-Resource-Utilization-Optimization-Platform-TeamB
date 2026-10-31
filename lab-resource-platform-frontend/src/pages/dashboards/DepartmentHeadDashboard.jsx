import EmptyRoleDashboard from "../staff/EmptyRoleDashboard";

export default function DepartmentHeadDashboard() {
  return (
    <EmptyRoleDashboard
      roleTitle="Department Head"
      roleSubtitle="Oversee department laboratory resources, college equipment, and student booking requests."
      equipmentsPath="/dashboard/department-head/equipments"
      bookingsPath="/dashboard/department-head/bookings"
    />
  );
}
