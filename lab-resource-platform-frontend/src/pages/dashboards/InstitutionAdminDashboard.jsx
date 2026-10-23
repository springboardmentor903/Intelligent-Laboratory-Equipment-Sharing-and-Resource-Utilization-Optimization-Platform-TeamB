function InstitutionAdminDashboard() {
  return (
    <>
      <h1>Institution Administrator Dashboard</h1>
      <p>Oversee institution-wide laboratory resources.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Institution Overview</h3>
          <p>Review institution resources.</p>
        </article>

        <article className="task-card">
          <h3>Departments</h3>
          <p>Manage department information.</p>
        </article>

        <article className="task-card">
          <h3>User Administration</h3>
          <p>Manage accounts within your permissions.</p>
        </article>
      </section>
    </>
  );
}

export default InstitutionAdminDashboard;