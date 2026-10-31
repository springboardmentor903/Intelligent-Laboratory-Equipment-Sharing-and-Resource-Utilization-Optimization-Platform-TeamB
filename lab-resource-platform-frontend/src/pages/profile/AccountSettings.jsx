
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export default function AccountSettings() {
    const { user, loading, logout } = useAuth();

    if (loading) {
        return <main className="profile-page">Loading settings...</main>;
    }

    if (!user) {
        return <main className="profile-page">Please log in to view your settings.</main>;
    }

    return (
        <main className="profile-page">
            <section className="profile-card settings-card">
                <h1>Account Settings</h1>
                <p className="profile-subtitle">
                    Manage your account and security
                </p>

                <div className="profile-details">
                    <div className="profile-field">
                        <span>Account Email</span>
                        <strong>{user.email || "Not available"}</strong>
                    </div>

                    <div className="profile-field">
                        <span>Current Role</span>
                        <strong>{user.role || "Not assigned"}</strong>
                    </div>
                </div>

                <div className="settings-actions">
                    <Link to="/dashboard/profile" className="profile-button profile-secondary-button">
                        Back to Profile
                    </Link>

                    <button
                        type="button"
                        className="profile-button profile-logout-button"
                        onClick={async () => {
                            try {
                                await logout();
                            } catch (error) {
                                console.error("Logout request failed:", error);
                            } finally {
                                window.location.replace("/login");
                            }
                        }}
                    >
                        Log Out
                    </button>
                </div>

                <p className="settings-note">
                    Password changes and profile editing can be added
                    after connecting the corresponding backend API endpoints.
                </p>
            </section>
        </main>
    );
}
