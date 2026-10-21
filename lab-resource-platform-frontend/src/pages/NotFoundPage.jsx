import { Link } from 'react-router-dom';

export const NotFoundPage = () => {
  return (
    <div className="max-w-md mx-auto my-16 bg-white rounded-xl border border-slate-200 p-8 shadow-xs text-center space-y-4">
      <div className="w-12 h-12 rounded-full bg-rose-50 text-rose-600 mx-auto flex items-center justify-center font-bold text-lg">
        404
      </div>
      <h1 className="text-xl font-bold text-slate-900">Page Not Found</h1>
      <p className="text-sm text-slate-600">
        The requested page could not be found. Please check the URL or return to the homepage.
      </p>
      <div className="pt-2">
        <Link
          to="/"
          className="inline-flex items-center justify-center px-4 py-2 rounded-lg bg-blue-600 text-white font-medium text-sm hover:bg-blue-700 transition-colors focus-visible:outline-2 focus-visible:outline-blue-600 cursor-pointer"
        >
          Return to Home
        </Link>
      </div>
    </div>
  );
};

export default NotFoundPage;
