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

          <div style={{ textAlign: "center", margin: "14px 0 8px 0" }}>
            <span style={{ color: "var(--text-muted)", fontSize: "0.82rem" }}>
              or continue with
            </span>
          </div>

          <a
            href="http://localhost:8080/oauth2/authorization/google"
            className="secondary-button"
            style={{ width: "100%", textDecoration: "none", gap: "8px" }}
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            Continue with Google
          </a>
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