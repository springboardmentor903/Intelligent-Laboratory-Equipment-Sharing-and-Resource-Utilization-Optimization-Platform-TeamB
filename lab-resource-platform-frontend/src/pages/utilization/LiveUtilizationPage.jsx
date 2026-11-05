import { useCallback, useEffect, useRef, useState } from "react";
import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { useSearch } from "../../context/SearchContext";
import FilterDropdown from "../../components/FilterDropdown";

function toISTTime(iso) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleTimeString("en-IN", {
      timeZone: "Asia/Kolkata",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
    });
  } catch {
    return iso;
  }
}

function formatDuration(minutes) {
  if (!minutes && minutes !== 0) return "—";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}m`;
  return `${h}h ${m}m`;
}

export default function LiveUtilizationPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { searchTerm, setSearchPlaceholder } = useSearch();

  const isStaff = [
    "INSTITUTION_ADMINISTRATOR",
    "DEPARTMENT_HEAD",
    "LAB_MANAGER",
    "LAB_TECHNICIAN",
  ].includes(user?.role);

  const [colleges, setColleges] = useState([]);
  const [selectedCollegeId, setSelectedCollegeId] = useState("");
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [wsConnected, setWsConnected] = useState(false);
  const [checkInModal, setCheckInModal] = useState(null); // equipment object
  const [checkInNotes, setCheckInNotes] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [selectedLogsEquipment, setSelectedLogsEquipment] = useState(null);
  const [equipmentLogs, setEquipmentLogs] = useState([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  const wsRef = useRef(null);

  useEffect(() => {
    setSearchPlaceholder("Search live instruments by name, model, specs, user...");
  }, [setSearchPlaceholder]);

  // Load colleges list for System Admin
  useEffect(() => {
    if (!isStaff) {
      api.get("/api/colleges")
        .then((res) => setColleges(res.data || []))
        .catch((err) => console.error("Could not fetch colleges", err));
    }
  }, [isStaff]);

  // Fetch summary and live status
  const fetchSummary = useCallback(async () => {
    try {
      const params = selectedCollegeId ? { collegeId: selectedCollegeId } : {};
      const res = await api.get("/api/utilization/summary", { params });
      setSummary(res.data);
    } catch (err) {
      console.error("Failed to load utilization data", err);
    } finally {
      setLoading(false);
    }
  }, [selectedCollegeId]);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  // Connect WebSocket for Real-Time Updates
  useEffect(() => {
    let ws = null;
    let reconnectTimeout = null;

    function connect() {
      try {
        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const wsUrl = `${protocol}//${window.location.hostname}:8080/ws/utilization`;
        ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
          setWsConnected(true);
        };

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.type === "CHECK_IN" || data.type === "CHECK_OUT" || data.type === "STATUS_CHANGE") {
              // Real-time update broadcast received! Refresh live status
              fetchSummary();
            }
          } catch (e) {
            // non-json or ping
          }
        };

        ws.onclose = () => {
          setWsConnected(false);
          // Try reconnect after 5 seconds
          reconnectTimeout = setTimeout(connect, 5000);
        };

        ws.onerror = () => {
          setWsConnected(false);
        };
      } catch (e) {
        setWsConnected(false);
      }
    }

    connect();

    // Fallback polling every 8s to guarantee real-time elapsed minutes
    const interval = setInterval(fetchSummary, 8000);

    return () => {
      clearInterval(interval);
      if (reconnectTimeout) clearTimeout(reconnectTimeout);
      if (ws) ws.close();
    };
  }, [fetchSummary]);

  // Handle Check-In / Session Start
  async function handleConfirmCheckIn() {
    if (!checkInModal) return;
    setActionLoading(true);
    try {
      await api.post("/api/utilization/check-in", {
        equipmentId: checkInModal.equipmentId,
        notes: checkInNotes.trim(),
      });
      toast.success(`Session started on "${checkInModal.equipmentName}". Equipment marked IN USE.`);
      setCheckInModal(null);
      setCheckInNotes("");
      fetchSummary();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to start session.");
    } finally {
      setActionLoading(false);
    }
  }

  // Handle Check-Out / Session Stop
  async function handleCheckOut(item) {
    if (!window.confirm(`Stop active session for "${item.equipmentName}"?`)) {
      return;
    }
    setActionLoading(true);
    try {
      if (item.currentUtilizationId) {
        await api.post(`/api/utilization/${item.currentUtilizationId}/check-out`, {
          notes: "Completed session via Live Monitor",
        });
      } else if (item.currentBookingId) {
        await api.post(`/api/utilization/booking/${item.currentBookingId}/check-out`, {
          notes: "Completed session via Live Monitor",
        });
      } else {
        await api.patch(`/api/equipment/${item.equipmentId}/status`, {
          status: "AVAILABLE",
        });
      }
      toast.success(`Session stopped. "${item.equipmentName}" is now Available.`);
      fetchSummary();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to stop session.");
    } finally {
      setActionLoading(false);
    }
  }

  // Fetch Usage History Logs for an equipment
  async function handleOpenLogs(item) {
    setSelectedLogsEquipment(item);
    setLoadingLogs(true);
    try {
      const res = await api.get(`/api/utilization/equipment/${item.equipmentId}/logs`);
      setEquipmentLogs(res.data || []);
    } catch (err) {
      toast.error("Could not load usage logs.");
    } finally {
      setLoadingLogs(false);
    }
  }

  const currentCollegeTitle = isStaff
    ? user?.collegeName || "Your College"
    : selectedCollegeId
    ? colleges.find((c) => String(c.id) === String(selectedCollegeId))?.name || "Selected College"
    : "All Partner Colleges";

  const rawEquipments = summary?.equipments || [];
  const equipmentsList = rawEquipments.filter((item) => {
    if (!searchTerm) return true;
    const q = searchTerm.toLowerCase();
    return (
      item.equipmentName?.toLowerCase().includes(q) ||
      item.category?.toLowerCase().includes(q) ||
      item.model?.toLowerCase().includes(q) ||
      item.currentUserName?.toLowerCase().includes(q) ||
      item.collegeName?.toLowerCase().includes(q) ||
      item.location?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="admin-page-container">
      {/* Header */}
      <div className="admin-header-row">
        <div>
          <div className="util-eyebrow-row">
            <p className="admin-eyebrow">REAL-TIME MONITORING &amp; TELEMETRY</p>
            <div className={`util-ws-badge ${wsConnected ? "connected" : "reconnecting"}`}>
              <span className="util-ws-dot" />
              <span>{wsConnected ? "WebSocket Stream: Live" : "Stream: Polling Active"}</span>
            </div>
          </div>
          <h1 className="admin-title">Live Equipment Utilization &amp; Status</h1>
          <p className="admin-subtitle">
            Real-time telemetry, session check-in / check-out controls, and live utilization vs. capacity metrics for {currentCollegeTitle}.
          </p>
        </div>

        <div className="admin-header-actions" style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          {!isStaff && colleges.length > 0 && (
            <FilterDropdown
              colleges={colleges}
              selectedCollegeId={selectedCollegeId}
              onCollegeChange={setSelectedCollegeId}
              showCollege={true}
              showCategory={false}
              showStatus={false}
            />
          )}

          <button
            type="button"
            className="admin-btn-secondary"
            onClick={fetchSummary}
            title="Refresh Data"
          >
            🔄 Refresh
          </button>
        </div>
      </div>

      {/* KPI Stats Summary Cards */}
      <div className="util-kpi-grid">
        <div className="util-kpi-card util-kpi-active">
          <div className="util-kpi-header">
            <span className="util-kpi-icon">⚡</span>
            <span className="util-kpi-title">In Active Use</span>
          </div>
          <div className="util-kpi-num">
            {summary?.inUseCount || 0}
            <span className="util-kpi-pulse" />
          </div>
          <span className="util-kpi-sub">
            {summary?.totalEquipments ? `${Math.round(((summary.inUseCount || 0) / summary.totalEquipments) * 100)}% of laboratory fleet` : "0% fleet"}
          </span>
        </div>

        <div className="util-kpi-card util-kpi-avail">
          <div className="util-kpi-header">
            <span className="util-kpi-icon">✅</span>
            <span className="util-kpi-title">Available for Booking</span>
          </div>
          <div className="util-kpi-num">{summary?.availableCount || 0}</div>
          <span className="util-kpi-sub">Ready for check-in</span>
        </div>

        <div className="util-kpi-card util-kpi-rate">
          <div className="util-kpi-header">
            <span className="util-kpi-icon">📈</span>
            <span className="util-kpi-title">Average Utilization</span>
          </div>
          <div className="util-kpi-num">{summary?.overallUtilizationRatePercent || 0}%</div>
          <span className="util-kpi-sub">Daily capacity rate</span>
        </div>

        <div className="util-kpi-card util-kpi-hours">
          <div className="util-kpi-header">
            <span className="util-kpi-icon">⏱️</span>
            <span className="util-kpi-title">Usage Logged Today</span>
          </div>
          <div className="util-kpi-num">{formatDuration(summary?.totalUsageMinutesToday || 0)}</div>
          <span className="util-kpi-sub">Cumulative active minutes</span>
        </div>
      </div>

      {/* Equipment Live Telemetry Grid */}
      <div className="util-section-header">
        <h2 className="util-section-title">Laboratory Instruments Live Telemetry</h2>
        <span className="util-section-count">
          {equipmentsList.length} instruments monitored
        </span>
      </div>

      {loading ? (
        <div className="admin-loading-state">Connecting to telemetry stream…</div>
      ) : equipmentsList.length === 0 ? (
        <div className="admin-empty-card">
          <h3>No Instruments Cataloged</h3>
          <p>No equipment records found for {currentCollegeTitle}.</p>
        </div>
      ) : (
        <div className="util-equipment-grid">
          {equipmentsList.map((item) => {
            const inUse = Boolean(item.isCurrentlyInUse || item.status === "IN_USE" || item.currentUtilizationId);
            const ratePercent = Math.min(100, item.todayUtilizationRatePercent || 0);

            return (
              <div
                key={item.equipmentId}
                className={`util-card ${inUse ? "card-in-use" : "card-available"}`}
              >
                {/* Card Top */}
                <div className="util-card-header">
                  <span className="admin-badge-college">{item.collegeName}</span>
                  <span className={`util-status-pill ${inUse ? "pill-in-use" : "pill-available"}`}>
                    <span className="util-status-dot" />
                    {inUse ? "IN USE" : item.status}
                  </span>
                </div>

                {/* Instrument Info */}
                <div className="util-card-body">
                  <h3 className="util-card-title">{item.equipmentName}</h3>
                  <div className="util-meta-row">
                    <span>📁 {item.category || "General"}</span>
                    <span>🏷️ {item.model || "Standard"}</span>
                  </div>
                  <div className="util-location">📍 {item.location || "Main Lab"}</div>

                  {/* Active Session Telemetry */}
                  {inUse ? (
                    <div className="util-active-session-box">
                      <div className="util-active-title">
                        <span className="util-pulse-dot" /> Live Active Session
                      </div>
                      <div className="util-active-user">
                        <strong>Operator:</strong> {item.currentUserName || item.currentUserEmail || "User in Lab"}
                      </div>
                      <div className="util-active-time">
                        <span>Started: {toISTTime(item.sessionStartTime)}</span>
                        <span className="util-elapsed-badge">⏱ {item.elapsedMinutes || 0}m elapsed</span>
                      </div>
                    </div>
                  ) : (
                    <div className="util-idle-box">
                      <span>💤 Instrument is currently idle and ready for check-in.</span>
                    </div>
                  )}

                  {/* Utilization Progress Bar */}
                  <div className="util-progress-section">
                    <div className="util-progress-label">
                      <span>Today's Utilization:</span>
                      <strong>{ratePercent}%</strong>
                    </div>
                    <div className="util-progress-bar">
                      <div
                        className={`util-progress-fill ${ratePercent > 70 ? "high" : ratePercent > 35 ? "med" : "low"}`}
                        style={{ width: `${ratePercent}%` }}
                      />
                    </div>
                    <div className="util-progress-sub">
                      <span>Completed: {item.totalSessionsCompleted} sessions</span>
                      <span>Total: {formatDuration(item.totalUsageMinutesToday)}</span>
                    </div>
                  </div>
                </div>

                {/* Actions Footer */}
                <div className="util-card-footer">
                  {inUse ? (
                    <button
                      type="button"
                      className="util-stop-btn"
                      disabled={actionLoading}
                      onClick={() => handleCheckOut(item)}
                    >
                      ⏹️ Check-Out / Stop
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="util-start-btn"
                      disabled={actionLoading || item.status !== "AVAILABLE"}
                      onClick={() => {
                        setCheckInModal(item);
                        setCheckInNotes("");
                      }}
                    >
                      ▶️ Check-In / Start
                    </button>
                  )}

                  <button
                    type="button"
                    className="util-logs-btn"
                    onClick={() => handleOpenLogs(item)}
                    title="View historical check-in/out logs"
                  >
                    📋 Logs
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Check-In Modal */}
      {checkInModal && (
        <div className="avail-modal-overlay" onClick={() => setCheckInModal(null)}>
          <div className="avail-modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 520 }}>
            <div className="avail-modal-header">
              <div className="avail-modal-title-group">
                <span className="avail-modal-icon">▶️</span>
                <div>
                  <h2 className="avail-modal-title">Equipment Session Check-In</h2>
                  <p className="avail-modal-subtitle">{checkInModal.equipmentName}</p>
                </div>
              </div>
              <button
                type="button"
                className="avail-modal-close"
                onClick={() => setCheckInModal(null)}
              >
                ✕
              </button>
            </div>

            <div className="avail-modal-body">
              <div className="util-modal-info">
                <p>
                  Starting a session will immediately mark <strong>{checkInModal.equipmentName}</strong> as <strong>IN USE</strong> and begin logging live usage.
                </p>
              </div>

              <div className="booking-field" style={{ marginTop: 16 }}>
                <label className="booking-label">Operator Notes / Purpose (Optional):</label>
                <textarea
                  className="booking-textarea"
                  rows={3}
                  placeholder="e.g., Major research prototype run, laser calibration..."
                  value={checkInNotes}
                  onChange={(e) => setCheckInNotes(e.target.value)}
                />
              </div>
            </div>

            <div className="avail-modal-footer" style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button
                type="button"
                className="booking-cancel-btn"
                onClick={() => setCheckInModal(null)}
                disabled={actionLoading}
              >
                Cancel
              </button>
              <button
                type="button"
                className="booking-submit-btn"
                style={{ width: "auto", padding: "10px 24px" }}
                disabled={actionLoading}
                onClick={handleConfirmCheckIn}
              >
                {actionLoading ? "Starting…" : "Confirm Check-In"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Historical Usage Logs Drawer / Modal */}
      {selectedLogsEquipment && (
        <div className="avail-modal-overlay" onClick={() => setSelectedLogsEquipment(null)}>
          <div className="avail-modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 680 }}>
            <div className="avail-modal-header">
              <div className="avail-modal-title-group">
                <span className="avail-modal-icon">📋</span>
                <div>
                  <h2 className="avail-modal-title">Equipment Usage Logs</h2>
                  <p className="avail-modal-subtitle">{selectedLogsEquipment.equipmentName}</p>
                </div>
              </div>
              <button
                type="button"
                className="avail-modal-close"
                onClick={() => setSelectedLogsEquipment(null)}
              >
                ✕
              </button>
            </div>

            <div className="avail-modal-body" style={{ maxHeight: "65vh" }}>
              {loadingLogs ? (
                <div className="avail-loading">Loading usage logs…</div>
              ) : equipmentLogs.length === 0 ? (
                <div className="avail-empty">
                  <h3>No Historical Logs Yet</h3>
                  <p>Check-in sessions will record timestamped telemetry and duration here.</p>
                </div>
              ) : (
                <div className="util-logs-list">
                  {equipmentLogs.map((log) => (
                    <div key={log.id} className="util-log-item">
                      <div className="util-log-top">
                        <span className={`util-status-pill ${log.status === "ACTIVE" ? "pill-in-use" : "pill-available"}`}>
                          {log.status}
                        </span>
                        <span className="util-log-duration">
                          {log.actualDurationMinutes ? `${log.actualDurationMinutes} mins` : "In Progress"}
                        </span>
                      </div>
                      <div className="util-log-user">
                        👤 <strong>{log.userName || "Student"}</strong> ({log.userEmail})
                      </div>
                      <div className="util-log-times">
                        <div>
                          <strong>Check-In:</strong> {toISTTime(log.checkInTime)}
                        </div>
                        {log.checkOutTime && (
                          <div>
                            <strong>Check-Out:</strong> {toISTTime(log.checkOutTime)}
                          </div>
                        )}
                      </div>
                      {log.operatorNotes && (
                        <div className="util-log-notes">
                          <em>Note:</em> {log.operatorNotes}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="avail-modal-footer">
              <span className="avail-footer-hint">
                All logs are stored with exact check-in and check-out timestamps in Indian Standard Time (IST).
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

