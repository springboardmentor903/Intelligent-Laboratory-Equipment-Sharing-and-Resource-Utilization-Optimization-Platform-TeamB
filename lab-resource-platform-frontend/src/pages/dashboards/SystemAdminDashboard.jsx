function SystemAdminDashboard() {
  return (
    <>
      <h1>System Administrator Dashboard</h1>
      <p>Manage platform-level operations and institutions.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Platform Overview</h3>
          <p>Review platform-level information.</p>
        </article>

        <article className="task-card">
          <h3>Institutions</h3>
          <p>Manage institutions on the platform.</p>
        </article>

        <article className="task-card">
          <h3>User Management</h3>
          <p>Manage platform accounts and access.</p>
        </article>
      </section>
    </>
  );
}

export default SystemAdminDashboard;