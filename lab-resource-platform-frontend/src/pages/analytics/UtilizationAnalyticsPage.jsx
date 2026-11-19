import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import * as d3 from "d3";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from "recharts";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import {
  FiBarChart2,
  FiGlobe,
  FiTrendingUp,
  FiCalendar,
  FiClock,
  FiZap,
  FiMoon,
  FiAlertTriangle,
  FiCheckCircle,
  FiList,
  FiArrowLeft,
  FiArrowRight,
} from "react-icons/fi";

// Format helper
function fmtHours(mins) {
  if (mins === null || mins === undefined) return "0.0h";
  return (mins / 60).toFixed(1) + "h";
}

function fmtPercent(val) {
  if (val === null || val === undefined) return "N/A";
  return val.toFixed(1) + "%";
}

export default function UtilizationAnalyticsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // Role checks
  const isSysAdmin = user?.role === "SYSTEM_ADMINISTRATOR";
  const isDeptHead = user?.role === "DEPARTMENT_HEAD";
  const isInstStaff =
    user?.role === "INSTITUTION_ADMINISTRATOR" || user?.role === "LAB_MANAGER";

  // Metadata dropdowns
  const [colleges, setColleges] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [equipments, setEquipments] = useState([]);

  // Active filters from URL query params (or defaults)
  const defaultScope = isDeptHead ? "DEPARTMENT" : "INSTITUTION";
  const scope = searchParams.get("scope") || defaultScope;
  const scopeId = searchParams.get("scopeId") || "";
  const period = searchParams.get("period") || "LAST_7_DAYS";
  const startDate = searchParams.get("startDate") || "";
  const endDate = searchParams.get("endDate") || "";
  const highDemandOnly = searchParams.get("highDemandOnly") === "true";
  const page = parseInt(searchParams.get("page") || "0", 10);

  // Custom date range local state for inputs
  const [customStart, setCustomStart] = useState(startDate);
  const [customEnd, setCustomEnd] = useState(endDate);

  // Independent Section States
  const [summary, setSummary] = useState({ loading: true, error: null, data: null });
  const [benchmark, setBenchmark] = useState({ loading: true, error: null, data: null });
  const [heatmap, setHeatmap] = useState({ loading: true, error: null, data: null });
  const [peakUsage, setPeakUsage] = useState({ loading: true, error: null, data: null });
  const [idleTime, setIdleTime] = useState({ loading: true, error: null, data: null });
  const [idleAlerts, setIdleAlerts] = useState({ loading: true, error: null, data: null });
  const [demand, setDemand] = useState({ loading: true, error: null, data: null });

  // Heatmap hover tooltip
  const [hoveredSlot, setHoveredSlot] = useState(null);

  // Update query params helper
  const updateParams = useCallback(
    (newParams) => {
      const current = Object.fromEntries(searchParams.entries());
      const merged = { ...current, ...newParams };
      // Remove empty keys
      Object.keys(merged).forEach((k) => {
        if (!merged[k] && merged[k] !== false && merged[k] !== 0) delete merged[k];
      });
      setSearchParams(merged);
    },
    [searchParams, setSearchParams]
  );

  // Load metadata for filters on mount
  useEffect(() => {
    // 1. Colleges
    api.get("/api/colleges")
      .then((res) => setColleges(res.data || []))
      .catch((err) => console.error("Colleges load failed", err));

    // 2. Departments
    api.get("/api/departments")
      .then((res) => setDepartments(res.data || []))
      .catch((err) => console.error("Departments load failed", err));

    // 3. Equipment
    api.get("/api/equipment")
      .then((res) => setEquipments(res.data || []))
      .catch((err) => console.error("Equipment load failed", err));
  }, []);

  // Filter available scopes & scope options according to logged in user role
  const filteredDepartments = useMemo(() => {
    if (isDeptHead && user?.departmentId) {
      return departments.filter((d) => d.id === user.departmentId);
    }
    if (isInstStaff) {
      const userCol = user?.collegeId || user?.institutionId;
      if (userCol) {
        return departments.filter((d) => (d.collegeId || d.institutionId) === userCol);
      }
    }
    return departments;
  }, [departments, isDeptHead, isInstStaff, user]);

  const filteredEquipments = useMemo(() => {
    if (isDeptHead && user?.departmentId) {
      return equipments.filter((e) => e.departmentId === user.departmentId);
    }
    if (isInstStaff) {
      const userCol = user?.collegeId || user?.institutionId;
      if (userCol) {
        return equipments.filter((e) => (e.collegeId || e.institutionId) === userCol);
      }
    }
    return equipments;
  }, [equipments, isDeptHead, isInstStaff, user]);

  // Construct current query params object
  const currentQueryParams = useMemo(() => {
    const q = { scope, period };
    if (scopeId) q.scopeId = scopeId;
    if (period === "CUSTOM") {
      if (startDate) q.startDate = startDate;
      if (endDate) q.endDate = endDate;
    }
    return q;
  }, [scope, scopeId, period, startDate, endDate]);

  // ===========================================================================
  // Fetch Handlers (Independent for each section)
  // ===========================================================================

  const fetchSummary = useCallback(async () => {
    setSummary((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/analytics/utilization/summary", { params: currentQueryParams });
      setSummary({ loading: false, error: null, data: res.data });
    } catch (err) {
      setSummary({ loading: false, error: err.response?.data?.message || "Failed to load summary", data: null });
    }
  }, [currentQueryParams]);

  const fetchBenchmark = useCallback(async () => {
    setBenchmark((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/analytics/utilization/benchmark", { params: currentQueryParams });
      setBenchmark({ loading: false, error: null, data: res.data });
    } catch (err) {
      setBenchmark({ loading: false, error: err.response?.data?.message || "Failed to load benchmark", data: null });
    }
  }, [currentQueryParams]);

  const fetchHeatmap = useCallback(async () => {
    setHeatmap((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/analytics/utilization/heatmap", { params: currentQueryParams });
      setHeatmap({ loading: false, error: null, data: res.data });
    } catch (err) {
      setHeatmap({ loading: false, error: err.response?.data?.message || "Failed to load heatmap", data: null });
    }
  }, [currentQueryParams]);

  const fetchPeakUsage = useCallback(async () => {
    setPeakUsage((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/analytics/utilization/peak-usage", { params: currentQueryParams });
      setPeakUsage({ loading: false, error: null, data: res.data });
    } catch (err) {
      setPeakUsage({ loading: false, error: err.response?.data?.message || "Failed to load peak usage", data: null });
    }
  }, [currentQueryParams]);

  const fetchIdleTime = useCallback(async () => {
    setIdleTime((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/analytics/utilization/idle-time", { params: currentQueryParams });
      setIdleTime({ loading: false, error: null, data: res.data });
    } catch (err) {
      setIdleTime({ loading: false, error: err.response?.data?.message || "Failed to load idle time", data: null });
    }
  }, [currentQueryParams]);

  const fetchIdleAlerts = useCallback(async () => {
    setIdleAlerts((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const q = { scope };
      if (scopeId) q.scopeId = scopeId;
      const res = await api.get("/api/analytics/utilization/idle-alerts", { params: q });
      setIdleAlerts({ loading: false, error: null, data: res.data });
    } catch (err) {
      setIdleAlerts({ loading: false, error: err.response?.data?.message || "Failed to load idle alerts", data: null });
    }
  }, [scope, scopeId]);

  const fetchDemand = useCallback(async () => {
    setDemand((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const q = { ...currentQueryParams, highDemandOnly, page, size: 10 };
      const res = await api.get("/api/analytics/utilization/demand", { params: q });
      setDemand({ loading: false, error: null, data: res.data });
    } catch (err) {
      setDemand({ loading: false, error: err.response?.data?.message || "Failed to load demand data", data: null });
    }
  }, [currentQueryParams, highDemandOnly, page]);

  // Reload when query params change
  useEffect(() => {
    fetchSummary();
    fetchBenchmark();
    fetchHeatmap();
    fetchPeakUsage();
    fetchIdleTime();
    fetchIdleAlerts();
    fetchDemand();
  }, [fetchSummary, fetchBenchmark, fetchHeatmap, fetchPeakUsage, fetchIdleTime, fetchIdleAlerts, fetchDemand]);

  // Handle Custom Date Range Submit
  const handleApplyCustomDates = (e) => {
    e.preventDefault();
    if (!customStart || !customEnd) {
      toast.error("Please provide both start and end dates.");
      return;
    }
    const s = new Date(customStart);
    const end = new Date(customEnd);
    if (end < s) {
      toast.error("End date cannot be earlier than start date.");
      return;
    }
    const diffDays = Math.ceil((end - s) / (1000 * 60 * 60 * 24)) + 1;
    if (diffDays > 90) {
      toast.error(`Date range cannot exceed 90 days (selected: ${diffDays} days).`);
      return;
    }
    updateParams({ period: "CUSTOM", startDate: customStart, endDate: customEnd });
  };

  // D3 Heatmap Color Scale Calculator
  const heatmapColorScale = useMemo(() => {
    return d3.scaleSequential(d3.interpolateBlues).domain([0, 100]);
  }, []);

  const getHeatmapColor = (pct) => {
    if (pct === null || pct === undefined) {
      return "#f1f5f9"; // Closed / Unavailable (Slate 100)
    }
    if (pct === 0) {
      return "#ffffff"; // Open but unused (White)
    }
    return heatmapColorScale(Math.min(100, Math.max(10, pct)));
  };

  // Day rows for heatmap
  const DAYS = [
    { num: 1, name: "Mon" },
    { num: 2, name: "Tue" },
    { num: 3, name: "Wed" },
    { num: 4, name: "Thu" },
    { num: 5, name: "Fri" },
    { num: 6, name: "Sat" },
    { num: 7, name: "Sun" },
  ];

  const HOURS = Array.from({ length: 24 }, (_, i) => i);

  // Group heatmap slots by day and hour for O(1) lookup
  const heatmapLookup = useMemo(() => {
    if (!heatmap.data) return {};
    const map = {};
    heatmap.data.forEach((slot) => {
      map[`${slot.dayOfWeek}-${slot.hour}`] = slot;
    });
    return map;
  }, [heatmap.data]);

  return (
    <div className="analytics-page-container">
      {/* Page Header */}
      <div className="analytics-header">
        <div>
          <h1 className="analytics-title" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <FiBarChart2 color="#2563eb" /> Utilization Analytics &amp; Capacity Engine
          </h1>
          <p className="analytics-subtitle">
            Comprehensive lab utilization, capacity benchmarks, idle detection, peak usage, and demand analytics.
          </p>
        </div>
        <div className="analytics-tz-badge" style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
          <FiGlobe /> Time Zone: <strong>Asia/Kolkata (IST)</strong>
        </div>
      </div>

      {/* SECTION A: FILTERS */}
      <div className="analytics-filter-card">
        <div className="analytics-filter-row">
          {/* Scope Selector */}
          <div className="analytics-filter-group">
            <label className="analytics-filter-label">Scope</label>
            <div className="analytics-btn-group">
              {!isDeptHead && (
                <button
                  type="button"
                  className={`analytics-pill-btn ${scope === "INSTITUTION" ? "active" : ""}`}
                  onClick={() => updateParams({ scope: "INSTITUTION", scopeId: "" })}
                >
                  Institution
                </button>
              )}
              <button
                type="button"
                className={`analytics-pill-btn ${scope === "DEPARTMENT" ? "active" : ""}`}
                onClick={() => updateParams({ scope: "DEPARTMENT", scopeId: "" })}
              >
                Department
              </button>
              <button
                type="button"
                className={`analytics-pill-btn ${scope === "EQUIPMENT" ? "active" : ""}`}
                onClick={() => updateParams({ scope: "EQUIPMENT", scopeId: "" })}
              >
                Equipment
              </button>
            </div>
          </div>

          {/* Scope Target Dropdown */}
          {scope === "INSTITUTION" && isSysAdmin && (
            <div className="analytics-filter-group">
              <label className="analytics-filter-label">Select Institution</label>
              <select
                className="analytics-select"
                value={scopeId}
                onChange={(e) => updateParams({ scopeId: e.target.value })}
              >
                <option value="">All Affiliated Institutions</option>
                {colleges.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.code})
                  </option>
                ))}
              </select>
            </div>
          )}

          {scope === "DEPARTMENT" && (
            <div className="analytics-filter-group">
              <label className="analytics-filter-label">Select Department</label>
              <select
                className="analytics-select"
                value={scopeId}
                onChange={(e) => updateParams({ scopeId: e.target.value })}
              >
                <option value="">Choose Department...</option>
                {filteredDepartments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} {d.code ? `(${d.code})` : ""}
                  </option>
                ))}
              </select>
            </div>
          )}

          {scope === "EQUIPMENT" && (
            <div className="analytics-filter-group">
              <label className="analytics-filter-label">Select Equipment</label>
              <select
                className="analytics-select"
                value={scopeId}
                onChange={(e) => updateParams({ scopeId: e.target.value })}
              >
                <option value="">Choose Instrument...</option>
                {filteredEquipments.map((eq) => (
                  <option key={eq.id} value={eq.id}>
                    {eq.name} [{eq.assetTag || `EQ-${eq.id}`}]
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Period Selector */}
          <div className="analytics-filter-group">
            <label className="analytics-filter-label">Time Period</label>
            <div className="analytics-btn-group">
              {[
                { key: "TODAY", label: "Today" },
                { key: "LAST_7_DAYS", label: "Last 7 Days" },
                { key: "LAST_30_DAYS", label: "Last 30 Days" },
                { key: "CUSTOM", label: "Custom Range" },
              ].map((p) => (
                <button
                  key={p.key}
                  type="button"
                  className={`analytics-pill-btn ${period === p.key ? "active" : ""}`}
                  onClick={() => updateParams({ period: p.key })}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Custom Range Bar */}
        {period === "CUSTOM" && (
          <form className="analytics-custom-range-row" onSubmit={handleApplyCustomDates}>
            <div className="analytics-date-input-group">
              <label>Start Date:</label>
              <input
                type="date"
                className="analytics-date-input"
                value={customStart}
                onChange={(e) => setCustomStart(e.target.value)}
              />
            </div>
            <div className="analytics-date-input-group">
              <label>End Date:</label>
              <input
                type="date"
                className="analytics-date-input"
                value={customEnd}
                onChange={(e) => setCustomEnd(e.target.value)}
              />
            </div>
            <button type="submit" className="analytics-apply-btn">
              Apply Range (Max 90d)
            </button>
          </form>
        )}
      </div>

      {/* SECTION B & C: SUMMARY METRICS & BENCHMARK */}
      <div className="analytics-grid-row">
        {/* Summary Card */}
        <div className="analytics-card flex-2">
          <div className="analytics-card-header">
            <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <FiTrendingUp color="#2563eb" /> Utilization Summary &amp; Capacity Targets
            </h2>
            {summary.loading && <span className="analytics-spinner" />}
          </div>

          {summary.error ? (
            <div className="analytics-error-box">
              <span>{summary.error}</span>
              <button type="button" onClick={fetchSummary} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : summary.loading ? (
            <div className="analytics-skeleton-box">Computing utilization metrics...</div>
          ) : !summary.data ? (
            <div className="analytics-empty-box">No utilization records found for this scope.</div>
          ) : (
            <div className="analytics-metrics-grid">
              {/* Metric 1: Rate */}
              <div className="analytics-metric-box primary">
                <div className="analytics-metric-label">Utilization Rate</div>
                <div className="analytics-metric-value">
                  {fmtPercent(summary.data.utilizationRate)}
                </div>
                <div className="analytics-metric-sub">
                  Based on actual usage logs clipped to operating hours
                </div>
              </div>

              {/* Metric 2: Used vs Available Hours */}
              <div className="analytics-metric-box">
                <div className="analytics-metric-label">Used vs Available Hours</div>
                <div className="analytics-metric-value">
                  {summary.data.totalUsedHours}h / {summary.data.totalAvailableHours}h
                </div>
                <div className="analytics-metric-sub">
                  {summary.data.totalUsedMinutes}m used of {summary.data.totalAvailableMinutes}m capacity
                </div>
              </div>

              {/* Metric 3: Shared Usage */}
              <div className="analytics-metric-box">
                <div className="analytics-metric-label">Shared Usage</div>
                <div className="analytics-metric-value">
                  {fmtPercent(summary.data.sharedUsagePercent)}
                </div>
                <div className="analytics-metric-sub">
                  {fmtHours(summary.data.sharedUsageMinutes)} external/cross-dept sharing
                </div>
              </div>

              {/* Metric 4: Target & Gap */}
              <div className="analytics-metric-box">
                <div className="analytics-metric-label">Target & Variance</div>
                <div className="analytics-metric-value">
                  Target: {fmtPercent(summary.data.targetUtilizationPercent)}
                </div>
                <div className="analytics-metric-sub">
                  Gap:{" "}
                  <strong
                    style={{
                      color:
                        (summary.data.targetGap || 0) >= 0 ? "#16a34a" : "#dc2626",
                    }}
                  >
                    {(summary.data.targetGap || 0) > 0 ? "+" : ""}
                    {fmtPercent(summary.data.targetGap)}
                  </strong>{" "}
                  ({summary.data.targetStatus})
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Benchmark Card */}
        <div className="analytics-card flex-1">
          <div className="analytics-card-header">
            <h2 className="analytics-card-title">⏱ 28-Day Benchmark Comparison</h2>
            {benchmark.loading && <span className="analytics-spinner" />}
          </div>

          {benchmark.error ? (
            <div className="analytics-error-box">
              <span>{benchmark.error}</span>
              <button type="button" onClick={fetchBenchmark} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : benchmark.loading ? (
            <div className="analytics-skeleton-box">Evaluating preceding 28 days...</div>
          ) : !benchmark.data ? (
            <div className="analytics-empty-box">Insufficient historical data.</div>
          ) : (
            <div className="analytics-benchmark-body">
              <div className="benchmark-stat-row">
                <span>Current Period:</span>
                <strong>{fmtPercent(benchmark.data.currentPeriodUtilization)}</strong>
              </div>
              <div className="benchmark-stat-row">
                <span>Preceding 28-Day Benchmark:</span>
                <strong>{fmtPercent(benchmark.data.benchmarkPeriodUtilization)}</strong>
              </div>
              <div className="benchmark-diff-badge-box">
                <span className="diff-label">Variance:</span>
                <span
                  className={`diff-badge ${
                    benchmark.data.comparisonStatus === "HIGHER"
                      ? "higher"
                      : benchmark.data.comparisonStatus === "LOWER"
                      ? "lower"
                      : "stable"
                  }`}
                >
                  {benchmark.data.differencePercentagePoints > 0 ? "+" : ""}
                  {benchmark.data.differencePercentagePoints}% pts (
                  {benchmark.data.comparisonStatus})
                </span>
              </div>
              <div className="benchmark-dates-footnote">
                Benchmark Window: {benchmark.data.benchmarkStartDate} to{" "}
                {benchmark.data.benchmarkEndDate}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* SECTION D: HEATMAP (168 SLOTS: 7 DAYS x 24 HOURS) */}
      <div className="analytics-card">
        <div className="analytics-card-header">
          <div>
            <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <FiCalendar color="#2563eb" /> Weekly Utilization Heatmap (168 Slots)
            </h2>
            <p className="analytics-card-sub">
              Hourly utilization across Monday to Sunday. Closed operating hours appear in grey, open slots scale light to dark.
            </p>
          </div>
          <div className="heatmap-legend">
            <span className="legend-item">
              <span className="legend-swatch closed" /> Closed / Outside Operating Hours
            </span>
            <span className="legend-item">
              <span className="legend-swatch open-idle" /> 0% (Open / Unused)
            </span>
            <span className="legend-item">
              <span className="legend-swatch low" /> 1-25%
            </span>
            <span className="legend-item">
              <span className="legend-swatch med" /> 26-50%
            </span>
            <span className="legend-item">
              <span className="legend-swatch high" /> 51-75%
            </span>
            <span className="legend-item">
              <span className="legend-swatch max" /> 76-100%
            </span>
          </div>
        </div>

        {heatmap.error ? (
          <div className="analytics-error-box">
            <span>{heatmap.error}</span>
            <button type="button" onClick={fetchHeatmap} className="analytics-retry-btn">
              Retry
            </button>
          </div>
        ) : heatmap.loading ? (
          <div className="analytics-skeleton-box">Building 168-cell matrix...</div>
        ) : !heatmap.data || heatmap.data.length === 0 ? (
          <div className="analytics-empty-box">No heatmap entries found.</div>
        ) : (
          <div className="heatmap-overflow-container">
            <div className="heatmap-grid">
              {/* Header row: 24 hours */}
              <div className="heatmap-header-row">
                <div className="heatmap-corner-cell">Day / Hour</div>
                {HOURS.map((h) => (
                  <div key={h} className="heatmap-hour-header">
                    {String(h).padStart(2, "0")}h
                  </div>
                ))}
              </div>

              {/* 7 Day Rows */}
              {DAYS.map((d) => (
                <div key={d.num} className="heatmap-day-row">
                  <div className="heatmap-day-label">{d.name}</div>
                  {HOURS.map((h) => {
                    const slot = heatmapLookup[`${d.num}-${h}`];
                    const pct = slot ? slot.utilizationPercent : null;
                    const isClosed = pct === null;

                    return (
                      <div
                        key={h}
                        className={`heatmap-cell ${isClosed ? "is-closed" : ""}`}
                        style={{ backgroundColor: getHeatmapColor(pct) }}
                        onMouseEnter={() => setHoveredSlot(slot || { dayName: d.name, hour: h, utilizationPercent: null })}
                        onMouseLeave={() => setHoveredSlot(null)}
                      >
                        {!isClosed && pct > 0 && <span className="cell-pct">{Math.round(pct)}%</span>}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>

            {/* Hover Tooltip display */}
            {hoveredSlot && (
              <div className="heatmap-hover-info">
                <strong>{hoveredSlot.dayName}</strong> ({String(hoveredSlot.hour).padStart(2, "0")}:00 -{" "}
                {String((hoveredSlot.hour + 1) % 24).padStart(2, "0")}:00) —{" "}
                {hoveredSlot.utilizationPercent === null ? (
                  <span className="tooltip-closed">Closed (Outside Operating Hours)</span>
                ) : (
                  <span>
                    Utilization: <strong>{hoveredSlot.utilizationPercent}%</strong> | Used:{" "}
                    <strong>{hoveredSlot.usedMinutes || 0}m</strong> / Available:{" "}
                    <strong>{hoveredSlot.availableMinutes || 0}m</strong>
                  </span>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* SECTION E: PEAK USAGE CHARTS & TOP SLOTS */}
      <div className="analytics-grid-row">
        {/* Peak Usage Day of Week Chart */}
        <div className="analytics-card flex-1">
          <div className="analytics-card-header">
            <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <FiBarChart2 color="#2563eb" /> Usage Across Days of the Week
            </h2>
            {peakUsage.loading && <span className="analytics-spinner" />}
          </div>

          {peakUsage.error ? (
            <div className="analytics-error-box">
              <span>{peakUsage.error}</span>
              <button type="button" onClick={fetchPeakUsage} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : peakUsage.loading ? (
            <div className="analytics-skeleton-box">Analyzing weekly patterns...</div>
          ) : !peakUsage.data ? (
            <div className="analytics-empty-box">No peak usage records.</div>
          ) : (
            <div>
              <div className="peak-highlight-badge">
                Busiest Day: <strong>{peakUsage.data.busiestDayOfWeek}</strong>
              </div>
              <div style={{ width: "100%", height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={Object.entries(peakUsage.data.dayOfWeekUsage || {}).map(([day, mins]) => ({
                      day: day.substring(0, 3),
                      hours: parseFloat((mins / 60).toFixed(1)),
                    }))}
                  >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="day" />
                    <YAxis unit="h" />
                    <Tooltip formatter={(value) => [`${value} hours`, "Usage"]} />
                    <Bar dataKey="hours" fill="#0284c7" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}
        </div>

        {/* Peak Usage Hourly Chart & Top 5 Slots */}
        <div className="analytics-card flex-1">
          <div className="analytics-card-header">
            <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <FiClock color="#2563eb" /> Hourly Distribution &amp; Top 5 Slots
            </h2>
            {peakUsage.loading && <span className="analytics-spinner" />}
          </div>

          {peakUsage.error ? (
            <div className="analytics-error-box">
              <span>{peakUsage.error}</span>
              <button type="button" onClick={fetchPeakUsage} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : peakUsage.loading ? (
            <div className="analytics-skeleton-box">Analyzing hourly load...</div>
          ) : !peakUsage.data ? (
            <div className="analytics-empty-box">No hourly records.</div>
          ) : (
            <div>
              <div style={{ width: "100%", height: 180 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={Object.entries(peakUsage.data.hourlyUsage || {}).map(([h, mins]) => ({
                      hour: `${h}h`,
                      hours: parseFloat((mins / 60).toFixed(1)),
                    }))}
                  >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="hour" />
                    <YAxis unit="h" />
                    <Tooltip formatter={(value) => [`${value} hours`, "Usage"]} />
                    <Bar dataKey="hours" fill="#6366f1" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>

              {/* Top 5 Slots List */}
              <div className="top-slots-container">
                <h3 className="top-slots-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                  <FiZap color="#f59e0b" /> Top 5 Busiest Day-and-Hour Slots:
                </h3>
                <div className="top-slots-grid">
                  {(peakUsage.data.top5Slots || []).map((slot, i) => (
                    <div key={i} className="top-slot-badge">
                      <span className="rank">#{i + 1}</span>
                      <span className="slot-day">{slot.dayName}</span>
                      <span className="slot-time">{slot.timeLabel}</span>
                      <strong className="slot-pct">{slot.utilizationPercent}%</strong>
                    </div>
                  ))}
                  {(!peakUsage.data.top5Slots || peakUsage.data.top5Slots.length === 0) && (
                    <span className="analytics-muted">No high-activity slots recorded.</span>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* SECTION F: IDLE TIME TREND & IDLE ALERTS */}
      <div className="analytics-grid-row">
        {/* Idle Time Breakdown Chart */}
        <div className="analytics-card flex-2">
          <div className="analytics-card-header">
            <div>
              <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <FiMoon color="#f59e0b" /> Daily Idle Time Breakdown
              </h2>
              <p className="analytics-card-sub">
                Idle time is open operating hours with no booking (Available - Booked).
              </p>
            </div>
            {idleTime.loading && <span className="analytics-spinner" />}
          </div>

          {idleTime.error ? (
            <div className="analytics-error-box">
              <span>{idleTime.error}</span>
              <button type="button" onClick={fetchIdleTime} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : idleTime.loading ? (
            <div className="analytics-skeleton-box">Calculating idle trend...</div>
          ) : !idleTime.data ? (
            <div className="analytics-empty-box">No idle records.</div>
          ) : (
            <div>
              <div className="idle-overview-bar">
                <span>Total Available: <strong>{idleTime.data.totalAvailableHours}h</strong></span>
                <span>Total Booked: <strong>{idleTime.data.totalBookedHours}h</strong></span>
                <span>Total Idle: <strong>{idleTime.data.totalIdleHours}h</strong></span>
                <span>Overall Idle %: <strong>{idleTime.data.idlePercentage}%</strong></span>
              </div>
              <div style={{ width: "100%", height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={(idleTime.data.dailyBreakdown || []).map((d) => ({
                      date: d.date,
                      day: d.dayName.substring(0, 3),
                      bookedHours: d.bookedHours,
                      idleHours: d.idleHours,
                    }))}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="day" />
                    <YAxis unit="h" />
                    <Tooltip />
                    <Legend />
                    <Area type="monotone" dataKey="bookedHours" stackId="1" stroke="#0284c7" fill="#bae6fd" name="Booked Hours" />
                    <Area type="monotone" dataKey="idleHours" stackId="1" stroke="#f59e0b" fill="#fef3c7" name="Idle Hours" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}
        </div>

        {/* Idle Alerts Table */}
        <div className="analytics-card flex-1">
          <div className="analytics-card-header">
            <div>
              <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <FiAlertTriangle color="#dc2626" /> Idle Alerts (Last 7 Days)
              </h2>
              <p className="analytics-card-sub">
                Instruments with ≥ 90% idle time over the last 7 completed days.
              </p>
            </div>
            {idleAlerts.loading && <span className="analytics-spinner" />}
          </div>

          {idleAlerts.error ? (
            <div className="analytics-error-box">
              <span>{idleAlerts.error}</span>
              <button type="button" onClick={fetchIdleAlerts} className="analytics-retry-btn">
                Retry
              </button>
            </div>
          ) : idleAlerts.loading ? (
            <div className="analytics-skeleton-box">Scanning for low-utilization equipment...</div>
          ) : !idleAlerts.data || idleAlerts.data.length === 0 ? (
            <div className="analytics-empty-box" style={{ padding: "40px 16px", display: "flex", alignItems: "center", justifyContent: "center", gap: "6px" }}>
              <FiCheckCircle color="#16a34a" size={18} /> No instruments with ≥ 90% idle time in the last 7 days.
            </div>
          ) : (
            <div className="idle-alerts-list">
              {idleAlerts.data.map((alert) => (
                <div key={alert.equipmentId} className="idle-alert-item">
                  <div className="alert-top">
                    <strong>{alert.equipmentName}</strong>
                    <span className="alert-idle-badge">{alert.idlePercentage}% Idle</span>
                  </div>
                  <div className="alert-details">
                    <span>Tag: {alert.assetTag}</span>
                    <span>Dept: {alert.departmentName}</span>
                    <span>Idle: {alert.idleHours}h of {alert.availableHours}h</span>
                  </div>
                  <div className="alert-footer">
                    <span>
                      Last booked:{" "}
                      {alert.lastBookingEndDate
                        ? new Date(alert.lastBookingEndDate).toLocaleDateString("en-IN")
                        : "Never"}
                    </span>
                    <button
                      type="button"
                      className="alert-link-btn"
                      onClick={() => navigate(`/dashboard/student/equipments`)}
                      style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                    >
                      View Instrument <FiArrowRight size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* SECTION G: DEMAND ANALYSIS TABLE */}
      <div className="analytics-card">
        <div className="analytics-card-header">
          <div>
            <h2 className="analytics-card-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <FiList color="#2563eb" /> Instrument Demand Analysis &amp; Waitlist Tracking
            </h2>
            <p className="analytics-card-sub">
              Calculates booking rates, no-show rates, cancellations, and active waitlists. Equipment with ≥ 80% booking rate is flagged as High Demand.
            </p>
          </div>
          <div className="demand-controls">
            <label className="demand-filter-checkbox">
              <input
                type="checkbox"
                checked={highDemandOnly}
                onChange={(e) => updateParams({ highDemandOnly: e.target.checked, page: 0 })}
              />
              <span>High-Demand Only (≥ 80%)</span>
            </label>
            {demand.loading && <span className="analytics-spinner" />}
          </div>
        </div>

        {demand.error ? (
          <div className="analytics-error-box">
            <span>{demand.error}</span>
            <button type="button" onClick={fetchDemand} className="analytics-retry-btn">
              Retry
            </button>
          </div>
        ) : demand.loading ? (
          <div className="analytics-skeleton-box">Calculating demand metrics across instruments...</div>
        ) : !demand.data || demand.data.items?.length === 0 ? (
          <div className="analytics-empty-box">No equipment demand data available.</div>
        ) : (
          <div>
            <div className="table-responsive">
              <table className="analytics-table">
                <thead>
                  <tr>
                    <th>Equipment</th>
                    <th>Asset Tag</th>
                    <th>Total Bookings</th>
                    <th>Booked / Available</th>
                    <th>Booking Rate</th>
                    <th>No-Show Rate</th>
                    <th>Cancellation Rate</th>
                    <th>Waitlist</th>
                    <th>Demand Status</th>
                  </tr>
                </thead>
                <tbody>
                  {demand.data.items.map((item) => (
                    <tr key={item.equipmentId}>
                      <td>
                        <strong>{item.equipmentName}</strong>
                        {item.category && <div className="text-muted">{item.category}</div>}
                      </td>
                      <td><code>{item.assetTag}</code></td>
                      <td>{item.totalBookings}</td>
                      <td>
                        {fmtHours(item.bookedMinutes)} / {fmtHours(item.availableMinutes)}
                      </td>
                      <td>
                        <div className="rate-cell">
                          <strong>{item.bookingRate}%</strong>
                          <div className="mini-progress-bar">
                            <div
                              className={`mini-progress-fill ${
                                item.bookingRate >= 80 ? "high" : item.bookingRate >= 50 ? "med" : "low"
                              }`}
                              style={{ width: `${Math.min(100, item.bookingRate)}%` }}
                            />
                          </div>
                        </div>
                      </td>
                      <td>
                        <span style={{ color: item.noShowRate > 20 ? "#dc2626" : "inherit" }}>
                          {item.noShowRate}%
                        </span>
                      </td>
                      <td>
                        <span style={{ color: item.cancellationRate > 30 ? "#f59e0b" : "inherit" }}>
                          {item.cancellationRate}%
                        </span>
                      </td>
                      <td>
                        <span className={`waitlist-badge ${item.waitlistCount > 0 ? "has-waiting" : ""}`}>
                          {item.waitlistCount} waiting
                        </span>
                      </td>
                      <td>
                        {item.highDemand ? (
                          <span className="badge-high-demand" style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                            <FiZap size={12} /> High Demand
                          </span>
                        ) : (
                          <span className="badge-normal-demand">Normal</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="analytics-pagination">
              <span>
                Showing page <strong>{page + 1}</strong> of <strong>{demand.data.totalPages || 1}</strong> (
                {demand.data.totalItems} instruments)
              </span>
              <div className="analytics-page-btns">
                <button
                  type="button"
                  disabled={page <= 0}
                  onClick={() => updateParams({ page: page - 1 })}
                  className="analytics-page-btn"
                  style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                >
                  <FiArrowLeft size={14} /> Previous
                </button>
                <button
                  type="button"
                  disabled={page + 1 >= (demand.data.totalPages || 1)}
                  onClick={() => updateParams({ page: page + 1 })}
                  className="analytics-page-btn"
                  style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                >
                  Next <FiArrowRight size={14} />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
