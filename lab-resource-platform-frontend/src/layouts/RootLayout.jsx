import { Outlet } from 'react-router-dom';
import Navbar from '../components/Navbar';

export const RootLayout = () => {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
      <Navbar />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>

      <footer className="bg-white border-t border-slate-200 py-6 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-2">
          <div>
            Intelligent Laboratory Equipment Sharing &amp; Resource Utilization Optimization Platform
          </div>
          <div className="text-slate-400">
            Centralized Lab Equipment Management &amp; Resource Scheduling
          </div>
        </div>
      </footer>
    </div>
  );
};

export default RootLayout;
