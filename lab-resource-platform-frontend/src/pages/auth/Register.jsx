import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

function Register() {
  const { user, loading, register } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
    role: "RESEARCHER_STUDENT",
  });

  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (loading) {
    return <div className="page-loading">Loading...</div>;
  }

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  function update(event) {
    const { name, value } = event.target;

    setForm((previousForm) => ({
      ...previousForm,
      [name]: value,
    }));
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");

    const fullName = form.fullName.trim();
    const email = form.email.trim();

    if (!fullName || !email || !form.password) {
      const msg = "Please fill in all required fields.";
      setError(msg);
      toast.warning(msg);
      return;
    }

    if (form.password.length < 8) {
      const msg = "Password must contain at least 8 characters.";
      setError(msg);
      toast.warning(msg);
      return;
    }

    setSubmitting(true);

    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      navigate("/login", {
        replace: true,
        state: {
          message: "Registration successful! Please sign in.",
        },
      });
    } catch (err) {
      console.error("Registration failed:", err);

      let msg = "Registration failed. Please try again.";

      if (err.response?.status === 409) {
        msg = "This email is already registered. Please sign in.";
      } else if (err.response?.data?.detail) {
        msg = err.response.data.detail;
      } else if (err.response?.data?.error) {
        msg = err.response.data.error;
      } else if (err.response?.data?.message) {
        msg = err.response.data.message;
      } else if (err.response?.status === 400) {
        msg = "Invalid registration details. Please check your inputs.";
      } else if (!err.response) {
        msg =
          "Unable to reach the registration service. Check your network connection.";
      }

      setError(msg);
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page register-page">
      <section className="auth-panel">
        <div className="auth-heading">
          <h1>Create your account</h1>
          <p>Register to explore and book laboratory resources.</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <label htmlFor="fullName">Full name</label>
          <input
            id="fullName"
            name="fullName"
            autoComplete="name"
            placeholder="Enter your full name"
            value={form.fullName}
            onChange={update}
            maxLength={150}
            required
          />

          <label htmlFor="email">Email address</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@institution.edu"
            value={form.email}
            onChange={update}
            maxLength={150}
            required
          />

          <div className="account-role-info" style={{ margin: "4px 0 12px", padding: "10px 12px", background: "var(--bg-main)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-color)" }}>
            <span style={{ fontSize: "0.85rem", color: "var(--text-muted)", display: "block" }}>Account Type</span>
            <strong style={{ fontSize: "0.92rem", color: "var(--text-primary)" }}>🎓 Researcher / Student</strong>
            <p style={{ margin: "4px 0 0", fontSize: "0.78rem", color: "var(--text-muted)" }}>
              Need elevated technician privileges?{" "}
              <Link to="/technician-access-request" style={{ color: "var(--brand-primary)", textDecoration: "underline" }}>
                Request technician access
              </Link>.
            </p>
          </div>

          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            placeholder="At least 8 characters"
            value={form.password}
            onChange={update}
            minLength={8}
            maxLength={72}
            required
          />

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            className="primary-button"
            disabled={submitting}
          >
            {submitting ? "Creating account..." : "Create Account"}
          </button>
        </form>

        <p className="auth-footer">
          Already registered? <Link to="/login">Login</Link>
        </p>
      </section>

      <aside className="auth-aside">
        <div>
          <p className="eyebrow">A BETTER WAY TO SHARE RESOURCES</p>
          <h2>Your laboratory work starts here.</h2>
          <p>
            Keep equipment access and resource booking organized in one place.
          </p>
        </div>

        <span className="aside-caption">Simple. Organized. Connected.</span>
      </aside>
    </main>
  );
}

export default Register;
