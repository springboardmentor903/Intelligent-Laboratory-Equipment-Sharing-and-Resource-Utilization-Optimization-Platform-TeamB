import { Link, useNavigate } from 'react-router-dom';
import BackendHealthCheck from '../components/BackendHealthCheck';
import useAuth from '../hooks/useAuth';

export const DashboardPage = () => {
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
          System Overview &amp; Service Health
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Monitor service connectivity and current session status.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        <BackendHealthCheck />

        <div className="bg-white rounded-lg border border-slate-200 shadow-sm p-6 space-y-4">
          <h2 className="text-base font-semibold text-slate-800 border-b border-slate-100 pb-3">
            Session &amp; Account Status
          </h2>

          <div className="space-y-3 text-sm">
            <div className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100">
              <span className="text-slate-600 font-medium">Session Status</span>
              <span className={`font-semibold px-2 py-0.5 rounded text-xs ${
                isAuthenticated ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
              }`}>
                {isAuthenticated ? 'Authenticated' : 'Guest (Not Signed In)'}
              </span>
            </div>

            {isAuthenticated ? (
              <div className="space-y-2 p-3 bg-slate-50 rounded-lg border border-slate-100 text-xs">
                {user?.fullName && (
                  <div className="flex justify-between">
                    <span className="text-slate-500">Name:</span>
                    <span className="font-medium text-slate-800">{user.fullName}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-slate-500">Email:</span>
                  <span className="font-mono text-slate-800">{user?.email}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Assigned Role:</span>
                  <span className="font-semibold text-blue-700">{user?.role || 'RESEARCHER_STUDENT'}</span>
                </div>
              </div>
            ) : (
              <p className="text-xs text-slate-500 leading-relaxed">
                Sign in with your institutional credentials to view authorized laboratory equipment, manage reservations, and access departmental analytics.
              </p>
            )}

            <div className="pt-2 flex flex-wrap gap-2">
              {isAuthenticated ? (
                <button
                  type="button"
                  onClick={handleLogout}
                  className="px-4 py-2 rounded-md bg-rose-50 text-rose-700 border border-rose-200 text-xs font-medium hover:bg-rose-100 transition-colors focus-visible:outline-2 focus-visible:outline-rose-600 cursor-pointer"
                >
                  Sign Out
                </button>
              ) : (
                <>
                  <Link
                    to="/login"
                    className="inline-flex items-center justify-center px-4 py-2 rounded-md bg-blue-600 text-white text-xs font-medium hover:bg-blue-700 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
                  >
                    Sign In
                  </Link>
                  <Link
                    to="/register"
                    className="inline-flex items-center justify-center px-4 py-2 rounded-md bg-white border border-slate-300 text-slate-700 text-xs font-medium hover:bg-slate-50 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
                  >
                    Create Account
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
