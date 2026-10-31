
import { Navigate } from "react-router-dom";

export default function RoleBasedAccess({
                                            user,
                                            allowedRoles = [],
                                            children,
                                        }) {
    if (!user) {
        return <Navigate to="/login" replace />;
    }

    const role = String(user.role || "")
        .replace(/^ROLE_/, "")
        .toUpperCase();

    if (!allowedRoles.includes(role)) {
        return <Navigate to="/unauthorized" replace />;
    }

    return children;
}
