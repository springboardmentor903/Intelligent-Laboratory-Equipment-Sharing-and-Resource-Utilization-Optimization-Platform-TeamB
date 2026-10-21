import { useState, useEffect } from 'react';
import apiClient from '../api/client';

export const BackendHealthCheck = () => {
  const [loading, setLoading] = useState(true);
  const [healthStatus, setHealthStatus] = useState('loading'); // 'loading' | 'online' | 'offline'
  const [responseDetails, setResponseDetails] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [checkedAt, setCheckedAt] = useState(null);

  const checkHealth = async () => {
    setLoading(true);
    setHealthStatus('loading');
    setErrorMessage(null);

    try {
      const response = await apiClient.get('/health');
      setHealthStatus('online');
      setResponseDetails({
        status: response.status,
        statusText: response.statusText,
        data: response.data,
      });
      setCheckedAt(new Date().toLocaleTimeString());
    } catch (err) {
      setHealthStatus('offline');
      const detail =
        err.message ||
        (err.status ? `HTTP ${err.status}` : 'Connection refused / network unreachable');
      setErrorMessage(detail);
      setResponseDetails(err.status ? { status: err.status } : null);
      setCheckedAt(new Date().toLocaleTimeString());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let isMounted = true;

    apiClient
      .get('/health')
      .then((response) => {
        if (!isMounted) return;
        setHealthStatus('online');
        setResponseDetails({
          status: response.status,
          statusText: response.statusText,
          data: response.data,
        });
        setCheckedAt(new Date().toLocaleTimeString());
      })
      .catch((err) => {
        if (!isMounted) return;
        setHealthStatus('offline');
        const detail =
          err.message ||
          (err.status ? `HTTP ${err.status}` : 'Connection refused / network unreachable');
        setErrorMessage(detail);
        setResponseDetails(err.status ? { status: err.status } : null);
        setCheckedAt(new Date().toLocaleTimeString());
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const configuredApiUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

  return (
    <div className="bg-white rounded-lg border border-slate-200 shadow-sm p-6 max-w-xl w-full">
      <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-4">
        <div>
          <h3 className="text-base font-semibold text-slate-800">
            Backend Service Status
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Endpoint: <code className="bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded font-mono text-xs">{configuredApiUrl}/health</code>
          </p>
        </div>
        <button
          type="button"
          onClick={checkHealth}
          disabled={loading}
          aria-label="Re-check backend health status"
          className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 cursor-pointer"
        >
          {loading ? (
            <>
              <svg className="animate-spin h-3.5 w-3.5 text-slate-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
              </svg>
              <span>Checking...</span>
            </>
          ) : (
            <>
              <svg className="w-3.5 h-3.5 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              <span>Re-check</span>
            </>
          )}
        </button>
      </div>

      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <div className="flex items-center">
            {healthStatus === 'online' && (
              <span className="flex h-3 w-3 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
              </span>
            )}
            {healthStatus === 'offline' && (
              <span className="inline-flex rounded-full h-3 w-3 bg-rose-500"></span>
            )}
            {healthStatus === 'loading' && (
              <span className="inline-flex rounded-full h-3 w-3 bg-amber-400 animate-pulse"></span>
            )}
          </div>

          <div className="text-sm font-medium">
            {healthStatus === 'online' && (
              <span className="text-emerald-700">Online — Service Responded</span>
            )}
            {healthStatus === 'offline' && (
              <span className="text-rose-700">Backend Not Reachable</span>
            )}
            {healthStatus === 'loading' && (
              <span className="text-amber-700">Checking service health...</span>
            )}
          </div>
        </div>

        {healthStatus === 'online' && responseDetails && (
          <div className="rounded-md bg-emerald-50 p-3 text-xs text-emerald-800 border border-emerald-200">
            <p className="font-semibold">Response Status: HTTP {responseDetails.status}</p>
            {responseDetails.data && (
              <pre className="mt-1 font-mono text-[11px] overflow-x-auto text-emerald-900 bg-white/60 p-2 rounded">
                {typeof responseDetails.data === 'string'
                  ? responseDetails.data
                  : JSON.stringify(responseDetails.data, null, 2)}
              </pre>
            )}
          </div>
        )}

        {healthStatus === 'offline' && (
          <div className="rounded-md bg-rose-50 p-3 text-xs text-rose-800 border border-rose-200 space-y-1">
            <div className="flex items-start gap-1.5">
              <svg className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <div>
                <p className="font-semibold">Diagnostic Output:</p>
                <p className="mt-0.5 font-mono text-[11px] text-rose-900 bg-white/60 p-1.5 rounded">{errorMessage}</p>
              </div>
            </div>
            <p className="text-[11px] text-rose-600 mt-2">
              Unable to connect to the backend service. Ensure the API server is running and accessible from this environment.
            </p>
          </div>
        )}

        {checkedAt && (
          <div className="text-[11px] text-slate-400 pt-1">
            Last checked: {checkedAt}
          </div>
        )}
      </div>
    </div>
  );
};

export default BackendHealthCheck;
