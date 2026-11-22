import React from "react";
import "./Dashboard.css";

export default function DashboardHeader({
  roleTitle,
  institutionName,
  title,
  subtitle,
  actions,
  badgeText,
}) {
  return (
    <div className="dashboard-header-container">
      <div className="dashboard-header-text">
        <div className="dashboard-eyebrow-row">
          {roleTitle && (
            <span className="dashboard-role-badge">
              {badgeText || roleTitle}
            </span>
          )}
          {institutionName && (
            <span className="dashboard-institution-tag">
              • {institutionName}
            </span>
          )}
        </div>
        <h1 className="dashboard-header-title">{title}</h1>
        {subtitle && <p className="dashboard-header-subtitle">{subtitle}</p>}
      </div>

      {actions && (
        <div className="dashboard-header-actions">
          {actions}
        </div>
      )}
    </div>
  );
}
