import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useToast } from "../context/ToastContext";

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const toast = useToast();

  const [message, setMessage] = useState("Completing Google sign-in...");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const completeLogin = async () => {
      const error = searchParams.get("error");

      if (error) {
        if (!cancelled) {
          setFailed(true);
          const msg =
            error === "access_denied"
              ? "Google sign-in was cancelled."
              : "Google sign-in could not be completed.";
          setMessage(msg);
          toast.error(msg);
        }
        return;
      }

      try {
        const user = await refreshUser();

        if (!cancelled) {
          if (user) {
            toast.success("Welcome! Signed in with Google successfully.");
            navigate("/dashboard", { replace: true });
          } else {
            setFailed(true);
            const msg = "Could not verify your session. Please try signing in again.";
            setMessage(msg);
            toast.error(msg);
          }
        }
      } catch {
        if (!cancelled) {
          setFailed(true);
          const msg = "Could not verify your session. Please try signing in again.";
          setMessage(msg);
          toast.error(msg);
        }
      }
    };

    completeLogin();

    return () => {
      cancelled = true;
    };
  }, [navigate, refreshUser, searchParams, toast]);

  return (
    <main className="auth-page">
      <section className="auth-card" style={{ maxWidth: "480px", margin: "auto", textAlign: "center" }}>
        <div className="auth-heading">
          <h1>{failed ? "Sign-In Failed" : "Signing In..."}</h1>
          <p>{message}</p>
        </div>

        {failed && (
          <div style={{ marginTop: "24px", display: "flex", flexDirection: "column", gap: "12px" }}>
            <Link to="/login" className="primary-button" style={{ textDecoration: "none" }}>
              Return to Login
            </Link>
            <Link to="/register" className="secondary-button" style={{ textDecoration: "none" }}>
              Create an Account
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}
