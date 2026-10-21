import { useState } from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import useAuth from '../hooks/useAuth';

export const Navbar = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    setMobileMenuOpen(false);
    navigate('/login');
  };

  const navLinkClasses = ({ isActive }) =>
    `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
      isActive
        ? 'bg-blue-50 text-blue-700 font-semibold border-b-2 border-blue-600'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
    }`;

  const mobileNavLinkClasses = ({ isActive }) =>
    `block px-3 py-2 rounded-md text-base font-medium transition-colors ${
      isActive
        ? 'bg-blue-50 text-blue-700 font-semibold'
        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
    }`;

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-50 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="flex items-center gap-2 text-slate-900 font-bold text-lg hover:text-blue-600 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600 rounded"
            >
              <div className="w-8 h-8 rounded-lg bg-blue-600 text-white flex items-center justify-center font-black text-sm tracking-tighter">
                LE
              </div>
              <span className="hidden sm:inline">Intelligent Lab Platform</span>
              <span className="sm:hidden">Lab Platform</span>
            </Link>
          </div>

          <nav aria-label="Main Navigation" className="hidden md:flex items-center gap-1">
            <NavLink to="/" end className={navLinkClasses}>
              Home
            </NavLink>
            <NavLink to="/dashboard" className={navLinkClasses}>
              Dashboard
            </NavLink>

            {isAuthenticated ? (
              <div className="flex items-center gap-2 ml-2 pl-2 border-l border-slate-200">
                <span className="text-xs font-medium text-slate-700 bg-slate-100 px-2.5 py-1 rounded-full border border-slate-200 max-w-[180px] truncate" title={user?.email || 'User'}>
                  {user?.fullName || user?.email || 'Authenticated'}
                </span>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="px-3 py-1.5 rounded-md text-xs font-medium text-slate-600 hover:text-rose-700 hover:bg-rose-50 transition-colors focus-visible:outline-2 focus-visible:outline-rose-600 cursor-pointer"
                >
                  Sign Out
                </button>
              </div>
            ) : (
              <>
                <NavLink to="/login" className={navLinkClasses}>
                  Sign In
                </NavLink>
                <NavLink to="/register" className={navLinkClasses}>
                  Register
                </NavLink>
              </>
            )}
          </nav>

          <div className="flex md:hidden">
            <button
              type="button"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              aria-controls="mobile-menu"
              aria-expanded={mobileMenuOpen}
              aria-label="Toggle navigation menu"
              className="p-2 rounded-md text-slate-600 hover:text-slate-900 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600"
            >
              {mobileMenuOpen ? (
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              ) : (
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              )}
            </button>
          </div>
        </div>
      </div>

      {mobileMenuOpen && (
        <div id="mobile-menu" className="md:hidden border-t border-slate-200 bg-white px-2 pt-2 pb-3 space-y-1">
          <NavLink
            to="/"
            end
            onClick={() => setMobileMenuOpen(false)}
            className={mobileNavLinkClasses}
          >
            Home
          </NavLink>
          <NavLink
            to="/dashboard"
            onClick={() => setMobileMenuOpen(false)}
            className={mobileNavLinkClasses}
          >
            Dashboard
          </NavLink>

          {isAuthenticated ? (
            <div className="pt-2 border-t border-slate-100 space-y-2">
              <div className="px-3 py-1 text-xs text-slate-500">
                Signed in as <strong className="text-slate-800">{user?.fullName || user?.email}</strong>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="w-full text-left px-3 py-2 rounded-md text-base font-medium text-rose-600 hover:bg-rose-50 cursor-pointer"
              >
                Sign Out
              </button>
            </div>
          ) : (
            <>
              <NavLink
                to="/login"
                onClick={() => setMobileMenuOpen(false)}
                className={mobileNavLinkClasses}
              >
                Sign In
              </NavLink>
              <NavLink
                to="/register"
                onClick={() => setMobileMenuOpen(false)}
                className={mobileNavLinkClasses}
              >
                Register
              </NavLink>
            </>
          )}
        </div>
      )}
    </header>
  );
};

export default Navbar;
