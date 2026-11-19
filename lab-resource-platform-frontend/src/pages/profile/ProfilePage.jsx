import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { FiKey, FiSettings } from "react-icons/fi";

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
                </div>

                <div className="profile-actions-group" style={{ display: "flex", flexDirection: "column", gap: "12px", width: "100%", marginTop: "24px" }}>
                    <Link to="/dashboard/account-settings" className="profile-button" style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}>
                        <FiSettings size={16} /> Account Settings
                    </Link>

                    <Link
                        to="/forgot-password"
                        className="profile-button profile-forgot-button"
                        style={{
                            backgroundColor: "#f3f4f6",
                            color: "#374151",
                            border: "1px solid #d1d5db",
                            textAlign: "center",
                            textDecoration: "none",
                            padding: "10px 16px",
                            borderRadius: "8px",
                            fontWeight: "600",
                            fontSize: "0.92rem",
                            transition: "all 0.2s ease",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            gap: "6px"
                        }}
                    >
                        <FiKey size={16} /> Forgot Password? Reset Password
                    </Link>
                </div>
            </section>
        </main>
    );
}
