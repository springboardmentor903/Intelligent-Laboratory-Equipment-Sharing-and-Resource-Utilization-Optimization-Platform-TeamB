import { Link } from 'react-router-dom';

export const ForgotPasswordPage = () => {
  return (
    <div className="max-w-md mx-auto my-12 bg-white rounded-xl border border-slate-200 p-8 shadow-xs text-center space-y-4">
      <div className="w-12 h-12 rounded-full bg-amber-50 text-amber-600 mx-auto flex items-center justify-center mb-2">
        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>

      <h1 className="text-2xl font-bold text-slate-900">Password Reset Assistance</h1>

      <div className="rounded-lg bg-amber-50/70 border border-amber-200 p-4 text-xs text-amber-900 text-left space-y-2">
        <p className="font-semibold text-amber-950 flex items-center gap-1.5">
          <svg className="w-4 h-4 text-amber-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Service Availability Notice
        </p>
        <p className="leading-relaxed">
          Automated self-service password reset endpoints are not currently enabled on the backend platform. Password modifications require verification by your department facility manager.
        </p>
      </div>

      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 text-xs text-slate-600 text-left space-y-2">
        <p className="font-semibold text-slate-800">How to request a credential reset:</p>
        <ol className="list-decimal list-inside space-y-1 text-slate-600">
          <li>Contact your department laboratory coordinator or faculty supervisor.</li>
          <li>Provide your registered institutional email and department ID.</li>
          <li>Your administrator will update your access credentials directly.</li>
        </ol>
      </div>

      <div className="pt-2 flex flex-col gap-2">
        <Link
          to="/login"
          className="w-full py-2.5 px-4 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors flex items-center justify-center focus-visible:outline-2 focus-visible:outline-blue-600 cursor-pointer shadow-xs"
        >
          Return to Sign In
        </Link>
        <Link
          to="/register"
          className="text-xs text-slate-500 hover:text-slate-800 transition-colors py-1"
        >
          Need to create a new account? Register
        </Link>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
