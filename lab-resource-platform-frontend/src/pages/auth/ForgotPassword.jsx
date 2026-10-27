import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import api from "../../services/api";
import { useToast } from "../../context/ToastContext";

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const toast = useToast();

  const initialToken = searchParams.get("token")?.trim() || "";

  const [step, setStep] = useState(initialToken ? 2 : 1); // 1 = Request token, 2 = Enter token & new password
  const [email, setEmail] = useState("");
  const [token, setToken] = useState(initialToken);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  async function handleRequestToken(event) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!email.trim()) {
      setError("Please enter your registered email address.");
      return;
    }

    setLoading(true);

    try {
      const response = await api.post("/api/auth/forgot-password/request", {
        email: email.trim(),
      });

      const msg =
        response.data.message ||
        "If an account exists with this email address, password reset instructions have been sent.";
      setSuccess(msg);
      toast.success(msg);
      setStep(2);
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        err.response?.data?.error ||
        "Unable to request password reset. Please try again later.";
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmReset(event) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!token.trim()) {
      setError("Reset token is required.");
      return;
    }

    if (newPassword !== confirmPassword) {
      const msg = "Passwords do not match.";
      setError(msg);
      toast.error(msg);
      return;
    }

    if (newPassword.length < 8 || newPassword.length > 72) {
      const msg = "Password must be between 8 and 72 characters.";
      setError(msg);
      toast.warning(msg);
      return;
    }

    setLoading(true);

    try {
      const response = await api.post("/api/auth/forgot-password/confirm", {
        token: token.trim(),
        newPassword,
        confirmPassword,
      });

      const msg = response.data.message || "Password updated successfully.";
      setSuccess(msg);
      toast.success(msg);

      setToken("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.detail ||
        err.response?.data?.error ||
        "Unable to reset password. The reset token may be invalid or expired.";
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" style={{ maxWidth: 440, width: "100%" }}>
        <h1>Reset Password</h1>
        <p>
          {step === 1
            ? "Enter your registered email to receive a secure password reset token."
            : "Enter your reset token and your new password."}
        </p>

        {error && (
          <p className="form-error" role="alert" style={{ marginBottom: 16 }}>
            {error}
          </p>
        )}

        {success && (
          <p className="form-success" role="status" style={{ marginBottom: 16 }}>
            {success}
          </p>
        )}

        {step === 1 ? (
          <form onSubmit={handleRequestToken} className="auth-form">
            <label htmlFor="email">Registered Email Address</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="you@institution.edu"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              maxLength={150}
              disabled={loading}
              required
            />

            <button type="submit" className="primary-button" disabled={loading} style={{ marginTop: 12 }}>
              {loading ? "Requesting..." : "Send Reset Token"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setStep(2);
                setError("");
                setSuccess("");
              }}
              style={{ marginTop: 8 }}
            >
              Already have a reset token? Enter token →
            </button>
          </form>
        ) : (
          <form onSubmit={handleConfirmReset} className="auth-form">
            <label htmlFor="token">Password Reset Token</label>
            <input
              id="token"
              name="token"
              type="text"
              placeholder="Paste reset token here"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              maxLength={64}
              disabled={loading}
              required
            />

            <label htmlFor="newPassword">New Password</label>
            <input
              id="newPassword"
              name="newPassword"
              type="password"
              placeholder="At least 8 characters"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              disabled={loading}
              required
            />

            <label htmlFor="confirmPassword">Confirm New Password</label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              placeholder="Confirm new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              disabled={loading}
              required
            />

            <button type="submit" className="primary-button" disabled={loading} style={{ marginTop: 12 }}>
              {loading ? "Updating..." : "Update Password"}
            </button>

            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                setStep(1);
                setError("");
                setSuccess("");
              }}
              style={{ marginTop: 8 }}
            >
              ← Back to Email Request
            </button>
          </form>
        )}

        <p className="auth-footer" style={{ marginTop: 20 }}>
          Remember your password? <Link to="/login">Back to Login</Link>
        </p>

        {success && step === 2 && !token && (
          <button
            type="button"
            className="primary-button"
            onClick={() => navigate("/login")}
            style={{ marginTop: 12 }}
          >
            Go to Login
          </button>
        )}
      </section>
    </main>
  );
}