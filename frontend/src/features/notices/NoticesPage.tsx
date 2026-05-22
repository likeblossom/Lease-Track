import {
  ArrowRight,
  BadgeCheck,
  Download,
  FileCheck2,
  FileText,
  PackageCheck,
  Plus,
  RefreshCw,
  Search,
  Send,
  Upload
} from "lucide-react";
import { FormEvent, useMemo, useState } from "react";
import {
  type AuditEventResponse,
  type CreateNoticeRequest,
  type DeliveryAttemptResponse,
  type DeliveryAttemptStatus,
  type DeliveryMethod,
  type EvidenceDocumentResponse,
  type EvidenceDocumentType,
  type EvidencePackageResponse,
  type LeaseSummaryResponse,
  type ListNoticesParams,
  type NoticeResponse,
  type NoticeStatus,
  type NoticeSummaryResponse,
  type NoticeType
} from "../../api/client";
import { DataTable, type DataTableColumn } from "../../components/ui/DataTable";
import { KpiCard } from "../../components/ui/KpiCard";
import { PageContainer } from "../../components/ui/PageContainer";

export function NoticesPage({
  auditEvents,
  documents,
  evidencePackage,
  leases,
  notices,
  selectedAttempt,
  selectedNotice,
  onCreateNotice,
  onDownloadPdf,
  onGeneratePackage,
  onRefresh,
  onSaveEvidence,
  onSearch,
  onSelectNotice,
  onUpdateStatus,
  onUploadDocument
}: {
  auditEvents: AuditEventResponse[];
  documents: EvidenceDocumentResponse[];
  evidencePackage: EvidencePackageResponse | null;
  leases: LeaseSummaryResponse[];
  notices: NoticeSummaryResponse[];
  selectedAttempt: DeliveryAttemptResponse | null;
  selectedNotice: NoticeResponse | null;
  onCreateNotice: (request: CreateNoticeRequest) => Promise<void>;
  onDownloadPdf: () => Promise<void>;
  onGeneratePackage: () => Promise<void>;
  onRefresh: () => Promise<void>;
  onSaveEvidence: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onSearch: (params: ListNoticesParams) => Promise<void>;
  onSelectNotice: (noticeId: string) => Promise<void>;
  onUpdateStatus: (status: DeliveryAttemptStatus) => Promise<void>;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => Promise<void>;
}) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<NoticeStatus | "">("");
  const [noticeType, setNoticeType] = useState<NoticeType | "">("");
  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod | "">("");
  const [leaseId, setLeaseId] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const columns = useMemo(() => noticeTableColumns(onSelectNotice), [onSelectNotice]);
  const filteredNotices = useMemo(() => filterNotices(notices, query), [notices, query]);
  const openNotices = notices.filter((notice) => notice.status === "OPEN").length;
  const completedNotices = notices.filter((notice) => notice.status === "COMPLETED").length;
  const cancelledNotices = notices.filter((notice) => notice.status === "CANCELLED").length;

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSearch({
      status: status || undefined,
      noticeType: noticeType || undefined,
      deliveryMethod: deliveryMethod || undefined,
      leaseId: leaseId || undefined,
      size: 50
    });
  }

  return (
    <PageContainer
      eyebrow="Notices"
      title="Notice workflow"
      description="Create notices, update delivery progress, collect proof, and generate evidence packages from one workspace."
    >
      <section className="kpi-grid" aria-label="Notice KPI cards">
        <KpiCard icon={FileText} label="Visible notices" value={String(notices.length)} trend="Current filtered result" />
        <KpiCard icon={Send} label="Open" value={String(openNotices)} trend="Require follow-up" tone={openNotices > 0 ? "warning" : "good"} />
        <KpiCard icon={BadgeCheck} label="Completed" value={String(completedNotices)} trend="Delivery workflow closed" tone="good" />
        <KpiCard icon={PackageCheck} label="Cancelled" value={String(cancelledNotices)} trend="Stopped notices" tone={cancelledNotices > 0 ? "danger" : "neutral"} />
      </section>

      <section className="property-toolbar">
        <form className="search-control notice-search-control" onSubmit={submitSearch}>
          <Search aria-hidden="true" size={16} />
          <input
            aria-label="Search notice recipients"
            placeholder="Search recipient or type"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <select aria-label="Filter notices by status" value={status} onChange={(event) => setStatus(event.target.value as NoticeStatus | "")}>
            <option value="">All status</option>
            {NOTICE_STATUSES.map((nextStatus) => (
              <option key={nextStatus} value={nextStatus}>
                {labelFor(nextStatus)}
              </option>
            ))}
          </select>
          <select aria-label="Filter notices by type" value={noticeType} onChange={(event) => setNoticeType(event.target.value as NoticeType | "")}>
            <option value="">All types</option>
            {NOTICE_TYPES.map((type) => (
              <option key={type} value={type}>
                {labelFor(type)}
              </option>
            ))}
          </select>
          <select
            aria-label="Filter notices by delivery method"
            value={deliveryMethod}
            onChange={(event) => setDeliveryMethod(event.target.value as DeliveryMethod | "")}
          >
            <option value="">All delivery</option>
            {DELIVERY_METHODS.map((method) => (
              <option key={method} value={method}>
                {labelFor(method)}
              </option>
            ))}
          </select>
          <select aria-label="Filter notices by lease" value={leaseId} onChange={(event) => setLeaseId(event.target.value)}>
            <option value="">All leases</option>
            {leases.map((lease) => (
              <option key={lease.id} value={lease.id}>
                {noticeLeaseLabel(lease)}
              </option>
            ))}
          </select>
          <button className="secondary-action compact" type="submit">
            Search
          </button>
        </form>
        <button className="secondary-action compact" type="button" onClick={onRefresh}>
          <RefreshCw aria-hidden="true" size={16} />
          <span>Refresh</span>
        </button>
      </section>

      <section className="notices-layout">
        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Queue</p>
              <h2>Notice list</h2>
            </div>
            <button className="primary-action compact-action" type="button" onClick={() => setIsCreating((current) => !current)}>
              <Plus aria-hidden="true" size={16} />
              <span>Notice</span>
            </button>
          </div>
          {isCreating ? (
            <NoticeForm
              leases={leases}
              onSubmit={onCreateNotice}
              onCreated={() => setIsCreating(false)}
            />
          ) : null}
          <div className="notice-queue-table">
            <DataTable columns={columns} rows={filteredNotices} getRowKey={(row) => row.id} emptyMessage="No notices found." />
          </div>
        </article>

        <article className="workspace-panel notice-record-panel">
          {selectedNotice && selectedAttempt ? (
            <NoticeRecord
              auditEvents={auditEvents}
              attempt={selectedAttempt}
              documents={documents}
              evidencePackage={evidencePackage}
              notice={selectedNotice}
              onDownloadPdf={onDownloadPdf}
              onGeneratePackage={onGeneratePackage}
              onSaveEvidence={onSaveEvidence}
              onUpdateStatus={onUpdateStatus}
              onUploadDocument={onUploadDocument}
            />
          ) : (
            <div className="empty-state compact">
              <FileText aria-hidden="true" size={28} />
              <h2>Select a notice</h2>
              <p>Delivery attempts, evidence, documents, packages, and audit history will appear here.</p>
            </div>
          )}
        </article>
      </section>
    </PageContainer>
  );
}

function NoticeForm({
  leases,
  onCreated,
  onSubmit
}: {
  leases: LeaseSummaryResponse[];
  onCreated: () => void;
  onSubmit: (request: CreateNoticeRequest) => Promise<void>;
}) {
  async function submitNotice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const deadlineValue = String(form.get("deadlineAt") ?? "");
    await onSubmit({
      recipientName: String(form.get("recipientName") ?? ""),
      recipientContactInfo: String(form.get("recipientContactInfo") ?? ""),
      noticeType: String(form.get("noticeType") ?? "RENT_INCREASE") as NoticeType,
      deliveryMethod: String(form.get("deliveryMethod") ?? "REGISTERED_MAIL") as DeliveryMethod,
      leaseId: optionalString(form.get("leaseId")),
      deadlineAt: deadlineValue ? new Date(deadlineValue).toISOString() : null,
      notes: optionalString(form.get("notes"))
    });
    formElement.reset();
    onCreated();
  }

  return (
    <form className="notice-form-grid" onSubmit={submitNotice}>
      <Field label="Recipient" name="recipientName" required />
      <Field label="Contact" name="recipientContactInfo" required />
      <label className="field">
        <span>Lease</span>
        <select name="leaseId">
          <option value="">No lease link</option>
          {leases.map((lease) => (
            <option key={lease.id} value={lease.id}>
              {noticeLeaseLabel(lease)}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Notice type</span>
        <select name="noticeType" defaultValue="RENT_INCREASE">
          {NOTICE_TYPES.map((type) => (
            <option key={type} value={type}>
              {labelFor(type)}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Delivery method</span>
        <select name="deliveryMethod" defaultValue="REGISTERED_MAIL">
          {DELIVERY_METHODS.map((method) => (
            <option key={method} value={method}>
              {labelFor(method)}
            </option>
          ))}
        </select>
      </label>
      <Field label="Deadline" name="deadlineAt" type="datetime-local" />
      <label className="field notice-form-notes">
        <span>Notes</span>
        <textarea name="notes" rows={3} />
      </label>
      <button className="primary-action notice-form-submit" type="submit">
        <span>Create notice</span>
        <ArrowRight aria-hidden="true" size={18} />
      </button>
    </form>
  );
}

function NoticeRecord({
  auditEvents,
  attempt,
  documents,
  evidencePackage,
  notice,
  onDownloadPdf,
  onGeneratePackage,
  onSaveEvidence,
  onUpdateStatus,
  onUploadDocument
}: {
  auditEvents: AuditEventResponse[];
  attempt: DeliveryAttemptResponse;
  documents: EvidenceDocumentResponse[];
  evidencePackage: EvidencePackageResponse | null;
  notice: NoticeResponse;
  onDownloadPdf: () => Promise<void>;
  onGeneratePackage: () => Promise<void>;
  onSaveEvidence: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onUpdateStatus: (status: DeliveryAttemptStatus) => Promise<void>;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => Promise<void>;
}) {
  const [isGeneratingPackage, setIsGeneratingPackage] = useState(false);

  async function generatePackage() {
    setIsGeneratingPackage(true);
    try {
      await onGeneratePackage();
    } finally {
      setIsGeneratingPackage(false);
    }
  }

  return (
    <>
      <div className={`notice-record-header notice-status-${notice.status.toLowerCase()}`}>
        <div>
          <p className="eyebrow">Notice record</p>
          <h2>{notice.recipientName}</h2>
          <p className="muted">{notice.recipientContactInfo}</p>
        </div>
        <NoticeStatusBadge value={notice.status} />
      </div>

      <div className="detail-meta">
        <Metric label="Type" value={labelFor(notice.noticeType)} />
        <Metric label="Delivery" value={labelFor(attempt.deliveryMethod)} />
        <Metric label="Attempt" value={labelFor(attempt.status)} tone={attempt.status.toLowerCase()} />
      </div>

      <div className="notice-action-bar" aria-label="Delivery status actions">
        {(["SENT", "DELIVERED", "FAILED", "CANCELLED"] as DeliveryAttemptStatus[]).map((nextStatus) => (
          <button
            key={nextStatus}
            className={`secondary-action status-action status-action-${nextStatus.toLowerCase()}`}
            type="button"
            onClick={() => onUpdateStatus(nextStatus)}
          >
            <Send aria-hidden="true" size={16} />
            <span>{labelFor(nextStatus)}</span>
          </button>
        ))}
      </div>

      <div className="detail-sections">
        <section className="notice-section">
          <SectionHeading
            title="Evidence"
            meta={attempt.trackingSyncStatus ? `Tracking ${labelFor(attempt.trackingSyncStatus)}` : undefined}
          />
          <form className="compact-form two-column" onSubmit={onSaveEvidence}>
            <Field label="Carrier" name="carrierName" placeholder="Canada Post" />
            <Field label="Tracking number" name="trackingNumber" />
            <Field label="Tracking URL" name="trackingUrl" type="url" />
            <Field label="Receipt reference" name="carrierReceiptRef" />
            <label className="field">
              <span>Confirmation notes</span>
              <textarea name="deliveryConfirmationMetadata" rows={2} />
            </label>
            <label className="checkbox-field">
              <input name="deliveryConfirmation" type="checkbox" />
              <span>Delivery confirmed</span>
            </label>
            <button className="primary-action" type="submit">
              <span>Save evidence</span>
              <BadgeCheck aria-hidden="true" size={18} />
            </button>
          </form>
        </section>

        <section className="notice-section">
          <SectionHeading title="Documents" meta={`${documents.length} uploaded`} />
          <form className="compact-form upload-form" onSubmit={onUploadDocument}>
            <label className="field">
              <span>Document type</span>
              <select name="documentType" defaultValue="CARRIER_RECEIPT">
                {DOCUMENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {labelFor(type)}
                  </option>
                ))}
              </select>
            </label>
            <Field label="File" name="file" type="file" required />
            <button className="secondary-action" type="submit">
              <Upload aria-hidden="true" size={16} />
              <span>Upload document</span>
            </button>
          </form>
          <ul className="document-list">
            {documents.length === 0 ? <li className="muted">No documents uploaded.</li> : null}
            {documents.map((document) => (
              <li key={document.id}>
                <FileCheck2 aria-hidden="true" size={16} />
                <span>{document.originalFilename}</span>
                <small>{labelFor(document.documentType)}</small>
              </li>
            ))}
          </ul>
        </section>

        <section className="notice-section package-section">
          <SectionHeading
            title="Evidence package"
            meta={evidencePackage ? `Hash ${shortHash(evidencePackage.packageHash)}` : "Not generated"}
          />
          <div className="package-actions">
            <button className="secondary-action" type="button" onClick={generatePackage} disabled={isGeneratingPackage}>
              <PackageCheck aria-hidden="true" size={16} />
              <span>{isGeneratingPackage ? "Generating..." : "Generate package"}</span>
            </button>
            <button className="secondary-action" type="button" onClick={onDownloadPdf}>
              <Download aria-hidden="true" size={16} />
              <span>Download PDF</span>
            </button>
          </div>
          {evidencePackage ? (
            <>
              <div className="package-success-banner">
                <PackageCheck aria-hidden="true" size={18} />
                <div>
                  <strong>Package generated</strong>
                  <span>{formatDateTime(evidencePackage.generatedAt)} · Version {evidencePackage.packageVersion}</span>
                </div>
              </div>
              <div className="package-summary">
                <Metric
                  label="Strength"
                  value={labelFor(evidencePackage.strongestEvidenceStrength)}
                  tone={`strength-${evidencePackage.strongestEvidenceStrength.toLowerCase()}`}
                />
                <Metric label="Documents" value={String(evidencePackage.evidenceDocuments.length)} />
                <Metric label="Audit events" value={String(evidencePackage.auditEvents.length)} />
              </div>
            </>
          ) : (
            <p className="package-empty-state">No evidence package has been generated for this notice yet.</p>
          )}
        </section>

        <section className="notice-section">
          <SectionHeading title="Audit log" meta={`${auditEvents.length} events`} />
          <ol className="audit-list">
            {auditEvents.length === 0 ? <li className="muted">No audit events yet.</li> : null}
            {auditEvents.slice(0, 8).map((event) => (
              <li key={event.id}>
                <strong>{labelFor(event.eventType)}</strong>
                <span>{formatDateTime(event.createdAt)}</span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </>
  );
}

function Field({
  label,
  name,
  placeholder,
  required,
  type = "text"
}: {
  label: string;
  name: string;
  placeholder?: string;
  required?: boolean;
  type?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input name={name} placeholder={placeholder} required={required} type={type} />
    </label>
  );
}

function SectionHeading({ meta, title }: { meta?: string; title: string }) {
  return (
    <div className="section-heading-inline">
      <h3>{title}</h3>
      {meta ? <span>{meta}</span> : null}
    </div>
  );
}

function Metric({ label, tone, value }: { label: string; tone?: string; value: string }) {
  return (
    <div className={`metric ${tone ? `metric-${tone}` : ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function NoticeStatusBadge({ value }: { value: NoticeStatus }) {
  return <span className={`notice-status-badge notice-status-${value.toLowerCase()}`}>{labelFor(value)}</span>;
}

function noticeTableColumns(onSelectNotice: (noticeId: string) => Promise<void>): DataTableColumn<NoticeSummaryResponse>[] {
  return [
    {
      key: "recipient",
      header: "Recipient",
      render: (row) => (
        <div className={`notice-primary-cell notice-status-${row.status.toLowerCase()}`}>
          <button className="table-link" type="button" onClick={() => onSelectNotice(row.id)}>
            {row.recipientName}
          </button>
          <span>{labelFor(row.noticeType)}</span>
        </div>
      )
    },
    { key: "status", header: "Status", render: (row) => <NoticeStatusBadge value={row.status} /> },
    { key: "created", header: "Created", render: (row) => formatDateOnly(row.createdAt) },
    { key: "updated", header: "Updated", render: (row) => formatDateOnly(row.updatedAt) }
  ];
}

function filterNotices(notices: NoticeSummaryResponse[], query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return notices;
  }
  return notices.filter((notice) =>
    [notice.recipientName, notice.noticeType, notice.status]
      .join(" ")
      .toLowerCase()
      .includes(normalized)
  );
}

function noticeLeaseLabel(lease: LeaseSummaryResponse) {
  return [firstLine(lease.tenantNames), lease.propertyAddress].filter(Boolean).join(" - ");
}

function firstLine(value: string) {
  return value.split(/\n|,|;/).map((part) => part.trim()).filter(Boolean)[0] ?? value;
}

function optionalString(value: FormDataEntryValue | null) {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(value));
}

function shortHash(value?: string | null) {
  if (!value) {
    return "Pending";
  }
  return value.length > 12 ? value.slice(0, 12) : value;
}

function labelFor(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}

const NOTICE_STATUSES: NoticeStatus[] = ["OPEN", "COMPLETED", "CANCELLED"];
const NOTICE_TYPES: NoticeType[] = [
  "RENEWAL_REFUSAL",
  "RENT_INCREASE",
  "NON_PAYMENT",
  "TERMINATION",
  "LEASE_MODIFICATION",
  "OTHER"
];
const DELIVERY_METHODS: DeliveryMethod[] = [
  "REGISTERED_MAIL",
  "HAND_DELIVERY_SIGNATURE",
  "EMAIL_ACKNOWLEDGEMENT",
  "BAILIFF"
];
const DOCUMENT_TYPES: EvidenceDocumentType[] = [
  "CARRIER_RECEIPT",
  "DELIVERY_CONFIRMATION",
  "SIGNED_ACKNOWLEDGEMENT",
  "EMAIL_ACKNOWLEDGEMENT",
  "BAILIFF_AFFIDAVIT",
  "OTHER"
];
