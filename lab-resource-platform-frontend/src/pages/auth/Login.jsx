import { useEffect, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

function Login() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (location.state?.message) {
      toast.success(location.state.message);
      window.history.replaceState({}, document.title);
    }
  }, [location.state, toast]);

  if (loading) return <div className="page-loading">Loading...</div>;
  if (user) return <Navigate to="/dashboard" replace />;

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      const currentUser = await login(form);

      if (!currentUser) {
        const msg = "Login succeeded, but the user session could not be loaded.";
        setError(msg);
        toast.error(msg);
        return;
      }

      toast.success("Welcome back! Signed in successfully.");
      navigate("/dashboard", { replace: true });
    } catch (err) {
      let msg = "Unable to reach the server. Check that the backend is running.";
      if (err.response?.status === 401) {
        msg = "Invalid email or password.";
      } else if (err.response?.status === 403) {
        msg = "Your account is inactive or access is denied.";
      }
      setError(msg);
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-page">
      <section className="auth-panel">

        <div className="auth-heading">
          <h1>Login</h1>
          <p>Login to access your laboratory workspace.</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@institution.edu"
            value={form.email}
            onChange={update}
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={form.password}
            onChange={update}
            required
          />

          {error && <p className="form-error" role="alert">{error}</p>}

          <button className="primary-button" disabled={submitting}>
            {submitting ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <div className="forgot-password-link">
          <Link to="/forgot-password">Forgot password?</Link>
        </div>

        <p className="auth-footer">
          Don't have an account? <Link to="/register">Create an account</Link>
        </p>
      </section>

      <aside className="auth-aside">
        <div>
          <p className="eyebrow">A SHARED SPACE FOR DISCOVERY</p>
          <h2>Connect resources. Enable research. Create impact.</h2>
          <p>
            — The spirit of collaborative research
          </p>
        </div>
        <span className="aside-caption">Laboratory Resource Management</span>
      </aside>
    </main>
  );
}

export default Login;