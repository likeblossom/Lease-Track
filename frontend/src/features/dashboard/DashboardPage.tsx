import { AlertTriangle, Archive, CalendarClock, FileCheck2, ScrollText } from "lucide-react";
import type { AuditEventResponse, EvidenceDocumentResponse, LeaseSummaryResponse, NoticeSummaryResponse, UserResponse } from "../../api/client";
import { DataTable, type DataTableColumn } from "../../components/ui/DataTable";
import { KpiCard } from "../../components/ui/KpiCard";
import { PageContainer } from "../../components/ui/PageContainer";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { Timeline, type TimelineItem } from "../../components/ui/Timeline";
import { complianceAlerts, dashboardKpis, evidenceSummary, recentActivity, upcomingDeadlines } from "../../mock/dashboard";

interface DashboardKpi {
  label: string;
  value: string;
  trend?: string;
  tone: "neutral" | "good" | "warning" | "danger";
}

interface Deadline {
  id: string;
  matter: string;
  property: string;
  owner: string;
  due: string;
  status: string;
}

interface ComplianceAlert {
  id: string;
  title: string;
  detail: string;
  status: string;
}

interface EvidenceSummaryItem {
  label: string;
  value: number;
}

const deadlineColumns: DataTableColumn<Deadline>[] = [
  {
    key: "matter",
    header: "Deadline",
    render: (row) => (
      <div className="table-primary-cell">
        <strong>{row.matter}</strong>
        <span>{row.property}</span>
      </div>
    )
  },
  { key: "owner", header: "Owner", render: (row) => row.owner },
  { key: "due", header: "Due", render: (row) => row.due },
  { key: "status", header: "Status", render: (row) => <StatusBadge value={row.status} /> }
];

export function DashboardPage({
  auditEvents,
  documents,
  leases,
  notices,
  user
}: {
  auditEvents: AuditEventResponse[];
  documents: EvidenceDocumentResponse[];
  leases: LeaseSummaryResponse[];
  notices: NoticeSummaryResponse[];
  user: UserResponse | null;
}) {
  const kpiIcons = [ScrollText, CalendarClock, FileCheck2, AlertTriangle];
  const useMockData = isDashboardTestUser(user);
  const data = useMockData
    ? {
        kpis: dashboardKpis,
        deadlines: upcomingDeadlines,
        activity: recentActivity,
        alerts: complianceAlerts,
        evidence: evidenceSummary,
        deadlineBadge: "5_due_soon"
      }
    : liveDashboardData({ auditEvents, documents, leases, notices });

  return (
    <PageContainer
      eyebrow="Dashboard"
      title="Overview"
      description=""
    >
      <section className="kpi-grid" aria-label="Portfolio KPI cards">
        {data.kpis.map((kpi, index) => (
          <KpiCard icon={kpiIcons[index]} key={kpi.label} label={kpi.label} value={kpi.value} trend={kpi.trend} tone={kpi.tone} />
        ))}
      </section>

      <section className="dashboard-grid">
        <article className="workspace-panel dashboard-wide">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Automation</p>
              <h2>Upcoming deadlines</h2>
            </div>
            {data.deadlineBadge ? <StatusBadge value={data.deadlineBadge} tone="warning" /> : null}
          </div>
          <DataTable columns={deadlineColumns} rows={data.deadlines} getRowKey={(row) => row.id} emptyMessage="No upcoming deadlines found." />
        </article>

        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Audit</p>
              <h2>Recent activity</h2>
            </div>
          </div>
          <Timeline items={data.activity} />
        </article>

        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Legal workflow</p>
              <h2>Compliance alerts</h2>
            </div>
          </div>
          <div className="alert-list">
            {data.alerts.length === 0 ? <p className="muted">No compliance alerts.</p> : null}
            {data.alerts.map((alert) => (
              <div className="alert-row" key={alert.id}>
                <div>
                  <strong>{alert.title}</strong>
                  <span>{alert.detail}</span>
                </div>
                <StatusBadge value={alert.status} />
              </div>
            ))}
          </div>
        </article>

        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Evidence vault</p>
              <h2>Status summary</h2>
            </div>
            <Archive aria-hidden="true" size={18} />
          </div>
          <div className="evidence-bars">
            {data.evidence.map((item) => (
              <div className="evidence-bar" key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}%</strong>
                <div>
                  <i style={{ width: `${item.value}%` }} />
                </div>
              </div>
            ))}
          </div>
        </article>
      </section>
    </PageContainer>
  );
}

function isDashboardTestUser(user: UserResponse | null) {
  const email = user?.email?.trim().toLowerCase() ?? "";
  return email === "test@example.com";
}

function liveDashboardData({
  auditEvents,
  documents,
  leases,
  notices
}: {
  auditEvents: AuditEventResponse[];
  documents: EvidenceDocumentResponse[];
  leases: LeaseSummaryResponse[];
  notices: NoticeSummaryResponse[];
}) {
  const openNotices = notices.filter((notice) => notice.status === "OPEN").length;
  const completedNotices = notices.filter((notice) => notice.status === "COMPLETED").length;
  const totalNotices = notices.length;
  const evidenceCoverage = totalNotices > 0 ? Math.round((documents.length / totalNotices) * 100) : 0;

  const kpis: DashboardKpi[] = [
    { label: "Tracked leases", value: String(leases.length), trend: "Loaded from your lease records", tone: "neutral" },
    { label: "Compliance deadlines", value: "0", trend: "Deadline automation will populate this view", tone: "neutral" },
    { label: "Open notices", value: String(openNotices), trend: `${completedNotices} completed notices`, tone: openNotices > 0 ? "warning" : "good" },
    { label: "Evidence coverage", value: `${evidenceCoverage}%`, trend: `${documents.length} documents in active context`, tone: evidenceCoverage >= 80 ? "good" : "neutral" }
  ];

  const activity: TimelineItem[] = auditEvents.slice(0, 6).map((event) => ({
    id: event.id,
    title: labelFor(event.eventType),
    description: event.details ?? "Audit event recorded on the selected notice.",
    timestamp: formatDate(event.createdAt),
    actor: labelFor(event.actorRole)
  }));

  const evidence: EvidenceSummaryItem[] = [
    { label: "Uploaded documents", value: documents.length > 0 ? 100 : 0 },
    { label: "Awaiting evidence", value: documents.length > 0 ? 0 : 100 }
  ];

  return {
    kpis,
    deadlines: [] as Deadline[],
    activity,
    alerts: [] as ComplianceAlert[],
    evidence,
    deadlineBadge: ""
  };
}

function labelFor(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(value));
}
