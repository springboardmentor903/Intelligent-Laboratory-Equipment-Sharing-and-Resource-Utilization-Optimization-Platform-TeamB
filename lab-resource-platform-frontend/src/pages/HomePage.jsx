import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

export const HomePage = () => {
  const { isAuthenticated, user } = useAuth();

  return (
    <div className="space-y-8">
      <div className="bg-white rounded-xl border border-slate-200 p-8 shadow-xs">
        <div className="max-w-3xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-50 border border-blue-200 text-blue-700 text-xs font-semibold uppercase tracking-wider mb-4">
            Campus Resource Sharing
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight leading-tight">
            Intelligent Laboratory Equipment Sharing &amp; Resource Utilization Platform
          </h1>
          <p className="mt-4 text-base sm:text-lg text-slate-600 leading-relaxed">
            A centralized platform designed to connect research facilities, schedule advanced analytical instruments, prevent booking conflicts, and optimize equipment utilization across academic departments.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <Link
              to="/dashboard"
              className="inline-flex items-center justify-center px-5 py-2.5 rounded-lg bg-blue-600 text-white font-medium text-sm hover:bg-blue-700 transition-colors shadow-xs focus-visible:outline-2 focus-visible:outline-blue-600 cursor-pointer"
            >
              {isAuthenticated ? 'Open Dashboard' : 'View System Dashboard'}
            </Link>
            {!isAuthenticated ? (
              <>
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center px-5 py-2.5 rounded-lg bg-white border border-slate-300 text-slate-700 font-medium text-sm hover:bg-slate-50 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600 cursor-pointer"
                >
                  Sign In
                </Link>
                <Link
                  to="/register"
                  className="inline-flex items-center justify-center px-5 py-2.5 rounded-lg bg-white border border-slate-300 text-slate-700 font-medium text-sm hover:bg-slate-50 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600 cursor-pointer"
                >
                  Create Account
                </Link>
              </>
            ) : (
              <span className="text-xs text-slate-500">
                Welcome back, <strong>{user?.fullName || user?.email}</strong>
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-xs">
          <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-sm mb-4">
            01
          </div>
          <h2 className="text-base font-semibold text-slate-900 mb-2">Resource Scheduling</h2>
          <p className="text-xs text-slate-600 leading-relaxed">
            Browse campus laboratory inventory, check instrument availability in real time, and request time slots with automatic conflict prevention.
          </p>
        </div>

        <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-xs">
          <div className="w-10 h-10 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-sm mb-4">
            02
          </div>
          <h2 className="text-base font-semibold text-slate-900 mb-2">Equipment Management</h2>
          <p className="text-xs text-slate-600 leading-relaxed">
            Track operational status, routine maintenance schedules, calibration logs, and safety certifications across departmental facilities.
          </p>
        </div>

        <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-xs">
          <div className="w-10 h-10 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-sm mb-4">
            03
          </div>
          <h2 className="text-base font-semibold text-slate-900 mb-2">Role-Based Access</h2>
          <p className="text-xs text-slate-600 leading-relaxed">
            Dedicated views tailored for researchers, laboratory technicians, facility managers, and institutional administrators.
          </p>
        </div>
      </div>
    </div>
  );
};

export default HomePage;
