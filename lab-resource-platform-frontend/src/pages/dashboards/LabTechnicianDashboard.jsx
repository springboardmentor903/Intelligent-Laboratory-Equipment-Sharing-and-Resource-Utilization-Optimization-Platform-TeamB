import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

function LabTechnicianDashboard() {
    const { user } = useAuth();

    const firstName =
        user?.fullName?.trim().split(/\s+/)[0] || "Technician";

    return (
        <div className="technician-dashboard">
            <section className="technician-welcome">
                <div>
                    <p className="technician-eyebrow">LABORATORY WORKSPACE</p>
                    <h1>Welcome back, {firstName}!</h1>
                    <p>
                        Monitor laboratory equipment, review booking activity,
                        and keep maintenance tasks organized.
                    </p>
                </div>
                <div className="technician-welcome-icon" aria-hidden="true">
                    🔬
                </div>
            </section>

            <section className="technician-section">
                <h2>Your workspace</h2>
                <p className="technician-section-description">
                    Choose an area to focus on.
                </p>

                <div className="technician-cards">
                    <article className="technician-card">
                        <div className="technician-card-icon">🧪</div>
                        <h3>Equipment Status</h3>
                        <p>
                            Review equipment condition and availability to help
                            keep laboratory resources ready for use.
                        </p>
                        <Link
                            to="/dashboard/equipment"
                            className="btn-secondary"
                            style={{ display: "inline-block", marginTop: "10px", width: "fit-content", textDecoration: "none" }}
                        >
                            Open Equipment Catalog →
                        </Link>
                    </article>

                    <article className="technician-card">
                        <div className="technician-card-icon">📅</div>
                        <h3>Bookings</h3>
                        <p>
                            Review laboratory booking activity and help ensure
                            equipment is ready for scheduled reservations.
                        </p>
                        <span className="technician-card-note">
              Booking data to be connected
            </span>
                    </article>

                    <article className="technician-card">
                        <div className="technician-card-icon">🛠️</div>
                        <h3>Maintenance</h3>
                        <p>
                            Organize equipment maintenance and keep track of
                            items that need attention.
                        </p>
                        <span className="technician-card-note">
              Maintenance data to be connected
            </span>
                    </article>
                </div>
            </section>

            <section className="technician-notice">
                <span aria-hidden="true">ℹ️</span>
                <p>
                    Live equipment status, booking records, and maintenance
                    tasks will appear after connecting the relevant backend APIs.
                </p>
            </section>
        </div>
    );
}

export default LabTechnicianDashboard;