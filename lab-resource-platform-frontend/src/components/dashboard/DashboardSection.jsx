import React from "react";
import { Link } from "react-router-dom";
import { FiArrowRight } from "react-icons/fi";
import "./Dashboard.css";

export default function DashboardSection({
  title,
  subtitle,
  actionText,
  actionTo,
  onActionClick,
  actionButton,
  icon: Icon,
  noPadding = false,
  className = "",
  children,
}) {
  return (
    <section className={`dashboard-section-card ${className}`}>
      {(title || actionText || actionButton) && (
        <header className="dashboard-section-header">
          <div className="dashboard-section-title-wrap">
            {Icon && <Icon size={18} color="var(--text-secondary)" aria-hidden="true" />}
            <div>
              <h3>{title}</h3>
              {subtitle && <p className="dashboard-section-subtitle">{subtitle}</p>}
            </div>
          </div>

          <div>
            {actionButton}
            {actionTo && actionText && (
              <Link to={actionTo} className="dashboard-section-action-link">
                <span>{actionText}</span>
                <FiArrowRight size={14} />
              </Link>
            )}
            {onActionClick && actionText && (
              <button
                type="button"
                onClick={onActionClick}
                className="dashboard-section-action-link"
                style={{ background: "none", border: "none", cursor: "pointer", padding: 0 }}
              >
                <span>{actionText}</span>
                <FiArrowRight size={14} />
              </button>
            )}
          </div>
        </header>
      )}

      <div className={`dashboard-section-body ${noPadding ? "no-padding" : ""}`}>
        {children}
      </div>
    </section>
  );
}
