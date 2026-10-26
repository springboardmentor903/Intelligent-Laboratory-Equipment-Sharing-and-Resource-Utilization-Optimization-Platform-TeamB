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

  if (loading) return <div className="page-loading">Loading...</div>;
  if (user) return <Navigate to="/dashboard" replace />;

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");

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
        state: { message: "Registration successful! Please sign in." },
      });
    } catch (err) {
      console.error("Registration failed:", err);
      console.error("Response status:", err.response?.status);
      console.error("Response data:", err.response?.data);

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
          "Unable to reach the registration service. Check the network connection.";
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

          <label htmlFor="role">Register as</label>
          <select
            id="role"
            name="role"
            className="role-select"
            value={form.role}
            onChange={update}
            required
          >
            <option value="RESEARCHER_STUDENT">
              Researcher / Student
            </option>
            <option value="LAB_TECHNICIAN">Lab Technician</option>
            <option value="LAB_MANAGER">Lab Manager</option>
            <option value="DEPARTMENT_HEAD">Department Head</option>
            <option value="INSTITUTION_ADMINISTRATOR">
              Institution Administrator
            </option>
            <option value="SYSTEM_ADMINISTRATOR">
              System Administrator
            </option>
          </select>

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
            {submitting ? "Creating account..." : "Create account"}
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
