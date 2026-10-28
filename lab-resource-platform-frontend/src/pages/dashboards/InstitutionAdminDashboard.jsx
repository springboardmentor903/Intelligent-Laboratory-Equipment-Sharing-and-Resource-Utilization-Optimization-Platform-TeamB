
import { useNavigate } from "react-router-dom";

function InstitutionAdminDashboard() {
    const navigate = useNavigate();

    const dashboardCards = [
        {
            title: "Institution Overview",
            description: "View institution-wide activity and resource utilization.",
            icon: "🏛️",
            action: "View Overview",
            path: null,
        },
        {
            title: "Departments",
            description: "Review and manage departments across the institution.",
            icon: "🏢",
            action: "Manage Departments",
            path: null,
        },
        {
            title: "Laboratory Resources",
            description: "Review laboratory equipment and resource availability.",
            icon: "🔬",
            action: "View Resources",
            path: "/dashboard/equipment",
        },
        {
            title: "User Administration",
            description: "Review user accounts and their assigned roles.",
            icon: "👥",
            action: "Manage Users",
            path: null,
        },
    ];

    return (
        <main className="institution-admin-dashboard">
            <header className="dashboard-header">
                <div>
                    <h1>Institution Administrator Dashboard</h1>
                    <p>
                        Oversee institution-wide departments, laboratory resources,
                        and user administration.
                    </p>
                </div>
            </header>

            <section className="dashboard-summary">
                <article className="summary-card">
                    <h3>Departments</h3>
                    <p>—</p>
                    <span>Institution-wide departments</span>
                </article>

                <article className="summary-card">
                    <h3>Laboratories</h3>
                    <p>—</p>
                    <span>Registered laboratories</span>
                </article>

                <article className="summary-card">
                    <h3>Equipment</h3>
                    <p>—</p>
                    <span>Tracked equipment items</span>
                </article>

                <article className="summary-card">
                    <h3>User Accounts</h3>
                    <p>—</p>
                    <span>Accounts across the institution</span>
                </article>
            </section>

            <section className="dashboard-section">
                <h2>Administration</h2>

                <div className="dashboard-cards">
                    {dashboardCards.map((card) => (
                        <article className="task-card" key={card.title}>
                            <div className="task-card-icon" aria-hidden="true">
                                {card.icon}
                            </div>

                            <h3>{card.title}</h3>
                            <p>{card.description}</p>

                            <button
                                type="button"
                                disabled={!card.path}
                                onClick={() => {
                                    if (card.path) navigate(card.path);
                                }}
                            >
                                {card.action}
                            </button>
                        </article>
                    ))}
                </div>
            </section>
        </main>
    );
}

export default InstitutionAdminDashboard;
