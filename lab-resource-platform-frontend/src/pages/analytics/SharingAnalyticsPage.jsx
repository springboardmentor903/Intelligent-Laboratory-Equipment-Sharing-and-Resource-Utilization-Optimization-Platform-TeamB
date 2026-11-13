import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useSearchParams } from "react-router-dom";
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

// Utility formatting helpers
function fmtCurrency(val) {
  if (val === null || val === undefined || isNaN(val)) return "₹0.00";
  const num = typeof val === "number" ? val : parseFloat(val);
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(num);
}

function fmtHours(val) {
  if (val === null || val === undefined || isNaN(val)) return "0.0h";
  const num = typeof val === "number" ? val : parseFloat(val);
  return `${num.toFixed(1)}h`;
}

function fmtPercent(val) {
  if (val === null || val === undefined || isNaN(val)) return "0.0%";
  const num = typeof val === "number" ? val : parseFloat(val);
  return `${num.toFixed(1)}%`;
}

function formatDate(isoStr) {
  if (!isoStr) return "—";
  try {
    return new Date(isoStr).toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  } catch {
    return isoStr;
  }
}

export default function SharingAnalyticsPage() {
  const { user } = useAuth();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const isSysAdmin = user?.role === "SYSTEM_ADMINISTRATOR";

  // Institution filter state (for System Admin multi-institution scoping)
  const [institutions, setInstitutions] = useState([]);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState(
    searchParams.get("institutionId") || ""
  );

  // Active view tab: "overview" | "partners" | "reports" | "equipment"
  const [activeTab, setActiveTab] = useState(searchParams.get("tab") || "overview");

  // Trends period filter: "MONTHLY" | "WEEKLY"
  const [trendPeriod, setTrendPeriod] = useState("MONTHLY");
  const [trendMetric, setTrendMetric] = useState("hours"); // "hours" | "fees"
  const [trendStartDate, setTrendStartDate] = useState("");
  const [trendEndDate, setTrendEndDate] = useState("");

  // Data section states
  const [summary, setSummary] = useState({ loading: true, error: null, data: null });
  const [trends, setTrends] = useState({ loading: true, error: null, data: null });
  const [partners, setPartners] = useState({ loading: true, error: null, data: null });
  const [mostShared, setMostShared] = useState({ loading: true, error: null, data: null });

  // Partner sorting & pagination
  const [partnerPage, setPartnerPage] = useState(0);
  const [partnerSortBy, setPartnerSortBy] = useState("balance");
  const [partnerDirection, setPartnerDirection] = useState("DESC");

  // Detailed Partnership Report state
  const [selectedPartnerId, setSelectedPartnerId] = useState("");
  const [partnershipReport, setPartnershipReport] = useState({
    loading: false,
    error: null,
    data: null,
  });

  // Most-Shared equipment sorting & pagination
  const [equipPage, setEquipPage] = useState(0);
  const [equipSortBy, setEquipSortBy] = useState("providedMinutes");
  const [equipDirection, setEquipDirection] = useState("DESC");

  // Printable report ref
  const reportPrintRef = useRef(null);

  // Load institutions list for System Administrator
  useEffect(() => {
    if (isSysAdmin) {
      api
        .get("/api/colleges")
        .then((res) => setInstitutions(res.data || []))
        .catch((err) => console.error("Could not load institutions list:", err));
    }
  }, [isSysAdmin]);

  // Sync tab with URL search params
  const handleTabChange = (newTab) => {
    setActiveTab(newTab);
    const updated = Object.fromEntries(searchParams.entries());
    updated.tab = newTab;
    setSearchParams(updated);
  };

  // Base query params builder
  const buildQueryParams = useCallback(
    (extra = {}) => {
      const params = { ...extra };
      if (selectedInstitutionId) {
        params.institutionId = selectedInstitutionId;
      }
      return params;
    },
    [selectedInstitutionId]
  );

  // 1. Fetch Analytics Summary
  const fetchSummary = useCallback(async () => {
    setSummary((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/sharing/analytics/summary", {
        params: buildQueryParams(),
      });
      setSummary({ loading: false, error: null, data: res.data });
    } catch (err) {
      setSummary({
        loading: false,
        error: err.response?.data?.message || "Failed to load sharing analytics summary.",
        data: null,
      });
    }
  }, [buildQueryParams]);

  // 2. Fetch Sharing Trends
  const fetchTrends = useCallback(async () => {
    setTrends((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const params = buildQueryParams({
        period: trendPeriod,
      });
      if (trendStartDate) params.startDate = trendStartDate;
      if (trendEndDate) params.endDate = trendEndDate;

      const res = await api.get("/api/sharing/analytics/trends", { params });
      setTrends({ loading: false, error: null, data: res.data });
    } catch (err) {
      setTrends({
        loading: false,
        error: err.response?.data?.message || "Failed to load sharing trends data.",
        data: null,
      });
    }
  }, [buildQueryParams, trendPeriod, trendStartDate, trendEndDate]);

  // 3. Fetch Partner Analytics (Paginated)
  const fetchPartners = useCallback(async () => {
    setPartners((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/sharing/analytics/partners", {
        params: buildQueryParams({
          page: partnerPage,
          size: 10,
          sortBy: partnerSortBy,
          direction: partnerDirection,
        }),
      });
      setPartners({ loading: false, error: null, data: res.data });
    } catch (err) {
      setPartners({
        loading: false,
        error: err.response?.data?.message || "Failed to load partner institution data.",
        data: null,
      });
    }
  }, [buildQueryParams, partnerPage, partnerSortBy, partnerDirection]);

  // 4. Fetch Most Shared Equipment (Paginated)
  const fetchMostShared = useCallback(async () => {
    setMostShared((prev) => ({ ...prev, loading: true, error: null }));
    try {
      const res = await api.get("/api/sharing/analytics/most-shared-equipment", {
        params: buildQueryParams({
          page: equipPage,
          size: 10,
          sortBy: equipSortBy,
          direction: equipDirection,
        }),
      });
      setMostShared({ loading: false, error: null, data: res.data });
    } catch (err) {
      setMostShared({
        loading: false,
        error: err.response?.data?.message || "Failed to load most shared equipment statistics.",
        data: null,
      });
    }
  }, [buildQueryParams, equipPage, equipSortBy, equipDirection]);

  // 5. Fetch Single Partnership Report
  const fetchPartnershipReport = useCallback(
    async (partnerId) => {
      if (!partnerId) {
        setPartnershipReport({ loading: false, error: null, data: null });
        return;
      }
      setPartnershipReport({ loading: true, error: null, data: null });
      try {
        const res = await api.get("/api/sharing/analytics/partnership-report", {
          params: buildQueryParams({ partnerInstitutionId: partnerId }),
        });
        setPartnershipReport({ loading: false, error: null, data: res.data });
      } catch (err) {
        setPartnershipReport({
          loading: false,
          error: err.response?.data?.message || "Failed to generate partnership report.",
          data: null,
        });
      }
    },
    [buildQueryParams]
  );

  // Trigger main fetches on mount or institution filter change
  useEffect(() => {
    fetchSummary();
    fetchTrends();
    fetchPartners();
    fetchMostShared();
  }, [fetchSummary, fetchTrends, fetchPartners, fetchMostShared]);

  // Fetch report whenever partner selection changes
  useEffect(() => {
    if (selectedPartnerId) {
      fetchPartnershipReport(selectedPartnerId);
    }
  }, [selectedPartnerId, fetchPartnershipReport]);

  // Select partner to inspect report
  const handleSelectPartner = (partnerId) => {
    setSelectedPartnerId(partnerId);
    setActiveTab("reports");
  };

  // Toggle sorting for Partner Table
  const handleSortPartners = (field) => {
    if (partnerSortBy === field) {
      setPartnerDirection((prev) => (prev === "ASC" ? "DESC" : "ASC"));
    } else {
      setPartnerSortBy(field);
      setPartnerDirection("DESC");
    }
    setPartnerPage(0);
  };

  // Printable Report Trigger
  const handlePrintReport = () => {
    window.print();
  };

  // CSV Export: Export Partners Summary
  const handleExportPartnersCSV = () => {
    if (!partners.data?.content || partners.data.content.length === 0) {
      toast.info("No partner data available to export.");
      return;
    }
    const headers = [
      "Partner Institution",
      "Hours Provided",
      "Hours Used",
      "Fees Earned (INR)",
      "Fees Owed (INR)",
      "Net Balance (INR)",
      "Active Agreements",
      "Total Bookings",
    ];
    const rows = partners.data.content.map((p) => [
      `"${p.partnerInstitutionName || "Unknown"}"`,
      p.hoursProvided ?? 0,
      p.hoursUsed ?? 0,
      p.feesEarned ?? 0,
      p.feesOwed ?? 0,
      p.balance ?? 0,
      p.activeAgreementsCount ?? 0,
      p.totalBookings ?? 0,
    ]);
    const csvContent =
      "data:text/csv;charset=utf-8," +
      [headers.join(","), ...rows.map((e) => e.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute(
      "download",
      `partner_analytics_${new Date().toISOString().split("T")[0]}.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast.success("Partner analytics CSV downloaded successfully.");
  };

  // CSV Export: Export Partnership Detailed Report
  const handleExportReportCSV = () => {
    const rep = partnershipReport.data;
    if (!rep) {
      toast.info("Please generate a partnership report first.");
      return;
    }
    const lines = [];
    lines.push(`"PARTNERSHIP AUDIT REPORT"`);
    lines.push(`"Home Institution","${rep.institutionName || "My Institution"}"`);
    lines.push(`"Partner Institution","${rep.partnerInstitutionName || "Partner"}"`);
    lines.push(`"Date of Report","${new Date().toLocaleDateString("en-IN")}"`);
    lines.push("");
    lines.push(`"SUMMARY METRICS"`);
    lines.push(`"Metric","Value"`);
    lines.push(`"Hours Provided",${rep.hoursProvided ?? 0}`);
    lines.push(`"Hours Borrowed",${rep.hoursUsed ?? 0}`);
    lines.push(`"Total Exchanged Hours",${rep.totalHours ?? 0}`);
    lines.push(`"Fees Earned",${rep.feesEarned ?? 0}`);
    lines.push(`"Fees Owed",${rep.feesOwed ?? 0}`);
    lines.push(`"Net Settlement Balance",${rep.balance ?? 0}`);
    lines.push(`"Total Sharing Bookings",${rep.totalBookings ?? 0}`);
    lines.push(`"No-Show Count",${rep.noShowCount ?? 0}`);
    lines.push(`"No-Show Rate (%)",${rep.noShowRate ?? 0}`);
    lines.push("");
    lines.push(`"GOVERNING SHARING AGREEMENTS"`);
    lines.push(
      `"Agreement ID","Equipment Name","Asset Tag","Status","Start Date","End Date","Fee Per Hour (INR)","Window"`
    );
    if (rep.agreements && rep.agreements.length > 0) {
      rep.agreements.forEach((a) => {
        lines.push(
          [
            `"#${a.agreementId}"`,
            `"${a.equipmentName || "N/A"}"`,
            `"${a.assetTag || "N/A"}"`,
            `"${a.status || "ACTIVE"}"`,
            `"${a.startDate || "N/A"}"`,
            `"${a.endDate || "N/A"}"`,
            a.feePerHour ?? 0,
            `"${a.allowedStartTime || "08:00"} - ${a.allowedEndTime || "20:00"}"`,
          ].join(",")
        );
      });
    } else {
      lines.push(`"No active agreements recorded"`);
    }

    const csvContent = "data:text/csv;charset=utf-8," + lines.join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute(
      "download",
      `partnership_report_${rep.partnerInstitutionName?.replace(/\s+/g, "_")}_${
        new Date().toISOString().split("T")[0]
      }.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast.success("Partnership report CSV downloaded successfully.");
  };

  const sumData = summary.data;
  const trendItems = trends.data?.items || [];
  const partnerList = partners.data?.content || [];
  const mostSharedList = mostShared.data?.content || [];

  return (
    <div className="analytics-page-container">
      {/* Page Title & Scope Header */}
      <div className="analytics-header">
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <h1 className="analytics-title">🤝 Inter-Institution Sharing &amp; Partnership Analytics</h1>
            <span
              style={{
                background: "#e0f2fe",
                color: "#0369a1",
                fontSize: "11px",
                fontWeight: 700,
                padding: "3px 8px",
                borderRadius: "12px",
                textTransform: "uppercase",
              }}
            >
              Milestone 2
            </span>
          </div>
          <p className="analytics-subtitle">
            Cross-institution resource exchange, financial settlement balances, sharing trends, and official partnership reports.
          </p>
        </div>

        {/* System Administrator Institution Selector */}
        {isSysAdmin && institutions.length > 0 && (
          <div className="analytics-filter-group" style={{ minWidth: "260px" }}>
            <label className="analytics-filter-label" htmlFor="inst-select">
              🏛️ Scope by Institution:
            </label>
            <select
              id="inst-select"
              className="analytics-select"
              value={selectedInstitutionId}
              onChange={(e) => {
                setSelectedInstitutionId(e.target.value);
                const updated = Object.fromEntries(searchParams.entries());
                if (e.target.value) {
                  updated.institutionId = e.target.value;
                } else {
                  delete updated.institutionId;
                }
                setSearchParams(updated);
              }}
            >
              <option value="">All / Primary Institution</option>
              {institutions.map((inst) => (
                <option key={inst.id} value={inst.id}>
                  {inst.name} ({inst.code || `ID: ${inst.id}`})
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Navigation Sub-Tabs */}
      <div
        style={{
          display: "flex",
          gap: "8px",
          borderBottom: "1px solid #e2e8f0",
          marginBottom: "24px",
          paddingBottom: "8px",
        }}
      >
        <button
          type="button"
          className={`analytics-pill-btn ${activeTab === "overview" ? "active" : ""}`}
          onClick={() => handleTabChange("overview")}
        >
          📊 Summary &amp; Trends
        </button>
        <button
          type="button"
          className={`analytics-pill-btn ${activeTab === "partners" ? "active" : ""}`}
          onClick={() => handleTabChange("partners")}
        >
          🏛️ Partner Institutions ({partners.data?.totalElements ?? 0})
        </button>
        <button
          type="button"
          className={`analytics-pill-btn ${activeTab === "reports" ? "active" : ""}`}
          onClick={() => handleTabChange("reports")}
        >
          📜 Partnership Reports &amp; Audit
        </button>
        <button
          type="button"
          className={`analytics-pill-btn ${activeTab === "equipment" ? "active" : ""}`}
          onClick={() => handleTabChange("equipment")}
        >
          🔬 Most Shared Instruments
        </button>
      </div>

      {/* ========================================================================= */}
      {/* SECTION A & B: OVERVIEW (SUMMARY METRIC CARDS & SHARING TRENDS)          */}
      {/* ========================================================================= */}
      {(activeTab === "overview" || activeTab === "all") && (
        <>
          {/* Summary KPI Cards */}
          {summary.loading ? (
            <div className="analytics-card" style={{ textAlign: "center", padding: "40px" }}>
              <div className="analytics-spinner" />
              <p style={{ marginTop: "12px", color: "#64748b" }}>Loading sharing analytics metrics…</p>
            </div>
          ) : summary.error ? (
            <div className="analytics-error-box">
              <span>⚠️ {summary.error}</span>
              <button type="button" className="analytics-retry-btn" onClick={fetchSummary}>
                Retry
              </button>
            </div>
          ) : (
            <div className="analytics-grid-row">
              {/* Card 1: Net Financial Balance */}
              <div className="analytics-card">
                <div className="analytics-card-header">
                  <div>
                    <h3 className="analytics-card-title">Net Financial Balance</h3>
                    <p className="analytics-card-sub">Fees earned vs. fees owed across partner institutions</p>
                  </div>
                  <span
                    style={{
                      fontSize: "12px",
                      fontWeight: 700,
                      padding: "4px 10px",
                      borderRadius: "16px",
                      background:
                        (sumData?.netBalance || 0) >= 0 ? "#dcfce7" : "#fee2e2",
                      color:
                        (sumData?.netBalance || 0) >= 0 ? "#15803d" : "#b91c1c",
                    }}
                  >
                    {(sumData?.netBalance || 0) >= 0 ? "SURPLUS" : "DEFICIT"}
                  </span>
                </div>

                <div
                  style={{
                    fontSize: "30px",
                    fontWeight: 800,
                    color: (sumData?.netBalance || 0) >= 0 ? "#15803d" : "#b91c1c",
                    marginBottom: "12px",
                  }}
                >
                  {(sumData?.netBalance || 0) >= 0 ? "+" : ""}
                  {fmtCurrency(sumData?.netBalance)}
                </div>

                <div className="analytics-benchmark-body">
                  <div className="benchmark-stat-row">
                    <span>Fees Earned (Shared Out):</span>
                    <strong style={{ color: "#15803d" }}>+{fmtCurrency(sumData?.feesEarned)}</strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>Fees Owed (Borrowed In):</span>
                    <strong style={{ color: "#b91c1c" }}>-{fmtCurrency(sumData?.feesOwed)}</strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>Expected Future Receivables:</span>
                    <span style={{ color: "#0369a1", fontWeight: 600 }}>
                      {fmtCurrency(sumData?.expectedFees)}
                    </span>
                  </div>
                </div>
              </div>

              {/* Card 2: Shared Hours Balance */}
              <div className="analytics-card">
                <div className="analytics-card-header">
                  <div>
                    <h3 className="analytics-card-title">Resource Hours Exchanged</h3>
                    <p className="analytics-card-sub">Actual instrument hours provided vs. consumed</p>
                  </div>
                  <span className="diff-badge higher" style={{ background: "#e0f2fe", color: "#0369a1" }}>
                    Actual Telemetry
                  </span>
                </div>

                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    gap: "12px",
                    marginBottom: "12px",
                  }}
                >
                  <span style={{ fontSize: "28px", fontWeight: 800, color: "#1e293b" }}>
                    {fmtHours(sumData?.totalHoursProvided)}
                  </span>
                  <span style={{ color: "#64748b", fontSize: "14px" }}>provided out</span>
                  <span style={{ color: "#94a3b8" }}>/</span>
                  <span style={{ fontSize: "22px", fontWeight: 700, color: "#475569" }}>
                    {fmtHours(sumData?.totalHoursUsed)}
                  </span>
                  <span style={{ color: "#64748b", fontSize: "14px" }}>borrowed in</span>
                </div>

                <div className="analytics-benchmark-body">
                  <div className="benchmark-stat-row">
                    <span>Net Hours Ratio:</span>
                    <strong>
                      {sumData?.totalHoursUsed && parseFloat(sumData.totalHoursUsed) > 0
                        ? (
                            parseFloat(sumData.totalHoursProvided || 0) /
                            parseFloat(sumData.totalHoursUsed)
                          ).toFixed(2) + "x"
                        : "N/A"}
                    </strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>External Share of Lab Capacity:</span>
                    <strong style={{ color: "#2563eb" }}>
                      {fmtPercent(sumData?.externalSharePercentage)}
                    </strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>Total Partner Bookings:</span>
                    <span>{sumData?.totalBookings ?? 0} sessions</span>
                  </div>
                </div>
              </div>

              {/* Card 3: Institutional Partnerships */}
              <div className="analytics-card">
                <div className="analytics-card-header">
                  <div>
                    <h3 className="analytics-card-title">Inter-Institution Network</h3>
                    <p className="analytics-card-sub">Active agreements and partner institutions</p>
                  </div>
                  <span className="admin-status-pill" style={{ background: "#fef3c7", color: "#b45309" }}>
                    {sumData?.expiringAgreementsCount || 0} Expiring Soon
                  </span>
                </div>

                <div
                  style={{
                    fontSize: "30px",
                    fontWeight: 800,
                    color: "#0f172a",
                    marginBottom: "12px",
                  }}
                >
                  {sumData?.activeAgreements ?? 0}
                  <span style={{ fontSize: "14px", fontWeight: 500, color: "#64748b", marginLeft: "8px" }}>
                    Active Agreements
                  </span>
                </div>

                <div className="analytics-benchmark-body">
                  <div className="benchmark-stat-row">
                    <span>Partner Institutions:</span>
                    <strong>{sumData?.totalPartners ?? 0} active colleges</strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>Agreements Expiring (30 Days):</span>
                    <strong
                      style={{
                        color:
                          (sumData?.expiringAgreementsCount || 0) > 0 ? "#b45309" : "#15803d",
                      }}
                    >
                      {sumData?.expiringAgreementsCount ?? 0}
                    </strong>
                  </div>
                  <div className="benchmark-stat-row">
                    <span>Sharing No-Show Rate:</span>
                    <strong
                      style={{
                        color:
                          (sumData?.noShowRate || 0) > 10 ? "#b91c1c" : "#15803d",
                      }}
                    >
                      {fmtPercent(sumData?.noShowRate)} ({sumData?.noShowCount ?? 0} no-shows)
                    </strong>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Sharing Trends Visualization */}
          <div className="analytics-card">
            <div className="analytics-card-header">
              <div>
                <h3 className="analytics-card-title">📈 Cross-Institution Sharing Activity Trends</h3>
                <p className="analytics-card-sub">
                  Track resource usage volume and financial settlement trajectories over time.
                </p>
              </div>

              {/* Filter Controls: Period (Monthly/Weekly) & Metric Toggle */}
              <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", alignItems: "center" }}>
                {/* Metric toggle */}
                <div className="analytics-btn-group">
                  <button
                    type="button"
                    className={`analytics-pill-btn ${trendMetric === "hours" ? "active" : ""}`}
                    onClick={() => setTrendMetric("hours")}
                  >
                    ⏱️ Usage Hours
                  </button>
                  <button
                    type="button"
                    className={`analytics-pill-btn ${trendMetric === "fees" ? "active" : ""}`}
                    onClick={() => setTrendMetric("fees")}
                  >
                    💰 Settlement Fees (₹)
                  </button>
                </div>

                {/* Period toggle */}
                <div className="analytics-btn-group">
                  <button
                    type="button"
                    className={`analytics-pill-btn ${trendPeriod === "MONTHLY" ? "active" : ""}`}
                    onClick={() => setTrendPeriod("MONTHLY")}
                  >
                    Monthly
                  </button>
                  <button
                    type="button"
                    className={`analytics-pill-btn ${trendPeriod === "WEEKLY" ? "active" : ""}`}
                    onClick={() => setTrendPeriod("WEEKLY")}
                  >
                    Weekly
                  </button>
                </div>
              </div>
            </div>

            {/* Custom Date Inputs */}
            <div
              style={{
                display: "flex",
                gap: "12px",
                alignItems: "center",
                flexWrap: "wrap",
                marginBottom: "16px",
                padding: "10px 14px",
                background: "#f8fafc",
                borderRadius: "8px",
              }}
            >
              <span style={{ fontSize: "12px", fontWeight: 600, color: "#475569" }}>
                Filter Date Range:
              </span>
              <input
                type="date"
                className="analytics-date-input"
                value={trendStartDate}
                onChange={(e) => setTrendStartDate(e.target.value)}
                title="Start Date"
              />
              <span style={{ color: "#94a3b8" }}>→</span>
              <input
                type="date"
                className="analytics-date-input"
                value={trendEndDate}
                onChange={(e) => setTrendEndDate(e.target.value)}
                title="End Date"
              />
              <button
                type="button"
                className="analytics-apply-btn"
                onClick={fetchTrends}
              >
                Apply Range
              </button>
              {(trendStartDate || trendEndDate) && (
                <button
                  type="button"
                  style={{
                    background: "none",
                    border: "none",
                    color: "#64748b",
                    fontSize: "12px",
                    cursor: "pointer",
                    textDecoration: "underline",
                  }}
                  onClick={() => {
                    setTrendStartDate("");
                    setTrendEndDate("");
                  }}
                >
                  Clear
                </button>
              )}
            </div>

            {/* Trend Chart */}
            {trends.loading ? (
              <div style={{ textAlign: "center", padding: "60px 0" }}>
                <div className="analytics-spinner" />
                <p style={{ marginTop: "12px", color: "#64748b" }}>Loading sharing trend series…</p>
              </div>
            ) : trends.error ? (
              <div className="analytics-error-box">
                <span>⚠️ {trends.error}</span>
                <button type="button" className="analytics-retry-btn" onClick={fetchTrends}>
                  Retry
                </button>
              </div>
            ) : trendItems.length === 0 ? (
              <div className="analytics-empty-box" style={{ padding: "40px" }}>
                <p>No sharing trend events recorded for the selected period.</p>
              </div>
            ) : (
              <div style={{ width: "100%", height: 340, marginTop: "12px" }}>
                <ResponsiveContainer width="100%" height="100%">
                  {trendMetric === "hours" ? (
                    <BarChart data={trendItems} margin={{ top: 10, right: 20, left: 0, bottom: 20 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                      <XAxis dataKey="periodLabel" stroke="#64748b" fontSize={12} />
                      <YAxis
                        stroke="#64748b"
                        fontSize={12}
                        tickFormatter={(v) => `${v}h`}
                      />
                      <Tooltip
                        formatter={(val, name) => [
                          `${parseFloat(val).toFixed(1)} hrs`,
                          name === "hoursProvided"
                            ? "Hours Provided (Shared Out)"
                            : "Hours Borrowed (Used In)",
                        ]}
                        labelFormatter={(label) => `Period: ${label}`}
                        contentStyle={{
                          background: "#ffffff",
                          border: "1px solid #e2e8f0",
                          borderRadius: "8px",
                          boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)",
                        }}
                      />
                      <Legend
                        verticalAlign="top"
                        height={36}
                        formatter={(value) =>
                          value === "hoursProvided"
                            ? "Hours Provided (External Sharing)"
                            : "Hours Borrowed (Your Institution)"
                        }
                      />
                      <Bar dataKey="hoursProvided" fill="#3b82f6" radius={[4, 4, 0, 0]} name="hoursProvided" />
                      <Bar dataKey="hoursUsed" fill="#10b981" radius={[4, 4, 0, 0]} name="hoursUsed" />
                    </BarChart>
                  ) : (
                    <AreaChart data={trendItems} margin={{ top: 10, right: 20, left: 10, bottom: 20 }}>
                      <defs>
                        <linearGradient id="colorEarned" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#10b981" stopOpacity={0.4} />
                          <stop offset="95%" stopColor="#10b981" stopOpacity={0.0} />
                        </linearGradient>
                        <linearGradient id="colorOwed" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#ef4444" stopOpacity={0.4} />
                          <stop offset="95%" stopColor="#ef4444" stopOpacity={0.0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                      <XAxis dataKey="periodLabel" stroke="#64748b" fontSize={12} />
                      <YAxis
                        stroke="#64748b"
                        fontSize={12}
                        tickFormatter={(v) => `₹${v}`}
                      />
                      <Tooltip
                        formatter={(val, name) => [
                          fmtCurrency(val),
                          name === "feesEarned"
                            ? "Fees Earned"
                            : name === "feesOwed"
                            ? "Fees Owed"
                            : "Net Settlement Balance",
                        ]}
                        labelFormatter={(label) => `Period: ${label}`}
                        contentStyle={{
                          background: "#ffffff",
                          border: "1px solid #e2e8f0",
                          borderRadius: "8px",
                          boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)",
                        }}
                      />
                      <Legend verticalAlign="top" height={36} />
                      <Area
                        type="monotone"
                        dataKey="feesEarned"
                        stroke="#10b981"
                        fillOpacity={1}
                        fill="url(#colorEarned)"
                        name="Fees Earned"
                      />
                      <Area
                        type="monotone"
                        dataKey="feesOwed"
                        stroke="#ef4444"
                        fillOpacity={1}
                        fill="url(#colorOwed)"
                        name="Fees Owed"
                      />
                    </AreaChart>
                  )}
                </ResponsiveContainer>
              </div>
            )}
          </div>
        </>
      )}

      {/* ========================================================================= */}
      {/* SECTION C: PARTNER INSTITUTIONS COMPARISON TABLE                          */}
      {/* ========================================================================= */}
      {(activeTab === "partners" || activeTab === "all") && (
        <div className="analytics-card">
          <div className="analytics-card-header">
            <div>
              <h3 className="analytics-card-title">🏛️ Partner Institution Comparison &amp; Balance Ledger</h3>
              <p className="analytics-card-sub">
                Exchange metrics, operational volume, and net financial credit/debit balances per institution.
              </p>
            </div>

            <div style={{ display: "flex", gap: "8px" }}>
              <button
                type="button"
                className="analytics-pill-btn"
                onClick={handleExportPartnersCSV}
                title="Download Partner Ledger as CSV"
              >
                📥 Export CSV
              </button>
            </div>
          </div>

          {partners.loading ? (
            <div style={{ textAlign: "center", padding: "50px" }}>
              <div className="analytics-spinner" />
              <p style={{ marginTop: "12px", color: "#64748b" }}>Loading partner institution ledger…</p>
            </div>
          ) : partners.error ? (
            <div className="analytics-error-box">
              <span>⚠️ {partners.error}</span>
              <button type="button" className="analytics-retry-btn" onClick={fetchPartners}>
                Retry
              </button>
            </div>
          ) : partnerList.length === 0 ? (
            <div className="analytics-empty-box" style={{ padding: "40px" }}>
              <p>No partner institutions linked with active equipment sharing yet.</p>
            </div>
          ) : (
            <>
              <div style={{ overflowX: "auto" }}>
                <table className="analytics-table">
                  <thead>
                    <tr>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("name")}
                      >
                        Partner Institution {partnerSortBy === "name" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("activeagreementscount")}
                      >
                        Active Agreements {partnerSortBy === "activeagreementscount" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("totalbookings")}
                      >
                        Total Bookings {partnerSortBy === "totalbookings" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("hoursprovided")}
                      >
                        Hours Provided {partnerSortBy === "hoursprovided" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("hoursused")}
                      >
                        Hours Used {partnerSortBy === "hoursused" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("feesearned")}
                      >
                        Fees Earned {partnerSortBy === "feesearned" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("feesowed")}
                      >
                        Fees Owed {partnerSortBy === "feesowed" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th
                        style={{ cursor: "pointer" }}
                        onClick={() => handleSortPartners("balance")}
                      >
                        Net Balance {partnerSortBy === "balance" && (partnerDirection === "ASC" ? "▲" : "▼")}
                      </th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {partnerList.map((p) => {
                      const netVal = p.balance || 0;
                      const isPositive = netVal >= 0;
                      return (
                        <tr key={p.partnerInstitutionId}>
                          <td>
                            <strong>{p.partnerInstitutionName || "Partner Institution"}</strong>
                            <div style={{ fontSize: "11px", color: "#64748b" }}>
                              ID: #{p.partnerInstitutionId}
                            </div>
                          </td>
                          <td>
                            <span className="admin-status-pill" style={{ background: "#e0f2fe", color: "#0369a1" }}>
                              {p.activeAgreementsCount ?? 0} active
                            </span>
                          </td>
                          <td>{p.totalBookings ?? 0}</td>
                          <td>
                            <strong style={{ color: "#2563eb" }}>{fmtHours(p.hoursProvided)}</strong>
                          </td>
                          <td>
                            <strong style={{ color: "#475569" }}>{fmtHours(p.hoursUsed)}</strong>
                          </td>
                          <td style={{ color: "#15803d", fontWeight: 600 }}>
                            {fmtCurrency(p.feesEarned)}
                          </td>
                          <td style={{ color: "#b91c1c", fontWeight: 600 }}>
                            {fmtCurrency(p.feesOwed)}
                          </td>
                          <td>
                            <span
                              style={{
                                display: "inline-block",
                                padding: "4px 8px",
                                borderRadius: "6px",
                                fontWeight: 700,
                                fontSize: "12px",
                                background: isPositive ? "#dcfce7" : "#fee2e2",
                                color: isPositive ? "#15803d" : "#b91c1c",
                              }}
                            >
                              {isPositive ? "+" : ""}
                              {fmtCurrency(netVal)}
                            </span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="analytics-pill-btn"
                              style={{ fontSize: "11px", padding: "4px 10px" }}
                              onClick={() => handleSelectPartner(p.partnerInstitutionId)}
                            >
                              📜 Audit Report
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {partners.data?.totalPages > 1 && (
                <div className="analytics-pagination" style={{ marginTop: "16px" }}>
                  <span>
                    Page {partners.data.number + 1} of {partners.data.totalPages} ({partners.data.totalElements} partners)
                  </span>
                  <div className="analytics-page-btns">
                    <button
                      type="button"
                      className="analytics-page-btn"
                      disabled={partners.data.number === 0}
                      onClick={() => setPartnerPage((p) => Math.max(0, p - 1))}
                    >
                      ← Previous
                    </button>
                    <button
                      type="button"
                      className="analytics-page-btn"
                      disabled={partners.data.number >= partners.data.totalPages - 1}
                      onClick={() => setPartnerPage((p) => p + 1)}
                    >
                      Next →
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* SECTION D: PARTNERSHIP AUDIT REPORT & PRINTABLE VIEW                      */}
      {/* ========================================================================= */}
      {(activeTab === "reports" || activeTab === "all") && (
        <div className="analytics-card">
          <div className="analytics-card-header">
            <div>
              <h3 className="analytics-card-title">📜 Bilateral Partnership Audit Report</h3>
              <p className="analytics-card-sub">
                Official institution-to-institution audit report with sharing agreements, hours ledger, and financial settlement balance.
              </p>
            </div>

            <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
              {/* Partner selector dropdown */}
              <select
                className="analytics-select"
                style={{ width: "auto", minWidth: "200px" }}
                value={selectedPartnerId}
                onChange={(e) => setSelectedPartnerId(e.target.value)}
              >
                <option value="">-- Choose Partner Institution --</option>
                {partnerList.map((p) => (
                  <option key={p.partnerInstitutionId} value={p.partnerInstitutionId}>
                    {p.partnerInstitutionName} (ID: #{p.partnerInstitutionId})
                  </option>
                ))}
              </select>

              {partnershipReport.data && (
                <>
                  <button
                    type="button"
                    className="analytics-pill-btn"
                    onClick={handleExportReportCSV}
                  >
                    📥 Export CSV
                  </button>
                  <button
                    type="button"
                    className="primary-button"
                    style={{ padding: "6px 14px", fontSize: "13px" }}
                    onClick={handlePrintReport}
                  >
                    🖨️ Print Report
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Report Content Body */}
          {partnershipReport.loading ? (
            <div style={{ textAlign: "center", padding: "60px" }}>
              <div className="analytics-spinner" />
              <p style={{ marginTop: "12px", color: "#64748b" }}>Generating official partnership report…</p>
            </div>
          ) : partnershipReport.error ? (
            <div className="analytics-error-box">
              <span>⚠️ {partnershipReport.error}</span>
              <button
                type="button"
                className="analytics-retry-btn"
                onClick={() => fetchPartnershipReport(selectedPartnerId)}
              >
                Retry
              </button>
            </div>
          ) : !selectedPartnerId ? (
            <div className="analytics-empty-box" style={{ padding: "50px", textAlign: "center" }}>
              <h3>Select a Partner Institution</h3>
              <p style={{ color: "#64748b", marginTop: "6px" }}>
                Select an institution from the dropdown above or click "Audit Report" on the Partner ledger table to inspect bilateral sharing details.
              </p>
            </div>
          ) : partnershipReport.data ? (
            <div ref={reportPrintRef} className="partnership-printable-sheet" style={{ marginTop: "10px" }}>
              {/* Report Header */}
              <div
                style={{
                  borderBottom: "2px solid #0f172a",
                  paddingBottom: "16px",
                  marginBottom: "20px",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  flexWrap: "wrap",
                  gap: "12px",
                }}
              >
                <div>
                  <h2 style={{ margin: 0, fontSize: "22px", color: "#0f172a" }}>
                    Bilateral Resource Sharing Settlement Statement
                  </h2>
                  <p style={{ margin: "4px 0 0 0", color: "#64748b", fontSize: "13px" }}>
                    Between <strong>{partnershipReport.data.institutionName || "Your Institution"}</strong> and{" "}
                    <strong>{partnershipReport.data.partnerInstitutionName || "Partner Institution"}</strong>
                  </p>
                </div>
                <div style={{ textAlign: "right", fontSize: "12px", color: "#64748b" }}>
                  <div>Report Generated: <strong>{new Date().toLocaleDateString("en-IN")}</strong></div>
                  <div>Status: <span style={{ color: "#15803d", fontWeight: 700 }}>RECONCILED</span></div>
                </div>
              </div>

              {/* Bilateral Summary Ledger Box */}
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
                  gap: "16px",
                  background: "#f8fafc",
                  padding: "16px",
                  borderRadius: "8px",
                  border: "1px solid #e2e8f0",
                  marginBottom: "24px",
                }}
              >
                <div>
                  <div className="analytics-metric-label">Hours Provided</div>
                  <div style={{ fontSize: "20px", fontWeight: 700, color: "#1e293b" }}>
                    {fmtHours(partnershipReport.data.hoursProvided)}
                  </div>
                  <div className="analytics-metric-sub">Shared out to partner</div>
                </div>

                <div>
                  <div className="analytics-metric-label">Hours Borrowed</div>
                  <div style={{ fontSize: "20px", fontWeight: 700, color: "#475569" }}>
                    {fmtHours(partnershipReport.data.hoursUsed)}
                  </div>
                  <div className="analytics-metric-sub">Used from partner</div>
                </div>

                <div>
                  <div className="analytics-metric-label">Gross Fees Earned</div>
                  <div style={{ fontSize: "20px", fontWeight: 700, color: "#15803d" }}>
                    {fmtCurrency(partnershipReport.data.feesEarned)}
                  </div>
                  <div className="analytics-metric-sub">Payable by partner</div>
                </div>

                <div>
                  <div className="analytics-metric-label">Gross Fees Owed</div>
                  <div style={{ fontSize: "20px", fontWeight: 700, color: "#b91c1c" }}>
                    {fmtCurrency(partnershipReport.data.feesOwed)}
                  </div>
                  <div className="analytics-metric-sub">Payable to partner</div>
                </div>

                <div>
                  <div className="analytics-metric-label">Net Settlement Balance</div>
                  <div
                    style={{
                      fontSize: "20px",
                      fontWeight: 800,
                      color:
                        (partnershipReport.data.balance || 0) >= 0 ? "#15803d" : "#b91c1c",
                    }}
                  >
                    {(partnershipReport.data.balance || 0) >= 0 ? "+" : ""}
                    {fmtCurrency(partnershipReport.data.balance)}
                  </div>
                  <div className="analytics-metric-sub">
                    {(partnershipReport.data.balance || 0) >= 0
                      ? "Net Receivable"
                      : "Net Payable"}
                  </div>
                </div>
              </div>

              {/* Governing Sharing Agreements Table */}
              <h4 style={{ fontSize: "15px", fontWeight: 700, color: "#1e293b", marginBottom: "12px" }}>
                📜 Governing Inter-Institution Sharing Agreements
              </h4>

              {partnershipReport.data.agreements?.length === 0 ? (
                <p style={{ color: "#64748b", fontStyle: "italic" }}>
                  No active bilateral agreements registered with this partner.
                </p>
              ) : (
                <table className="analytics-table" style={{ marginBottom: "20px" }}>
                  <thead>
                    <tr>
                      <th>Agreement ID</th>
                      <th>Instrument Name</th>
                      <th>Asset Tag</th>
                      <th>Validity Period</th>
                      <th>Fee / Hour</th>
                      <th>Operating Window</th>
                      <th>Agreement Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {partnershipReport.data.agreements.map((agr) => (
                      <tr key={agr.agreementId}>
                        <td>
                          <strong>#{agr.agreementId}</strong>
                        </td>
                        <td>{agr.equipmentName}</td>
                        <td>
                          <span style={{ fontFamily: "monospace", fontSize: "11px" }}>
                            {agr.assetTag || "—"}
                          </span>
                        </td>
                        <td>
                          {formatDate(agr.startDate)} – {formatDate(agr.endDate)}
                        </td>
                        <td style={{ fontWeight: 600 }}>{fmtCurrency(agr.feePerHour)}/hr</td>
                        <td>
                          {agr.allowedStartTime || "08:00"} – {agr.allowedEndTime || "20:00"}
                        </td>
                        <td>
                          <span
                            className="admin-status-pill"
                            style={{
                              background:
                                agr.status === "ACTIVE"
                                  ? "#dcfce7"
                                  : agr.status === "EXPIRED"
                                  ? "#fef3c7"
                                  : "#fee2e2",
                              color:
                                agr.status === "ACTIVE"
                                  ? "#15803d"
                                  : agr.status === "EXPIRED"
                                  ? "#b45309"
                                  : "#b91c1c",
                            }}
                          >
                            {agr.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {/* Quality & Reliability Metrics */}
              <div
                style={{
                  display: "flex",
                  gap: "24px",
                  fontSize: "13px",
                  color: "#475569",
                  padding: "12px 16px",
                  background: "#f1f5f9",
                  borderRadius: "6px",
                  marginBottom: "20px",
                }}
              >
                <span>
                  Total Bookings Under Partnership: <strong>{partnershipReport.data.totalBookings ?? 0}</strong>
                </span>
                <span>
                  No-Show Count: <strong>{partnershipReport.data.noShowCount ?? 0}</strong>
                </span>
                <span>
                  Reliability Rate:{" "}
                  <strong style={{ color: "#15803d" }}>
                    {(100 - parseFloat(partnershipReport.data.noShowRate || 0)).toFixed(1)}%
                  </strong>
                </span>
              </div>
            </div>
          ) : null}
        </div>
      )}

      {/* ========================================================================= */}
      {/* SECTION E: MOST SHARED LABORATORY INSTRUMENTS                            */}
      {/* ========================================================================= */}
      {(activeTab === "equipment" || activeTab === "all") && (
        <div className="analytics-card">
          <div className="analytics-card-header">
            <div>
              <h3 className="analytics-card-title">🔬 Most Shared Laboratory Instruments</h3>
              <p className="analytics-card-sub">
                Top laboratory equipment driving external research partnerships and revenue generation.
              </p>
            </div>
          </div>

          {mostShared.loading ? (
            <div style={{ textAlign: "center", padding: "50px" }}>
              <div className="analytics-spinner" />
              <p style={{ marginTop: "12px", color: "#64748b" }}>Loading instrument sharing statistics…</p>
            </div>
          ) : mostShared.error ? (
            <div className="analytics-error-box">
              <span>⚠️ {mostShared.error}</span>
              <button type="button" className="analytics-retry-btn" onClick={fetchMostShared}>
                Retry
              </button>
            </div>
          ) : mostSharedList.length === 0 ? (
            <div className="analytics-empty-box" style={{ padding: "40px" }}>
              <p>No equipment usage logs recorded for external sharing yet.</p>
            </div>
          ) : (
            <>
              <div style={{ overflowX: "auto" }}>
                <table className="analytics-table">
                  <thead>
                    <tr>
                      <th>Instrument Name</th>
                      <th>Category</th>
                      <th>Asset Tag</th>
                      <th>External Hours Provided</th>
                      <th>Total Lab Hours</th>
                      <th>External Share %</th>
                      <th>Fees Generated</th>
                      <th>Bookings</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mostSharedList.map((item) => (
                      <tr key={item.equipmentId}>
                        <td>
                          <strong>{item.equipmentName}</strong>
                          <div style={{ fontSize: "11px", color: "#64748b" }}>
                            ID: #{item.equipmentId}
                          </div>
                        </td>
                        <td>
                          <span className="admin-status-pill" style={{ background: "#f1f5f9", color: "#334155" }}>
                            {item.categoryName || "General"}
                          </span>
                        </td>
                        <td style={{ fontFamily: "monospace", fontSize: "11px" }}>
                          {item.assetTag || "—"}
                        </td>
                        <td>
                          <strong style={{ color: "#2563eb" }}>{fmtHours(item.providedHours)}</strong>
                        </td>
                        <td>{fmtHours(item.totalUsageHours)}</td>
                        <td>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                            <div className="mini-progress-bar">
                              <div
                                className="mini-progress-fill med"
                                style={{
                                  width: `${Math.min(100, item.externalSharePercentage || 0)}%`,
                                }}
                              />
                            </div>
                            <span style={{ fontSize: "12px", fontWeight: 600 }}>
                              {fmtPercent(item.externalSharePercentage)}
                            </span>
                          </div>
                        </td>
                        <td style={{ color: "#15803d", fontWeight: 700 }}>
                          {fmtCurrency(item.feesEarned)}
                        </td>
                        <td>
                          <span className="waitlist-badge">{item.bookingCount ?? 0} sessions</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {mostShared.data?.totalPages > 1 && (
                <div className="analytics-pagination" style={{ marginTop: "16px" }}>
                  <span>
                    Page {mostShared.data.number + 1} of {mostShared.data.totalPages} ({mostShared.data.totalElements} instruments)
                  </span>
                  <div className="analytics-page-btns">
                    <button
                      type="button"
                      className="analytics-page-btn"
                      disabled={mostShared.data.number === 0}
                      onClick={() => setEquipPage((p) => Math.max(0, p - 1))}
                    >
                      ← Previous
                    </button>
                    <button
                      type="button"
                      className="analytics-page-btn"
                      disabled={mostShared.data.number >= mostShared.data.totalPages - 1}
                      onClick={() => setEquipPage((p) => p + 1)}
                    >
                      Next →
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}

