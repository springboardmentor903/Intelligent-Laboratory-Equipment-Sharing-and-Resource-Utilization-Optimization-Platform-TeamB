function StudentDashboard() {
  return (
    <>
      <h1>Student Dashboard</h1>
      <p>Find equipment and manage your laboratory bookings.</p>

      <section className="dashboard-cards">
        <article className="task-card">
          <h3>Browse Equipment</h3>
          <p>Explore available laboratory equipment.</p>
        </article>

        <article className="task-card">
          <h3>My Bookings</h3>
          <p>View upcoming and pending bookings.</p>
        </article>

        <article className="task-card">
          <h3>Booking History</h3>
          <p>Review previous bookings.</p>
        </article>
      </section>
    </>
  );
}

export default StudentDashboard;