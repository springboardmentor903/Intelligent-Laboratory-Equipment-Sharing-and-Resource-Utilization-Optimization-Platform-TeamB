function LabTechnicianDashboard() {
  return (
    <>
      <h1>Lab Technician Dashboard</h1>
      <p>Manage equipment status and laboratory maintenance.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Equipment Status</h3>
          <p>Monitor equipment condition and availability.</p>
        </article>

        <article className="task-card">
          <h3>Bookings</h3>
          <p>Review laboratory booking activity.</p>
        </article>

        <article className="task-card">
          <h3>Maintenance</h3>
          <p>Track equipment maintenance tasks.</p>
        </article>
      </section>
    </>
  );
}

export default LabTechnicianDashboard;