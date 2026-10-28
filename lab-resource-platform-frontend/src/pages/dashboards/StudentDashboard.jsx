import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

function StudentDashboard() {
    const { user } = useAuth();

    const firstName = user?.fullName?.trim().split(/\s+/)[0] || "Researcher";

    return (
        <div className="student-dashboard">
            <section className="student-welcome">
                <div>
                    <p className="student-eyebrow">STUDENT WORKSPACE</p>
                    <h1>Welcome back, {firstName}!</h1>
                    <p>
                        Find laboratory equipment, manage your bookings, and keep your
                        research moving forward.
                    </p>
                </div>

                <div className="student-welcome-icon" aria-hidden="true">
                    🔬
                </div>
            </section>

            <section className="student-stats" aria-label="Booking summary">
                <Link to="/dashboard/equipment" style={{ textDecoration: "none", color: "inherit" }}>
                    <article className="student-stat-card">
                        <span className="student-stat-icon">🧪</span>
                        <div>
                            <p>Available Equipment</p>
                            <h2>Explore</h2>
                            <span>Find equipment for your research</span>
                        </div>
                    </article>
                </Link>

                <Link to="/dashboard/bookings/my" style={{ textDecoration: "none", color: "inherit" }}>
                    <article className="student-stat-card">
                        <span className="student-stat-icon">📅</span>
                        <div>
                            <p>My Bookings</p>
                            <h2>Manage</h2>
                            <span>Review your booking requests</span>
                        </div>
                    </article>
                </Link>

                <Link to="/dashboard/bookings/calendar" style={{ textDecoration: "none", color: "inherit" }}>
                    <article className="student-stat-card">
                        <span className="student-stat-icon">📋</span>
                        <div>
                            <p>Booking Calendar</p>
                            <h2>Schedule</h2>
                            <span>Inspect time slot availability</span>
                        </div>
                    </article>
                </Link>
            </section>

            <section className="student-section">
                <div className="student-section-heading">
                    <div>
                        <h2>Quick access</h2>
                        <p>Manage your account and research workspace.</p>
                    </div>
                </div>

                <div className="student-quick-actions">
                    <article className="student-action-card">
                        <span className="student-action-icon">🔍</span>
                        <h3>Browse Equipment</h3>
                        <p>
                            Explore laboratory resources and find equipment for your
                            research needs.
                        </p>
                        <Link
                            to="/dashboard/equipment"
                            className="btn-secondary"
                            style={{ display: "inline-block", marginTop: "10px", width: "fit-content", textDecoration: "none" }}
                        >
                            Open Equipment Catalog →
                        </Link>
                    </article>

                    <article className="student-action-card">
                        <span className="student-action-icon">🗓️</span>
                        <h3>My Bookings</h3>
                        <p>
                            Keep track of booking requests and check their status.
                        </p>
                        <Link
                            to="/dashboard/bookings/my"
                            className="btn-secondary"
                            style={{ display: "inline-block", marginTop: "10px", width: "fit-content", textDecoration: "none" }}
                        >
                            View My Reservations →
                        </Link>
                    </article>
                </div>
            </section>

            <section className="student-notice">
                <span aria-hidden="true">ℹ️</span>
                <p>
                    Equipment availability, booking counts, and recent activity will
                    appear here once the corresponding backend endpoints are connected.
                </p>
            </section>
        </div>
    );
}

export default StudentDashboard;