import { ArrowRight, CalendarClock, FileText, Plus, RefreshCw, Search, Send, Users } from "lucide-react";
import { FormEvent, useMemo, useState } from "react";
import {
  type CreateNoticeRequest,
  type LeaseResponse,
  type LeaseStatus,
  type LeaseSummaryResponse,
  type NoticeSummaryResponse,
  type NoticeType,
  type PropertySummaryResponse,
  type RenewLeaseRequest
} from "../../api/client";
import { DataTable, type DataTableColumn } from "../../components/ui/DataTable";
import { KpiCard } from "../../components/ui/KpiCard";
import { PageContainer } from "../../components/ui/PageContainer";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { Timeline } from "../../components/ui/Timeline";

type LeaseTab = "overview" | "timeline" | "documents" | "notices";

export interface CreateLeaseFormValue {
  name: string;
  propertyAddress: string;
  unitId?: string | null;
  tenantNames: string;
  tenantEmail?: string | null;
  tenantPhone?: string | null;
  leaseStartDate: string;
  leaseEndDate: string;
  rentCents: number;
  notes?: string | null;
}

export function LeasesPage({
  leases,
  properties,
  selectedLease,
  onCreateLease,
  onCreateNotice,
  onRefresh,
  onRequestRenewal,
  onRenewLease,
  onSearch,
  onSelectLease,
  onTerminateLease
}: {
  leases: LeaseSummaryResponse[];
  properties: PropertySummaryResponse[];
  selectedLease: LeaseResponse | null;
  onCreateLease: (request: CreateLeaseFormValue) => Promise<void>;
  onCreateNotice: (request: CreateNoticeRequest) => Promise<void>;
  onRefresh: () => Promise<void>;
  onRequestRenewal: (leaseId: string) => Promise<void>;
  onRenewLease: (leaseId: string, request: RenewLeaseRequest) => Promise<void>;
  onSearch: (query: string, status?: LeaseStatus) => Promise<void>;
  onSelectLease: (leaseId: string) => Promise<void>;
  onTerminateLease: (leaseId: string, reason?: string | null) => Promise<void>;
}) {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<LeaseStatus | "">("");
  const [isCreating, setIsCreating] = useState(false);
  const columns = useMemo(() => leaseTableColumns(onSelectLease), [onSelectLease]);

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSearch(query, status || undefined);
  }

  return (
    <PageContainer
      eyebrow="Leases"
      title="Lease lifecycle management"
      description="Create, monitor, renew, and audit lease records from a CRM-style operational workspace."
    >
      <section className="property-toolbar">
        <form className="search-control" onSubmit={submitSearch}>
          <Search aria-hidden="true" size={16} />
          <input
            aria-label="Search leases"
            placeholder="Search unit, tenant, property"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <select aria-label="Filter by status" value={status} onChange={(event) => setStatus(event.target.value as LeaseStatus | "")}>
            <option value="">All status</option>
            {LEASE_STATUSES.map((leaseStatus) => (
              <option key={leaseStatus} value={leaseStatus}>{labelFor(leaseStatus)}</option>
            ))}
          </select>
          <button className="secondary-action compact" type="submit">Search</button>
        </form>
        <button className="secondary-action compact" type="button" onClick={onRefresh}>
          <RefreshCw aria-hidden="true" size={16} />
          <span>Refresh</span>
        </button>
      </section>

      <section className="leases-layout">
        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Pipeline</p>
              <h2>Lease list</h2>
            </div>
            <button className="primary-action compact-action" type="button" onClick={() => setIsCreating((current) => !current)}>
              <Plus aria-hidden="true" size={16} />
              <span>Lease</span>
            </button>
          </div>
          {isCreating ? <LeaseForm properties={properties} onSubmit={onCreateLease} onCreated={() => setIsCreating(false)} /> : null}
          <DataTable columns={columns} rows={leases} getRowKey={(row) => row.id} emptyMessage="No leases found." />
        </article>

        <article className="workspace-panel lease-record-panel">
          {selectedLease ? (
            <LeaseRecord
              lease={selectedLease}
              onCreateNotice={onCreateNotice}
              onRequestRenewal={onRequestRenewal}
              onRenewLease={onRenewLease}
              onTerminateLease={onTerminateLease}
            />
          ) : (
            <div className="empty-state compact">
              <FileText aria-hidden="true" size={28} />
              <h2>Select a lease</h2>
              <p>Lifecycle details, renewal workflow, notices, and immutable timeline will appear here.</p>
            </div>
          )}
        </article>
      </section>
    </PageContainer>
  );
}

function LeaseForm({
  onCreated,
  properties,
  onSubmit
}: {
  onCreated: () => void;
  properties: PropertySummaryResponse[];
  onSubmit: (request: CreateLeaseFormValue) => Promise<void>;
}) {
  const [formState, setFormState] = useState({
    propertyAddress: "",
    unit: "",
    tenantFirstName: "",
    tenantLastName: "",
    tenantEmail: "",
    tenantPhone: "",
    leaseStartDate: "",
    leaseEndDate: "",
    rent: "",
    notes: ""
  });
  const formIsComplete =
    formState.propertyAddress.trim() !== "" &&
    formState.tenantFirstName.trim() !== "" &&
    formState.tenantLastName.trim() !== "" &&
    formState.leaseStartDate.trim() !== "" &&
    formState.leaseEndDate.trim() !== "" &&
    formState.rent.trim() !== "";

  async function submitLease(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const tenantNames = [formState.tenantFirstName.trim(), formState.tenantLastName.trim()].filter(Boolean).join(" ");
    await onSubmit({
      name: derivedLeaseName(formState.propertyAddress, formState.unit.trim(), tenantNames, formState.leaseStartDate, formState.leaseEndDate),
      propertyAddress: formState.propertyAddress,
      unitId: null,
      tenantNames,
      tenantEmail: optionalString(formState.tenantEmail),
      tenantPhone: optionalString(formState.tenantPhone),
      leaseStartDate: formState.leaseStartDate,
      leaseEndDate: formState.leaseEndDate,
      rentCents: currencyToCents(formState.rent),
      notes: optionalString(formState.notes)
    });
    setFormState({
      propertyAddress: "",
      unit: "",
      tenantFirstName: "",
      tenantLastName: "",
      tenantEmail: "",
      tenantPhone: "",
      leaseStartDate: "",
      leaseEndDate: "",
      rent: "",
      notes: ""
    });
    onCreated();
  }

  return (
    <form className="lease-form-grid" onSubmit={submitLease}>
      <label className="field">
        <span>Property address</span>
        <select
          name="propertyAddress"
          required
          value={formState.propertyAddress}
          onChange={(event) => setFormState((current) => ({ ...current, propertyAddress: event.target.value }))}
        >
          <option value="">Select property</option>
          {properties.map((property) => (
            <option key={property.id} value={property.address}>
              {property.address}
            </option>
          ))}
        </select>
      </label>
      <Field
        label="Unit"
        name="unit"
        value={formState.unit}
        onChange={(value) => setFormState((current) => ({ ...current, unit: value }))}
      />
      <Field
        label="Tenant's first name"
        name="tenantFirstName"
        required
        value={formState.tenantFirstName}
        onChange={(value) => setFormState((current) => ({ ...current, tenantFirstName: value }))}
      />
      <Field
        label="Tenant's last name"
        name="tenantLastName"
        required
        value={formState.tenantLastName}
        onChange={(value) => setFormState((current) => ({ ...current, tenantLastName: value }))}
      />
      <Field
        label="Email"
        name="tenantEmail"
        type="email"
        value={formState.tenantEmail}
        onChange={(value) => setFormState((current) => ({ ...current, tenantEmail: value }))}
      />
      <Field
        label="Phone"
        name="tenantPhone"
        value={formState.tenantPhone}
        onChange={(value) => setFormState((current) => ({ ...current, tenantPhone: value }))}
      />
      <Field
        label="Start date"
        name="leaseStartDate"
        type="date"
        required
        value={formState.leaseStartDate}
        onChange={(value) => setFormState((current) => ({ ...current, leaseStartDate: value }))}
      />
      <Field
        label="End date"
        name="leaseEndDate"
        type="date"
        required
        value={formState.leaseEndDate}
        onChange={(value) => setFormState((current) => ({ ...current, leaseEndDate: value }))}
      />
      <Field
        label="Rent amount ($)"
        name="rent"
        type="number"
        step="5"
        required
        value={formState.rent}
        onChange={(value) => setFormState((current) => ({ ...current, rent: value }))}
      />
      <label className="field lease-form-notes">
        <span>Notes</span>
        <textarea
          name="notes"
          rows={3}
          value={formState.notes}
          onChange={(event) => setFormState((current) => ({ ...current, notes: event.target.value }))}
        />
      </label>
      <button className="primary-action lease-form-submit" type="submit" disabled={!formIsComplete}>
        <span>Create lease</span>
        <ArrowRight aria-hidden="true" size={18} />
      </button>
    </form>
  );
}

function LeaseRecord({
  lease,
  onCreateNotice,
  onRequestRenewal,
  onRenewLease,
  onTerminateLease
}: {
  lease: LeaseResponse;
  onCreateNotice: (request: CreateNoticeRequest) => Promise<void>;
  onRequestRenewal: (leaseId: string) => Promise<void>;
  onRenewLease: (leaseId: string, request: RenewLeaseRequest) => Promise<void>;
  onTerminateLease: (leaseId: string, reason?: string | null) => Promise<void>;
}) {
  const [activeTab, setActiveTab] = useState<LeaseTab>("overview");
  const locationText = displayLeaseLocation(lease.unitLabel, lease.name, lease.propertyAddress);

  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Lease record</p>
          <h2>{locationText}</h2>
          <p className="muted">{lease.propertyAddress}</p>
        </div>
        <StatusBadge value={lease.status ?? "ACTIVE"} />
      </div>

      <div className="record-actions">
        <button className="secondary-action compact" type="button" onClick={() => onRequestRenewal(lease.id)}>
          <CalendarClock aria-hidden="true" size={16} />
          <span>Renewal</span>
        </button>
        <button className="secondary-action compact" type="button" onClick={() => onTerminateLease(lease.id, "Terminated from workspace")}>
          <Send aria-hidden="true" size={16} />
          <span>Terminate</span>
        </button>
      </div>

      <div className="tabs" role="tablist" aria-label="Lease record sections">
        {(["overview", "timeline", "documents", "notices"] as LeaseTab[]).map((tab) => (
          <button className={activeTab === tab ? "active" : ""} key={tab} type="button" onClick={() => setActiveTab(tab)}>
            {labelFor(tab)}
          </button>
        ))}
      </div>

      {activeTab === "overview" ? (
        <section className="lease-overview-grid">
          <KpiCard icon={Users} label="Tenants" value={String(splitTenants(lease.tenantNames).length)} trend={splitTenants(lease.tenantNames).join(", ")} />
          <KpiCard icon={CalendarClock} label="Lease end" value={formatDateOnly(lease.leaseEndDate)} trend={daysUntilText(lease.leaseEndDate)} tone={lease.status === "EXPIRING_SOON" ? "warning" : "neutral"} />
          <KpiCard icon={FileText} label="Rent" value={formatCents(lease.rentCents ?? 0)} trend="Monthly contract rent" />
          <div className="summary-block">
            <h3>Renewal workflow</h3>
            <p>Renewal deadlines are tracked through linked notices.</p>
            <RenewalForm lease={lease} onRenewLease={onRenewLease} />
          </div>
        </section>
      ) : null}

      {activeTab === "timeline" ? (
        <Timeline
          items={(lease.timeline ?? []).map((event) => ({
            id: event.id,
            title: labelFor(event.eventType),
            description: event.details,
            timestamp: formatDateTime(event.createdAt),
            actor: event.actorReference
          }))}
          emptyMessage="No lease events yet."
        />
      ) : null}

      {activeTab === "documents" ? (
        <div className="summary-block">
          <h3>Documents</h3>
          <p>Lease documents will connect to evidence vault versioned storage in Phase 6.</p>
        </div>
      ) : null}

      {activeTab === "notices" ? (
        <LeaseNotices lease={lease} notices={lease.notices ?? []} onCreateNotice={onCreateNotice} />
      ) : null}
    </>
  );
}

function RenewalForm({ lease, onRenewLease }: { lease: LeaseResponse; onRenewLease: (leaseId: string, request: RenewLeaseRequest) => Promise<void> }) {
  async function submitRenewal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await onRenewLease(lease.id, {
      nextStartDate: String(form.get("nextStartDate") ?? ""),
      nextEndDate: String(form.get("nextEndDate") ?? ""),
      rentCents: currencyToCents(form.get("renewalRent")),
      notes: optionalString(form.get("renewalNotes"))
    });
    event.currentTarget.reset();
  }

  return (
    <form className="renewal-form-grid" onSubmit={submitRenewal}>
      <Field label="Next start" name="nextStartDate" type="date" required />
      <Field label="Next end" name="nextEndDate" type="date" required />
      <Field label="Rent" name="renewalRent" type="number" step="0.01" required />
      <Field label="Notes" name="renewalNotes" />
      <button className="primary-action" type="submit">Complete renewal</button>
    </form>
  );
}

function LeaseNotices({
  lease,
  notices,
  onCreateNotice
}: {
  lease: LeaseResponse;
  notices: NoticeSummaryResponse[];
  onCreateNotice: (request: CreateNoticeRequest) => Promise<void>;
}) {
  async function submitNotice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await onCreateNotice({
      recipientName: String(form.get("recipientName") ?? ""),
      recipientContactInfo: String(form.get("recipientContactInfo") ?? ""),
      noticeType: String(form.get("noticeType") ?? "RENEWAL_REFUSAL") as NoticeType,
      deliveryMethod: "REGISTERED_MAIL",
      leaseId: lease.id,
      deadlineAt: null,
      notes: optionalString(form.get("notes"))
    });
    event.currentTarget.reset();
  }

  return (
    <section className="lease-notices-grid">
      <form className="compact-form" onSubmit={submitNotice}>
        <Field label="Recipient" name="recipientName" defaultValue={splitTenants(lease.tenantNames)[0] ?? ""} required />
        <Field label="Contact" name="recipientContactInfo" defaultValue={lease.tenantEmail ?? lease.tenantPhone ?? ""} required />
        <label className="field">
          <span>Notice type</span>
          <select name="noticeType" defaultValue="RENEWAL_REFUSAL">
            {NOTICE_TYPES.map((type) => (
              <option key={type} value={type}>{labelFor(type)}</option>
            ))}
          </select>
        </label>
        <Field label="Notes" name="notes" />
        <button className="primary-action" type="submit">Create notice</button>
      </form>
      <div className="notice-list">
        {notices.length === 0 ? <p className="muted">No notices linked to this lease.</p> : null}
        {notices.map((notice) => (
          <div className="notice-row" key={notice.id}>
            <span>
              <strong>{notice.recipientName}</strong>
              <small>{labelFor(notice.noticeType)}</small>
            </span>
            <StatusBadge value={notice.status} />
          </div>
        ))}
      </div>
    </section>
  );
}

function Field({
  defaultValue,
  label,
  name,
  onChange,
  required,
  step,
  type = "text",
  value
}: {
  defaultValue?: string;
  label: string;
  name: string;
  onChange?: (value: string) => void;
  required?: boolean;
  step?: string;
  type?: string;
  value?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        defaultValue={defaultValue}
        name={name}
        required={required}
        step={step}
        type={type}
        value={value}
        onChange={onChange ? (event) => onChange(event.target.value) : undefined}
      />
    </label>
  );
}

const LEASE_STATUSES: LeaseStatus[] = ["ACTIVE", "EXPIRING_SOON", "PENDING_RENEWAL", "EXPIRED", "TERMINATED"];
const NOTICE_TYPES: NoticeType[] = ["RENEWAL_REFUSAL", "RENT_INCREASE", "NON_PAYMENT", "TERMINATION", "LEASE_MODIFICATION", "OTHER"];

function leaseTableColumns(onSelectLease: (leaseId: string) => Promise<void>): DataTableColumn<LeaseSummaryResponse>[] {
  return [
    {
      key: "unit",
      header: "Property / Unit",
      render: (row) => (
        <button className="table-link" type="button" onClick={() => onSelectLease(row.id)}>
          {displayLeaseLocation(row.unitLabel, row.name, row.propertyAddress)}
        </button>
      )
    },
    { key: "tenant", header: "Tenant", render: (row) => firstLine(row.tenantNames) },
    { key: "start", header: "Start date", render: (row) => formatDateOnly(row.leaseStartDate) },
    { key: "end", header: "End date", render: (row) => formatDateOnly(row.leaseEndDate) },
    { key: "rent", header: "Rent", render: (row) => formatCents(row.rentCents ?? 0), align: "right" },
    { key: "status", header: "Status", render: (row) => <StatusBadge value={row.status ?? "ACTIVE"} /> }
  ];
}

function optionalString(value: string | FormDataEntryValue | null) {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function currencyToCents(value: string | FormDataEntryValue | null) {
  const text = optionalString(value);
  return text ? Math.round(Number(text) * 100) : 0;
}

function formatCents(value?: number | null) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "CAD" }).format((value ?? 0) / 100);
}

function displayUnit(unitLabel: string | null | undefined, leaseName: string, propertyAddress: string) {
  if (unitLabel?.trim()) {
    return `Unit ${unitLabel.trim()}`;
  }
  const extracted = extractUnitFromLeaseName(leaseName, propertyAddress);
  return extracted || null;
}

function displayLeaseLocation(unitLabel: string | null | undefined, leaseName: string, propertyAddress: string) {
  const address = propertyAddress.trim();
  const unit = displayUnit(unitLabel, leaseName, propertyAddress);
  return [address, unit].filter(Boolean).join(" · ") || "Unassigned property";
}

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(new Date(`${value}T00:00:00`));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(value));
}

function splitTenants(value: string) {
  return value.split(/\n|,|;/).map((part) => part.trim()).filter(Boolean);
}

function firstLine(value: string) {
  return splitTenants(value)[0] ?? value;
}

function derivedLeaseName(
  propertyAddress: string,
  unit: string,
  tenantNames: string,
  leaseStartDate: string,
  leaseEndDate: string
) {
  const tenant = firstLine(tenantNames);
  const period = [leaseStartDate, leaseEndDate].filter(Boolean).join(" to ");
  const location = [unit, propertyAddress].filter(Boolean).join(", ");
  return [tenant, location, period].filter(Boolean).join(" - ") || "Lease";
}

function extractUnitFromLeaseName(leaseName: string, propertyAddress: string) {
  const parts = leaseName.split(" - ");
  if (parts.length < 2) {
    return null;
  }
  const location = parts[1]?.trim() ?? "";
  if (!location) {
    return null;
  }
  if (location === propertyAddress) {
    return null;
  }
  const propertySuffix = `, ${propertyAddress}`;
  if (location.endsWith(propertySuffix)) {
    return location.slice(0, -propertySuffix.length).trim() || null;
  }
  return null;
}

function daysUntilText(value: string) {
  const end = new Date(`${value}T00:00:00`).getTime();
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = Math.ceil((end - today.getTime()) / 86_400_000);
  if (days < 0) {
    return `${Math.abs(days)} days past end date`;
  }
  return `${days} days remaining`;
}

function labelFor(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}
