import { useCallback, useEffect, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

const ROLE_OPTIONS = [
  { value: "SYSTEM_ADMINISTRATOR", label: "System Administrator", color: "#6d28d9" },
  { value: "INSTITUTION_ADMINISTRATOR", label: "Institution Administrator", color: "#1d4ed8" },
  { value: "DEPARTMENT_HEAD", label: "Department Head", color: "#0369a1" },
  { value: "LAB_MANAGER", label: "Lab Manager", color: "#047857" },
  { value: "LAB_TECHNICIAN", label: "Lab Technician", color: "#b45309" },
  { value: "RESEARCHER_STUDENT", label: "Researcher / Student", color: "#4b5563" },
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

        <button type="button" className="admin-btn-primary" onClick={handleOpenCreate}>
          + Create Account
        </button>
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
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Indicator */}
      <div className="admin-active-filter-badge">
        Showing accounts for: <strong>{currentCollegeName}</strong> ({filteredUsers.length}{" "}
        {filteredUsers.length === 1 ? "account" : "accounts"})
      </div>

      {/* Users Table */}
      {loading ? (
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
              >
                ✕
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

