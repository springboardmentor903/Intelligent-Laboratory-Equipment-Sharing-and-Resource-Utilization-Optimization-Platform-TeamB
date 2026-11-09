import { useEffect, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

function Login() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [form, setForm] = useState({
    email: "",
    password: "",
  });

  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (location.state?.message) {
      toast.success(location.state.message);
      window.history.replaceState({}, document.title);
    }
  }, [location.state, toast]);

  if (loading) {
    return <div className="page-loading">Loading...</div>;
  }

  if (user) {
    return <Navigate to="/dashboard" replace />;
  }

  function update(event) {
    setForm({ ...form, [event.target.name]: event.target.value });
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      // Authenticate using email and password only.
      const currentUser = await login({
        email: form.email,
        password: form.password,
      });

      if (!currentUser) {
        const msg =
          "Login succeeded, but the user session could not be loaded.";

        setError(msg);
        toast.error(msg);
        return;
      }

      // Navigate according to the authenticated user's actual backend role.
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

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

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
            className="google-button"
          >
            <svg
              className="google-icon"
              viewBox="0 0 48 48"
              aria-hidden="true"
            >
              <path
                fill="#4285F4"
                d="M43.6 24.5c0-1.4-.1-2.8-.4-4.1H24v7.8h11a9.4 9.4 0 0 1-4.1 6.2v5.1h6.6c3.9-3.6 6.1-8.8 6.1-15z"
              />
              <path
                fill="#34A853"
                d="M24 44c5.5 0 10.1-1.8 13.5-4.9l-6.6-5.1c-1.8 1.2-4.1 2-6.9 2-5.3 0-9.8-3.6-11.4-8.4H5.8v5.3A20 20 0 0 0 24 44z"
              />
              <path
                fill="#FBBC05"
                d="M12.6 27.6a12 12 0 0 1 0-7.2v-5.3H5.8a20 20 0 0 0 0 17.8z"
              />
              <path
                fill="#EA4335"
                d="M24 12.1c3 0 5.7 1 7.8 3.1l5.8-5.8C34.1 6.1 29.5 4 24 4A20 20 0 0 0 5.8 15.1l6.8 5.3c1.6-4.8 6.1-8.3 11.4-8.3z"
              />
            </svg>
            <span>Continue with Google</span>
          </a>
        </form>

        <div className="forgot-password-link">
          <Link to="/forgot-password">Forgot password?</Link>
        </div>

        <p className="auth-footer">
          Don't have an account? <Link to="/register">Create an account</Link>
        </p>

        <p className="technician-access">
          Need role or institution access?{" "}
          <Link to="/technician-access-request">Request access</Link>
        </p>
      </section>

      <aside className="auth-aside">
        <div>
          <p className="eyebrow">A SHARED SPACE FOR DISCOVERY</p>
          <h2>Connect resources. Enable research. Create impact.</h2>
          <p>— The spirit of collaborative research</p>
        </div>
        <span className="aside-caption">Laboratory Resource Management</span>
      </aside>
    </main>
  );
}

export default Login;
