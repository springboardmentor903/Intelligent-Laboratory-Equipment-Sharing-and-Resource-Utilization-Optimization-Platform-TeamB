import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

export default function ForgotPassword() {
  const navigate = useNavigate();
  const toast = useToast();

  const [form, setForm] = useState({
    email: "",
    newPassword: "",
    confirmPassword: "",
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  function handleChange(event) {
    setForm({
      ...form,
      [event.target.name]: event.target.value,
    });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (form.newPassword !== form.confirmPassword) {
      const msg = "Passwords do not match.";
      setError(msg);
      toast.error(msg);
      return;
    }

    if (
      form.newPassword.length < 8 ||
      form.newPassword.length > 72
    ) {
      const msg = "Password must be between 8 and 72 characters.";
      setError(msg);
      toast.warning(msg);
      return;
    }

    setLoading(true);

    try {
      const response = await api.post(
        "/api/auth/forgot-password",
        form
      );

      const msg = response.data.message || "Password updated successfully.";
      setSuccess(msg);
      toast.success(msg);

      setForm({
        email: "",
        newPassword: "",
        confirmPassword: "",
      });
    } catch (err) {
      const msg =
        err.response?.data?.detail ||
        err.response?.data?.message ||
        "Unable to update password. Please try again.";
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <h1>Forgot password?</h1>
        <p>
          Enter your registered email and choose a new password.
        </p>

        <form onSubmit={handleSubmit}>
          <label htmlFor="email">Registered Email</label>
          <input
            id="email"
            name="email"
            type="email"
            placeholder="Enter your registered email"
            value={form.email}
            onChange={handleChange}
            autoComplete="email"
            maxLength={150}
            required
          />

          <label htmlFor="newPassword">New Password</label>
          <input
            id="newPassword"
            name="newPassword"
            type="password"
            placeholder="Enter new password"
            value={form.newPassword}
            onChange={handleChange}
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            required
          />

          <label htmlFor="confirmPassword">
            Confirm Password
          </label>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            placeholder="Confirm new password"
            value={form.confirmPassword}
            onChange={handleChange}
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            required
          />

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          {success && (
            <p className="form-success" role="status">
              {success}
            </p>
          )}

          <button type="submit" disabled={loading}>
            {loading ? "Updating..." : "Update Password"}
          </button>
        </form>

        <p className="auth-footer">
          Remember your password?{" "}
          <Link to="/login">Back to Login</Link>
        </p>

        {success && (
          <button
            type="button"
            onClick={() => navigate("/login")}
          >
            Go to Login
          </button>
        )}
      </section>
    </main>
  );
}