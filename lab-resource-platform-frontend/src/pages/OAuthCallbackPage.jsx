
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const OAuthCallbackPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { refreshUser } = useAuth();

  const [message, setMessage] = useState('Completing Google sign-in...');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const completeLogin = async () => {
      const error = searchParams.get('error');

      if (error) {
        if (!cancelled) {
          setFailed(true);
          setMessage(
            error === 'access_denied'
              ? 'Google sign-in was cancelled.'
              : 'Google sign-in could not be completed.'
          );
        }
        return;
      }

      try {
        await refreshUser();

        if (!cancelled) {
          navigate('/', { replace: true });
        }
      } catch {
        if (!cancelled) {
          setFailed(true);
          setMessage(
            'Could not verify your session. Please try signing in again.'
          );
        }
      }
    };

    completeLogin();

    return () => {
      cancelled = true;
    };
  }, [navigate, refreshUser, searchParams]);

  return (
    <div className="max-w-md mx-auto my-12 bg-white rounded-xl border border-slate-200 p-8 shadow-xs text-center space-y-4">
      <h1 className="text-2xl font-bold text-slate-900">
        {failed ? 'Google Sign-In Failed' : 'Google Sign-In'}
      </h1>

      <p className="text-sm text-slate-600">{message}</p>

      {failed && (
        <div className="pt-2 flex flex-col gap-2">
          <Link
            to="/login"
            className="w-full py-2.5 px-4 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700"
          >
            Return to Sign In
          </Link>

          <Link
            to="/register"
            className="text-sm text-slate-500 hover:text-slate-800"
          >
            Create an account
          </Link>
        </div>
      )}
    </div>
  );
};

export default OAuthCallbackPage;
