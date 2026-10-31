
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function ProfilePage() {
    const { user, loading } = useAuth();

    if (loading) {
        return <main className="profile-page">Loading profile...</main>;
    }

    if (!user) {
        return <main className="profile-page">Please log in to view your profile.</main>;
    }

    const roleLabels = {
        RESEARCHER_STUDENT: "Researcher",
        LAB_TECHNICIAN: "Technician",
        LAB_MANAGER: "Lab Manager",
        DEPARTMENT_HEAD: "Department Head",
        INSTITUTION_ADMINISTRATOR: "Institution Admin",
        SYSTEM_ADMINISTRATOR: "System Admin",
    };

    const role = String(user.role || "").replace(/^ROLE_/, "");

    return (
        <main className="profile-page">
            <section className="profile-card">
                <div className="profile-avatar">
                    {(user.name || user.fullName || user.email || "U")
                        .charAt(0)
                        .toUpperCase()}
                </div>

                <h1>My Profile</h1>
                <p className="profile-subtitle">
                    View your account information
                </p>

                <div className="profile-details">
                    <div className="profile-field">
                        <span>Full Name</span>
                        <strong>
                            {user.name || user.fullName || "Not provided"}
                        </strong>
                    </div>

                    <div className="profile-field">
                        <span>Email Address</span>
                        <strong>{user.email || "Not available"}</strong>
                    </div>

                    <div className="profile-field">
                        <span>Account Role</span>
                        <strong>{roleLabels[role] || role || "Not assigned"}</strong>
                    </div>

                    {user.id != null && (
                        <div className="profile-field">
                            <span>User ID</span>
                            <strong>{user.id}</strong>
                        </div>
                    )}
                </div>

                <Link to="/dashboard/account-settings" className="profile-button">
                    Account Settings
                </Link>
            </section>
        </main>
    );
}
