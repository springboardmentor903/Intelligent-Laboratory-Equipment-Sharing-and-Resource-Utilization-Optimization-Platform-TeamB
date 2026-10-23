function LabManagerDashboard() {
  return (
    <>
      <h1>Lab Manager Dashboard</h1>
      <p>Coordinate laboratory inventory and resource allocation.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Equipment Inventory</h3>
          <p>Manage laboratory equipment records.</p>
        </article>

        <article className="task-card">
          <h3>Booking Management</h3>
          <p>Review and manage booking requests.</p>
        </article>

        <article className="task-card">
          <h3>Resource Sharing</h3>
          <p>Coordinate resource sharing between laboratories.</p>
        </article>
      </section>
    </>
  );
}

export default LabManagerDashboard;