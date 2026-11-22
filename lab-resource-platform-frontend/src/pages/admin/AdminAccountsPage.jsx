import { useCallback, useEffect, useMemo, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { FiX } from "react-icons/fi";
import { TeamLanyardRail } from "@/components/ui/team-lanyard-rail";

const ROLE_OPTIONS = [
  { value: "SYSTEM_ADMINISTRATOR", label: "System Administrator", color: "#6d28d9" },
  { value: "INSTITUTION_ADMINISTRATOR", label: "Institution Administrator", color: "#1d4ed8" },
  { value: "DEPARTMENT_HEAD", label: "Department Head", color: "#0369a1" },
  { value: "LAB_MANAGER", label: "Lab Manager", color: "#047857" },
  { value: "LAB_TECHNICIAN", label: "Lab Technician", color: "#b45309" },
  { value: "RESEARCHER_STUDENT", label: "Researcher / Student", color: "#4b5563" },
];

const AVATAR_LIST = [
  "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=320&auto=format&fit=crop&q=80",
  "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=320&auto=format&fit=crop&q=80",
];

export default function AdminAccountsPage() {
  const { user: currentAdmin } = useAuth();
  const toast = useToast();

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [searchTerm, setSearchTerm] = useState("");

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState("wall"); // "wall" (default) or "table"

  // Modal State
  const [showModal, setShowModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    fullName: "",
    email: "",
    password: "",
    role: "LAB_TECHNICIAN",
    collegeId: "",
    phone: "",
    active: true,
  });

  const loadColleges = useCallback(async () => {
    try {
      const res = await api.get("/api/colleges");
      setColleges(res.data || []);
    } catch (err) {
      console.error("Could not load colleges", err);
    }
  }, []);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get("/api/admin/users");
      setUsers(res.data || []);
    } catch (err) {
      toast.error("Could not load user accounts list.");
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    loadColleges();
    loadUsers();
  }, [loadColleges, loadUsers]);

  function handleOpenCreate() {
    setEditingUser(null);
    setFormData({
      fullName: "",
      email: "",
      password: "",
      role: "LAB_TECHNICIAN",
      collegeId: selectedCollegeId || (colleges[0]?.id ? String(colleges[0].id) : ""),
      phone: "",
      active: true,
    });
    setShowModal(true);
  }

  function handleOpenEdit(user) {
    setEditingUser(user);
    setFormData({
      fullName: user.fullName || "",
      email: user.email || "",
      password: "",
      role: user.role || "RESEARCHER_STUDENT",
      collegeId: user.collegeId ? String(user.collegeId) : "",
      phone: user.phone || "",
      active: user.active,
    });
    setShowModal(true);
  }

  async function handleSaveUser(e) {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingUser) {
        const payload = {
          fullName: formData.fullName.trim(),
          email: formData.email.trim(),
          password: formData.password ? formData.password.trim() : null,
          role: formData.role,
          collegeId: formData.collegeId ? Number(formData.collegeId) : null,
          phone: formData.phone.trim() || null,
          active: formData.active,
        };
        const res = await api.put(`/api/admin/users/${editingUser.id}`, payload);
        setUsers((prev) =>
          prev.map((u) => (u.id === editingUser.id ? res.data : u))
        );
        toast.success("Account updated successfully.");
      } else {
        const payload = {
          fullName: formData.fullName.trim(),
          email: formData.email.trim(),
          password: formData.password.trim(),
          role: formData.role,
          collegeId: formData.collegeId ? Number(formData.collegeId) : null,
          phone: formData.phone.trim() || null,
        };
        const res = await api.post("/api/admin/users", payload);
        setUsers((prev) => [res.data, ...prev]);
        toast.success("New account created successfully.");
      }
      setShowModal(false);
    } catch (err) {
      toast.error(err.response?.data?.message || err.response?.data?.error || "Failed to save user account.");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleStatus(user) {
    if (user.id === currentAdmin?.id) {
      toast.error("You cannot deactivate your own administrator account.");
      return;
    }
    try {
      const res = await api.patch(`/api/admin/users/${user.id}/status`);
      setUsers((prev) => prev.map((u) => (u.id === user.id ? res.data : u)));
      toast.success(
        `Account ${res.data.active ? "activated" : "deactivated"} successfully.`
      );
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to update account status.");
    }
  }

  async function handleDeleteUser(user) {
    if (user.id === currentAdmin?.id) {
      toast.error("You cannot delete your own administrator account.");
      return;
    }
    if (!window.confirm(`Are you sure you want to permanently delete "${user.fullName}" (${user.email})?`)) {
      return;
    }
    try {
      await api.delete(`/api/admin/users/${user.id}`);
      setUsers((prev) => prev.filter((u) => u.id !== user.id));
      toast.success("Account deleted successfully.");
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not delete user account.");
    }
  }

  // Filter users
  const filteredUsers = users.filter((u) => {
    if (selectedCollegeId && String(u.collegeId) !== String(selectedCollegeId)) {
      return false;
    }
    if (roleFilter && u.role !== roleFilter) {
      return false;
    }
    if (statusFilter !== "") {
      const wantActive = statusFilter === "active";
      if (u.active !== wantActive) return false;
    }
    if (searchTerm) {
      const q = searchTerm.toLowerCase();
      const matchName = u.fullName?.toLowerCase().includes(q);
      const matchEmail = u.email?.toLowerCase().includes(q);
      const matchPhone = u.phone?.toLowerCase().includes(q);
      const matchCol = u.collegeName?.toLowerCase().includes(q);
      if (!matchName && !matchEmail && !matchPhone && !matchCol) {
        return false;
      }
    }
    return true;
  });

  // Transform users into interactive Lanyard Badges
  const lanyardMembers = useMemo(() => {
    const list = filteredUsers.length > 0 ? filteredUsers : users;
    if (list.length === 0) {
      return [
        {
          id: "admin-root",
          name: currentAdmin?.fullName || "System Administrator",
          role: "System Administrator",
          team: "Platform Administration · Central",
          image: AVATAR_LIST[0],
          bio: "Managing institutional tenant configurations, global RBAC permissions, and laboratory sharing networks.",
          tags: ["Platform Admin", "Infrastructure", "Security"],
          links: [{ label: "Email", href: `mailto:${currentAdmin?.email || "admin@platform.edu"}` }],
          tone: 1,
        },
      ];
    }

    const roleToneMap = {
      SYSTEM_ADMINISTRATOR: 1,
      INSTITUTION_ADMINISTRATOR: 2,
      DEPARTMENT_HEAD: 3,
      LAB_MANAGER: 4,
      LAB_TECHNICIAN: 5,
      RESEARCHER_STUDENT: 2,
    };

    return list.slice(0, 10).map((u, i) => {
      const roleObj = ROLE_OPTIONS.find((r) => r.value === u.role);
      const roleLabel = roleObj ? roleObj.label : u.role || "Team Member";
      const tone = roleToneMap[u.role] || ((i % 5) + 1);
      return {
        id: String(u.id || i),
        name: u.fullName || u.username || `User #${u.id}`,
        role: roleLabel,
        team: u.collegeName ? `${u.collegeName.slice(0, 24)} · Lab Ops` : "Engineering & Research Labs",
        image: u.avatarUrl || AVATAR_LIST[i % AVATAR_LIST.length],
        bio: `${roleLabel} at ${u.collegeName || "Institution"}. Contact: ${u.email}`,
        tags: [
          roleLabel.split(" ")[0],
          u.active ? "Active" : "Inactive",
          u.collegeName ? u.collegeName.slice(0, 14) : "Campus",
        ],
        links: [
          { label: "Email", href: `mailto:${u.email}` },
          ...(u.phone ? [{ label: "Call", href: `tel:${u.phone}` }] : []),
        ],
        tone,
        drop: (i % 3) * 6,
      };
    });
  }, [filteredUsers, users, currentAdmin]);

  const collegeMap = Object.fromEntries(colleges.map((c) => [c.id, c.name]));
  const currentCollegeName = selectedCollegeId
    ? collegeMap[selectedCollegeId] || "Selected College"
    : "All Colleges";

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <p className="admin-eyebrow">USER ADMINISTRATION</p>
          <h1 className="admin-title">Accounts Management</h1>
          <p className="admin-subtitle">
            Complete CRUD access to user accounts, role allocations, and institutional access.
          </p>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "12px", flexWrap: "wrap" }}>
          <div style={{ display: "inline-flex", background: "#f1f5f9", padding: "4px", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
            <button
              type="button"
              onClick={() => setViewMode("table")}
              style={{
                padding: "6px 14px",
                borderRadius: "6px",
                border: "none",
                cursor: "pointer",
                fontSize: "0.85rem",
                fontWeight: viewMode === "table" ? "600" : "500",
                background: viewMode === "table" ? "#ffffff" : "transparent",
                color: viewMode === "table" ? "#0f172a" : "#64748b",
                boxShadow: viewMode === "table" ? "0 1px 3px rgba(0,0,0,0.1)" : "none",
                transition: "all 0.15s ease",
              }}
            >
              📋 Directory Table
            </button>
            <button
              type="button"
              onClick={() => setViewMode("wall")}
              style={{
                padding: "6px 14px",
                borderRadius: "6px",
                border: "none",
                cursor: "pointer",
                fontSize: "0.85rem",
                fontWeight: viewMode === "wall" ? "600" : "500",
                background: viewMode === "wall" ? "#ffffff" : "transparent",
                color: viewMode === "wall" ? "#0f172a" : "#64748b",
                boxShadow: viewMode === "wall" ? "0 1px 3px rgba(0,0,0,0.1)" : "none",
                transition: "all 0.15s ease",
              }}
            >
              🪪 Team Lanyard Wall
            </button>
          </div>

          <button type="button" className="admin-btn-primary" onClick={handleOpenCreate}>
            + Create Account
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="admin-filter-bar">
        <div className="admin-filter-group">
          <label htmlFor="userCollegeSelect" className="admin-filter-label">
            Filter by College:
          </label>
          <select
            id="userCollegeSelect"
            className="admin-select admin-college-select"
            value={selectedCollegeId}
            onChange={(e) => setSelectedCollegeId(e.target.value)}
          >
            <option value="">All Colleges ({colleges.length})</option>
            {colleges.map((col) => (
              <option key={col.id} value={col.id}>
                {col.name} ({col.code || "COL"})
              </option>
            ))}
          </select>
        </div>

        <div className="admin-filter-group">
          <label htmlFor="userRoleSelect" className="admin-filter-label">
            Role:
          </label>
          <select
            id="userRoleSelect"
            className="admin-select"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
          >
            <option value="">All Roles</option>
            {ROLE_OPTIONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </div>

        <div className="admin-filter-group">
          <label htmlFor="userStatusSelect" className="admin-filter-label">
            Status:
          </label>
          <select
            id="userStatusSelect"
            className="admin-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All Statuses</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </select>
        </div>

        <div className="admin-filter-group admin-search-group">
          <input
            type="text"
            className="admin-input"
            placeholder="Search by name, email, phone..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="admin-clear-btn"
              onClick={() => setSearchTerm("")}
              style={{ display: "inline-flex", alignItems: "center", justifyContent: "center" }}
            >
              <FiX size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Indicator */}
      <div className="admin-active-filter-badge">
        Showing accounts for: <strong>{currentCollegeName}</strong> ({filteredUsers.length}{" "}
        {filteredUsers.length === 1 ? "account" : "accounts"})
      </div>

      {/* View Switch: Team Lanyard Rail vs Table */}
      {viewMode === "wall" ? (
        <div className="lanyard-wall-container">
          <div className="lanyard-wall-header">
            <div>
              <span className="lanyard-wall-badge">INTERACTIVE BADGE RAIL</span>
              <h2 className="lanyard-wall-title" style={{ marginTop: "4px" }}>
                Personnel & Lab Staff Badges
              </h2>
            </div>
            <p style={{ fontSize: "0.85rem", color: "var(--text-muted, #64748b)", margin: 0 }}>
              Showing {lanyardMembers.length} team member{lanyardMembers.length === 1 ? "" : "s"}
            </p>
          </div>
          <TeamLanyardRail
            items={lanyardMembers}
            heading="A team wall you can flip through, right on the page."
            caption="Hover or swipe through badges. Click to flip and inspect verified institutional credentials & contact links."
          />
        </div>
      ) : loading ? (
        <div className="admin-loading-state">Loading user accounts...</div>
      ) : filteredUsers.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No accounts found</h3>
          <p>
            {selectedCollegeId
              ? `There are no accounts registered under ${currentCollegeName}.`
              : "No user accounts match your filter criteria."}
          </p>
        </div>
      ) : (
        <div className="admin-table-wrapper">
          <table className="admin-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Role</th>
                <th>College / Institution</th>
                <th>Phone</th>
                <th>Status</th>
                <th>Created</th>
                <th style={{ textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map((u) => {
                const roleObj = ROLE_OPTIONS.find((r) => r.value === u.role);
                const roleColor = roleObj ? roleObj.color : "#4b5563";
                const roleLabel = roleObj ? roleObj.label : u.role;
                const isSelf = u.id === currentAdmin?.id;

                return (
                  <tr key={u.id}>
                    <td>
                      <div className="admin-user-cell">
                        <div className="admin-avatar-small">
                          {u.fullName?.charAt(0)?.toUpperCase() || "U"}
                        </div>
                        <div>
                          <strong>
                            {u.fullName} {isSelf && <span className="admin-self-pill">(You)</span>}
                          </strong>
                          <div className="admin-table-sub">{u.email}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span
                        className="admin-role-pill"
                        style={{ backgroundColor: roleColor }}
                      >
                        {roleLabel}
                      </span>
                    </td>
                    <td>
                      <span className="admin-badge-college">
                        {u.collegeName || "General / None"}
                      </span>
                    </td>
                    <td>{u.phone || "—"}</td>
                    <td>
                      <span
                        className={`admin-status-pill ${
                          u.active ? "admin-pill-active" : "admin-pill-inactive"
                        }`}
                      >
                        {u.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>{u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "—"}</td>
                    <td style={{ textAlign: "right" }}>
                      <div className="admin-action-row">
                        <button
                          type="button"
                          className="admin-btn-secondary"
                          onClick={() => handleOpenEdit(u)}
                        >
                          Edit
                        </button>

                        <button
                          type="button"
                          className="admin-btn-secondary"
                          disabled={isSelf}
                          title={isSelf ? "Cannot deactivate yourself" : "Toggle status"}
                          onClick={() => handleToggleStatus(u)}
                        >
                          {u.active ? "Deactivate" : "Activate"}
                        </button>

                        <button
                          type="button"
                          className="admin-btn-danger"
                          disabled={isSelf}
                          title={isSelf ? "Cannot delete yourself" : "Delete account"}
                          onClick={() => handleDeleteUser(u)}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Create / Edit User Modal */}
      {showModal && (
        <div className="admin-modal-backdrop" onClick={() => setShowModal(false)}>
          <div
            className="admin-modal-content"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="admin-modal-header">
              <h2>{editingUser ? "Edit User Account" : "Create New User Account"}</h2>
              <button
                type="button"
                className="admin-modal-close"
                onClick={() => setShowModal(false)}
                style={{ display: "inline-flex", alignItems: "center", justifyContent: "center" }}
              >
                <FiX size={16} />
              </button>
            </div>

            <form onSubmit={handleSaveUser} className="admin-modal-form">
              <label>
                Full Name <span style={{ color: "#ef4444" }}>*</span>
                <input
                  type="text"
                  required
                  className="admin-input"
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                  placeholder="e.g. Dr. John Doe"
                />
              </label>

              <label>
                Email Address <span style={{ color: "#ef4444" }}>*</span>
                <input
                  type="email"
                  required
                  className="admin-input"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  placeholder="e.g. john.doe@institution.edu"
                />
              </label>

              <label>
                Password {editingUser ? "(leave blank to keep unchanged)" : <span style={{ color: "#ef4444" }}>*</span>}
                <input
                  type="password"
                  required={!editingUser}
                  minLength={6}
                  maxLength={72}
                  className="admin-input"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  placeholder={editingUser ? "Enter new password to change" : "Minimum 6 characters"}
                />
              </label>

              <div className="admin-form-row">
                <label>
                  Role <span style={{ color: "#ef4444" }}>*</span>
                  <select
                    required
                    className="admin-select"
                    value={formData.role}
                    onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                  >
                    {ROLE_OPTIONS.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.label}
                      </option>
                    ))}
                  </select>
                </label>

                <label>
                  College / Institution
                  <select
                    className="admin-select"
                    value={formData.collegeId}
                    onChange={(e) => setFormData({ ...formData, collegeId: e.target.value })}
                  >
                    <option value="">None / General</option>
                    {colleges.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.code || "COL"})
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="admin-form-row">
                <label>
                  Phone Number
                  <input
                    type="tel"
                    className="admin-input"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    placeholder="e.g. +1 555-0199"
                  />
                </label>

                {editingUser && (
                  <label className="admin-checkbox-label" style={{ marginTop: "1.6rem" }}>
                    <input
                      type="checkbox"
                      checked={formData.active}
                      disabled={editingUser.id === currentAdmin?.id}
                      onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                    />
                    <span>Account Active</span>
                  </label>
                )}
              </div>

              <div className="admin-modal-actions">
                <button
                  type="button"
                  className="admin-btn-secondary"
                  onClick={() => setShowModal(false)}
                  disabled={saving}
                >
                  Cancel
                </button>
                <button type="submit" className="admin-btn-primary" disabled={saving}>
                  {saving ? "Saving..." : editingUser ? "Save Changes" : "Create Account"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

