import { useNavigate } from "react-router-dom";

function LabManagerDashboard() {
    const navigate = useNavigate();

    const dashboardItems = [
        {
            title: "Booking Approvals",
            description: "Review pending reservation requests submitted by researchers.",
            path: "/dashboard/bookings/approvals",
            action: "Review Requests",
        },
        {
            title: "Equipment Inventory",
            description: "View and manage laboratory equipment records.",
            path: "/dashboard/equipment",
            action: "Manage Equipment",
        },
        {
            title: "Booking Management",
            description: "Review booking requests and manage reservations.",
            path: "/bookings",
            action: "Manage Bookings",
        },
        {
            title: "Resource Sharing",
            description: "Coordinate equipment and resource sharing between laboratories.",
            path: "/resource-sharing",
            action: "View Resources",
        },
        {
            title: "Maintenance",
            description: "Monitor equipment maintenance and service requests.",
            path: "/maintenance",
            action: "View Maintenance",
        },
        {
            title: "Analytics and Reports",
            description: "Review laboratory utilization and resource reports.",
            path: "/analytics",
            action: "View Reports",
        },
    ];

    return (
        <main className="lab-manager-dashboard">
            <header className="dashboard-header">
                <h1>Lab Manager Dashboard</h1>
                <p>
                    Coordinate laboratory inventory, booking requests, maintenance,
                    and resource allocation.
                </p>
            </header>

            <section className="dashboard-cards">
                {dashboardItems.map((item) => (
                    <article className="task-card" key={item.title}>
                        <h3>{item.title}</h3>
                        <p>{item.description}</p>

                        <button
                            type="button"
                            onClick={() => navigate(item.path)}
                        >
                            {item.action}
                        </button>
                    </article>
                ))}
            </section>
        </main>
    );
}

export default LabManagerDashboard;