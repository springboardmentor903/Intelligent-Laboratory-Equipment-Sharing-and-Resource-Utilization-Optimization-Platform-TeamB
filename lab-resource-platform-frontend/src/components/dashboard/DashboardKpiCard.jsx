import React from "react";
import { Link } from "react-router-dom";
import { FiArrowRight } from "react-icons/fi";
import "./Dashboard.css";

export default function DashboardKpiCard({
  title,
  value,
  unit,
  subtitle,
  icon: Icon,
  variant = "primary",
  to,
  loading = false,
}) {
  const content = (
    <>
      <div className="dashboard-kpi-top">
        <span className="dashboard-kpi-label">{title}</span>
        {Icon && (
          <div className={`dashboard-kpi-icon-wrap variant-${variant}`} aria-hidden="true">
            <Icon size={18} />
          </div>
        )}
      </div>

      <div className="dashboard-kpi-bottom">
        <div className="dashboard-kpi-value-row">
          <span className="dashboard-kpi-value">
            {loading ? "—" : value ?? "0"}
          </span>
          {unit && <span className="dashboard-kpi-unit">{unit}</span>}
        </div>

        {subtitle && (
          <p className="dashboard-kpi-subtext">
            <span>{subtitle}</span>
            {to && <FiArrowRight size={13} style={{ marginLeft: "auto" }} />}
          </p>
        )}
      </div>
    </>
  );

  if (to) {
    return (
      <Link to={to} className="dashboard-kpi-card interactive" title={`View ${title}`}>
        {content}
      </Link>
    );
  }

  return (
    <div className="dashboard-kpi-card">
      {content}
    </div>
  );
}
