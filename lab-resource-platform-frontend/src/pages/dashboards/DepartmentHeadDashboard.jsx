function DepartmentHeadDashboard() {
  return (
    <>
      <h1>Department Head Dashboard</h1>
      <p>Oversee department resources and laboratory utilization.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Department Overview</h3>
          <p>Review department laboratory resources.</p>
        </article>

        <article className="task-card">
          <h3>Utilization</h3>
          <p>Review resource utilization information.</p>
        </article>

        <article className="task-card">
          <h3>Reports</h3>
          <p>Access department-level reports.</p>
        </article>
      </section>
    </>
  );
}

export default DepartmentHeadDashboard;