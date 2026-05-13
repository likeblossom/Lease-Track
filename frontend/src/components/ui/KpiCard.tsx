import type { LucideIcon } from "lucide-react";

export function KpiCard({
  icon: Icon,
  label,
  value,
  trend,
  tone = "neutral"
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  trend?: string;
  tone?: "neutral" | "good" | "warning" | "danger";
}) {
  return (
    <article className={`kpi-card ${tone}`}>
      <span className="kpi-icon">
        <Icon aria-hidden="true" size={18} />
      </span>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
        {trend ? <small>{trend}</small> : null}
      </div>
    </article>
  );
}
