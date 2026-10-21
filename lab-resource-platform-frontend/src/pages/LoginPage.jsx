import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { loginUser, getGoogleAuthUrl } from '../api/auth';
import useAuth from '../hooks/useAuth';

const GoogleIcon = () => (
  <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24" aria-hidden="true">
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
);

export const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated } = useAuth();

  const [formData, setFormData] = useState(() => {
    let savedEmail = '';
    let rememberMe = false;
    try {
      savedEmail = localStorage.getItem('remembered_email') || '';
      rememberMe = Boolean(savedEmail);
    } catch {
      // Graceful fallback if storage is restricted
    }
    return {
      email: savedEmail,
      password: '',
      rememberMe,
    };
  });

  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // If already authenticated, redirect to dashboard or requested page
  useEffect(() => {
    if (isAuthenticated) {
      const from = location.state?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, location]);

  const validate = () => {
    const errors = {};
    const emailTrimmed = formData.email.trim();

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailTrimmed) {
      errors.email = 'Email address is required.';
    } else if (!emailRegex.test(emailTrimmed)) {
      errors.email = 'Please enter a valid email address.';
    }

    if (!formData.password) {
      errors.password = 'Password is required.';
    }

    return errors;
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    if (fieldErrors[name]) {
      setFieldErrors((prev) => ({ ...prev, [name]: null }));
    }
    if (submitError) {
      setSubmitError(null);
    }
  };

  const handleGoogleSignIn = () => {
    if (isSubmitting || isGoogleSubmitting) return;

    const authUrl = getGoogleAuthUrl();

    // Check if an authorization initiation endpoint is configured
    if (!authUrl) {
      setSubmitError(
        'Google sign-in is currently unavailable because OAuth2 is not configured on the backend server. Please sign in with your email and password.'
      );
      return;
    }

    setIsGoogleSubmitting(true);
    setSubmitError(null);

    // Redirect to the verified backend OAuth2 authorization endpoint
    window.location.href = authUrl;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isSubmitting || isGoogleSubmitting) return;

    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const response = await loginUser({
        email: formData.email.trim().toLowerCase(),
        password: formData.password,
      });

      // Update remembered email preference
      try {
        if (formData.rememberMe) {
          localStorage.setItem('remembered_email', formData.email.trim());
        } else {
          localStorage.removeItem('remembered_email');
        }
      } catch {
        // Ignore web storage errors
      }

      // The backend may return a raw JWT string or an object with token/accessToken
      let token = null;
      let userData = null;

      if (typeof response === 'string') {
        const trimmed = response.trim();
        if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
          try {
            const parsed = JSON.parse(trimmed);
            token = parsed.token || parsed.accessToken || parsed.jwt || null;
            userData = parsed.user || null;
          } catch {
            token = trimmed;
          }
        } else if (trimmed) {
          token = trimmed;
        }
      } else if (response && typeof response === 'object') {
        token =
          response.token ||
          response.accessToken ||
          response.jwt ||
          response.data?.token ||
          null;
        userData = response.user || response.data?.user || null;
      }

      if (!token && !userData) {
        throw new Error('Authentication succeeded but the server response did not include a valid session token.');
      }

      login(token, userData, formData.rememberMe);

      const from = location.state?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    } catch (err) {
      const message =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Authentication failed. Please verify your credentials.';
      setSubmitError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto my-10 bg-white rounded-xl border border-slate-200 p-8 shadow-xs">
      <div className="text-center mb-6">
        <div className="w-12 h-12 rounded-full bg-blue-50 text-blue-600 mx-auto flex items-center justify-center mb-3">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-slate-900">Sign In</h1>
        <p className="text-xs text-slate-500 mt-1">
          Access your equipment reservations and facility schedules
        </p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {submitError && (
          <div className="rounded-lg bg-rose-50 border border-rose-200 p-3.5 text-xs text-rose-800 flex items-start gap-2">
            <svg className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div>
              <p className="font-semibold">Sign-In Error</p>
              <p className="mt-0.5">{submitError}</p>
            </div>
          </div>
        )}

        <div>
          <label htmlFor="email" className="block text-xs font-semibold text-slate-700 mb-1">
            Email Address <span className="text-rose-500">*</span>
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            disabled={isSubmitting || isGoogleSubmitting}
            value={formData.email}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            placeholder="name@university.edu"
            className={`w-full px-3 py-2 text-sm rounded-lg border transition-colors bg-white ${
              fieldErrors.email
                ? 'border-rose-400 focus:border-rose-500 ring-1 ring-rose-400'
                : 'border-slate-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500'
            }`}
          />
          {fieldErrors.email && (
            <p id="email-error" className="text-xs text-rose-600 mt-1">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <label htmlFor="password" className="block text-xs font-semibold text-slate-700">
              Password <span className="text-rose-500">*</span>
            </label>
            <Link
              to="/forgot-password"
              className="text-xs text-blue-600 hover:text-blue-800 font-medium focus-visible:outline-2 focus-visible:outline-blue-600 rounded"
            >
              Forgot password?
            </Link>
          </div>
          <div className="relative">
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              disabled={isSubmitting || isGoogleSubmitting}
              value={formData.password}
              onChange={handleChange}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              className={`w-full px-3 py-2 pr-10 text-sm rounded-lg border transition-colors bg-white ${
                fieldErrors.password
                  ? 'border-rose-400 focus:border-rose-500 ring-1 ring-rose-400'
                  : 'border-slate-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500'
              }`}
            />
            <button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-600 focus:outline-none cursor-pointer"
            >
              {showPassword ? (
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l18 18" />
                </svg>
              ) : (
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              )}
            </button>
          </div>
          {fieldErrors.password && (
            <p id="password-error" className="text-xs text-rose-600 mt-1">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <div className="flex items-center">
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              name="rememberMe"
              checked={formData.rememberMe}
              onChange={handleChange}
              disabled={isSubmitting || isGoogleSubmitting}
              className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 cursor-pointer"
            />
            <span className="text-xs text-slate-600 font-medium">Remember me on this browser</span>
          </label>
        </div>

        <button
          type="submit"
          disabled={isSubmitting || isGoogleSubmitting}
          className="w-full py-2.5 px-4 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-blue-600 flex items-center justify-center gap-2 cursor-pointer shadow-xs"
        >
          {isSubmitting ? (
            <>
              <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
              </svg>
              <span>Signing In...</span>
            </>
          ) : (
            <span>Sign In</span>
          )}
        </button>

        <div className="relative my-4">
          <div className="absolute inset-0 flex items-center" aria-hidden="true">
            <div className="w-full border-t border-slate-200"></div>
          </div>
          <div className="relative flex justify-center text-xs">
            <span className="bg-white px-3 text-slate-500 font-medium">Or continue with</span>
          </div>
        </div>

        <button
          type="button"
          onClick={handleGoogleSignIn}
          disabled={isSubmitting || isGoogleSubmitting}
          className="w-full py-2.5 px-4 rounded-lg border border-slate-300 bg-white text-slate-700 text-sm font-medium hover:bg-slate-50 hover:border-slate-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-blue-600 flex items-center justify-center gap-2.5 cursor-pointer shadow-xs"
        >
          {isGoogleSubmitting ? (
            <>
              <svg className="animate-spin h-4 w-4 text-slate-600" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
              </svg>
              <span>Connecting to Google...</span>
            </>
          ) : (
            <>
              <GoogleIcon />
              <span>Continue with Google</span>
            </>
          )}
        </button>

        <div className="pt-2 text-center text-xs text-slate-600">
          Need an account?{' '}
          <Link
            to="/register"
            className="text-blue-600 hover:text-blue-800 font-semibold focus-visible:outline-2 focus-visible:outline-blue-600 rounded"
          >
            Register
          </Link>
        </div>
      </form>
    </div>
  );
};

export default LoginPage;
