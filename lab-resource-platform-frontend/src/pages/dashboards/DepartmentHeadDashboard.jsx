
import { useAuth } from "../../context/AuthContext";

function DepartmentHeadDashboard() {
    const { user } = useAuth();

    const sections = [
        {
            title: "Department Overview",
            description:
                "Review laboratory equipment and resources assigned to your department.",
            status: "Ready for integration",
        },
        {
            title: "Resource Utilization",
            description:
                "Monitor equipment usage, booking activity, and resource availability.",
            status: "Ready for integration",
        },
        {
            title: "Department Reports",
            description:
                "Review department-level usage summaries and booking reports.",
            status: "Ready for integration",
        },
    ];

    return (
        <main className="department-head-dashboard">
            <header className="department-head-header">
                <div>
                    <h1>Department Head Dashboard</h1>
                    <p>
                        Welcome{user?.name ? `, ${user.name}` : ""}. Oversee your
                        department's laboratory resources and utilization.
                    </p>
                </div>
            </header>

            <section className="department-head-summary">
                <article className="department-head-summary-card">
                    <span>Equipment Overview</span>
                    <h2>—</h2>
                    <p>Awaiting backend data</p>
                </article>

                <article className="department-head-summary-card">
                    <span>Active Bookings</span>
                    <h2>—</h2>
                    <p>Awaiting backend data</p>
                </article>

                <article className="department-head-summary-card">
                    <span>Resource Utilization</span>
                    <h2>—</h2>
                    <p>Awaiting backend data</p>
                </article>
            </section>

            <section className="department-head-features">
                <h2>Department Management</h2>

                <div className="dashboard-cards">
                    {sections.map((section) => (
                        <article className="task-card" key={section.title}>
                            <h3>{section.title}</h3>
                            <p>{section.description}</p>
                            <span className="department-head-status">
                {section.status}
              </span>
                        </article>
                    ))}
                </div>
            </section>
        </main>
    );
}

export default DepartmentHeadDashboard;
