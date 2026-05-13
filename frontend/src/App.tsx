import {
  ArrowRight,
  BadgeCheck,
  BellRing,
  Building2,
  Download,
  FileCheck2,
  FileText,
  LogOut,
  Mail,
  PackageCheck,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Upload,
  X,
  UserPlus
} from "lucide-react";
import { ChangeEvent, Dispatch, FormEvent, SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import {
  ApiError,
  AuditEventResponse,
  DeliveryAttemptResponse,
  DeliveryAttemptStatus,
  DeliveryMethod,
  EvidenceDocumentResponse,
  EvidenceDocumentType,
  EvidencePackageResponse,
  LeaseResponse,
  LeaseSummaryResponse,
  NoticeResponse,
  NoticeSummaryResponse,
  NoticeType
} from "./api/client";
import { useAuth } from "./auth/AuthContext";

interface TenantNameInput {
  firstName: string;
  lastName: string;
}

export default function App() {
  const auth = useAuth();
  const route = useRoute();

  if (auth.isAuthenticated) {
    return <Workspace />;
  }

  return route === "/register" ? <RegisterScreen /> : <AuthScreen />;
}

function AuthScreen() {
  return (
    <main className="auth-page">
      <AuthCopy />

      <section className="auth-panel" aria-label="Log in">
        <LoginForm />
      </section>
    </main>
  );
}

function RegisterScreen() {
  return (
    <main className="auth-page">
      <AuthCopy />
      <section className="auth-panel register-panel" aria-label="Create account">
        <RegisterForm />
      </section>
    </main>
  );
}

function AuthCopy() {
  return (
    <section className="auth-copy" aria-labelledby="auth-heading">
      <div className="brand-mark">
        <FileCheck2 aria-hidden="true" size={24} />
      </div>
      <p className="eyebrow">Lease Track</p>
      <h1 id="auth-heading">Notice compliance, ready for the workday.</h1>
      <p className="lede">
        Track notices, evidence, delivery status, and audit history from one focused workspace.
      </p>
      <div className="proof-list" aria-label="Platform capabilities">
        <span>
          <ShieldCheck aria-hidden="true" size={18} />
          Role-aware access
        </span>
        <span>
          <BadgeCheck aria-hidden="true" size={18} />
          Evidence packages
        </span>
        <span>
          <BellRing aria-hidden="true" size={18} />
          Delivery tracking
        </span>
      </div>
    </section>
  );
}

function LoginForm() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useStatus();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus({ kind: "loading", message: "Signing in..." });
    try {
      await login({ email, password });
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <FormHeader
        icon={<Mail aria-hidden="true" size={20} />}
        title="Welcome back"
        description="Use the credentials for your Lease Track account."
      />
      <Field label="Email" htmlFor="login-email">
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </Field>
      <Field label="Password" htmlFor="login-password">
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </Field>
      <StatusMessage status={status} />
      <button className="primary-action" type="submit" disabled={status.kind === "loading"}>
        <span>Log in</span>
        <ArrowRight aria-hidden="true" size={18} />
      </button>
      <p className="auth-switch">
        Need an account? <a href="/register">Create one</a>
      </p>
    </form>
  );
}

function RegisterForm() {
  const { register } = useAuth();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useStatus();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStatus({ kind: "loading", message: "Creating account..." });
    try {
      await register({ displayName, email, password, role: "LANDLORD" });
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <FormHeader
        icon={<UserPlus aria-hidden="true" size={20} />}
        title="Create your account"
        description="For people managing lease notices, delivery evidence, and compliance records."
      />
      <Field label="Display name" htmlFor="register-name">
        <input
          id="register-name"
          type="text"
          autoComplete="name"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          required
        />
      </Field>
      <Field label="Email" htmlFor="register-email">
        <input
          id="register-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </Field>
      <Field label="Password" htmlFor="register-password" hint="8 characters minimum">
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          minLength={8}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </Field>
      <StatusMessage status={status} />
      <button className="primary-action" type="submit" disabled={status.kind === "loading"}>
        <span>Create account</span>
        <ArrowRight aria-hidden="true" size={18} />
      </button>
      <p className="auth-switch">
        Already have an account? <a href="/">Log in</a>
      </p>
    </form>
  );
}

function Workspace() {
  const { api, user, logout } = useAuth();
  const initials = useMemo(() => initialsFor(user?.displayName ?? user?.email ?? "Lease Track"), [user]);
  const [leases, setLeases] = useState<LeaseSummaryResponse[]>([]);
  const [selectedLease, setSelectedLease] = useState<LeaseResponse | null>(null);
  const [tenantNameInputs, setTenantNameInputs] = useState<TenantNameInput[]>([{ firstName: "", lastName: "" }]);
  const [notices, setNotices] = useState<NoticeSummaryResponse[]>([]);
  const [selectedNotice, setSelectedNotice] = useState<NoticeResponse | null>(null);
  const [documents, setDocuments] = useState<EvidenceDocumentResponse[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEventResponse[]>([]);
  const [evidencePackage, setEvidencePackage] = useState<EvidencePackageResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [status, setStatus] = useStatus();

  const selectedAttempt = selectedNotice?.deliveryAttempts[0] ?? null;

  const refreshLeases = useCallback(async () => {
    const page = await api.listLeases({ size: 50 });
    setLeases(page.content);
    return page.content;
  }, [api]);

  const refreshNotices = useCallback(async () => {
    const page = await api.listNotices({ leaseId: selectedLease?.id, size: 50 });
    setNotices(page.content);
    return page.content;
  }, [api, selectedLease?.id]);

  const loadNotice = useCallback(
    async (noticeId: string) => {
      setStatus({ kind: "loading", message: "Loading notice..." });
      const notice = await api.getNotice(noticeId);
      setSelectedNotice(notice);
      setEvidencePackage(null);
      const attempt = notice.deliveryAttempts[0];
      const [nextDocuments, nextAuditEvents] = await Promise.all([
        attempt ? api.listEvidenceDocuments(notice.id, attempt.id) : Promise.resolve([]),
        api.getAuditLog(notice.id)
      ]);
      setDocuments(nextDocuments);
      setAuditEvents(nextAuditEvents);
      setStatus({ kind: "idle", message: "" });
    },
    [api, setStatus]
  );

  const loadLease = useCallback(
    async (leaseId: string) => {
      setStatus({ kind: "loading", message: "Loading lease..." });
      const lease = await api.getLease(leaseId);
      setSelectedLease(lease);
      setNotices(lease.notices);
      setSelectedNotice(null);
      setDocuments([]);
      setAuditEvents([]);
      setEvidencePackage(null);
      setStatus({ kind: "idle", message: "" });
      if (lease.notices[0]) {
        await loadNotice(lease.notices[0].id);
      }
      return lease;
    },
    [api, loadNotice, setStatus]
  );

  const refreshWorkspace = useCallback(async () => {
    setIsLoading(true);
    try {
      const nextLeases = await refreshLeases();
      if (selectedLease) {
        await loadLease(selectedLease.id);
      } else if (nextLeases[0]) {
        await loadLease(nextLeases[0].id);
      }
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    } finally {
      setIsLoading(false);
    }
  }, [loadLease, refreshLeases, selectedLease, setStatus]);

  useEffect(() => {
    refreshWorkspace();
    // Run once on authenticated mount; subsequent refreshes are user-driven.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onCreateLease(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const tenantNames = tenantNameInputs
      .map((tenant) => `${tenant.firstName.trim()} ${tenant.lastName.trim()}`.trim())
      .filter(Boolean);
    if (tenantNames.length === 0) {
      setStatus({ kind: "error", message: "Add at least one tenant name." });
      return;
    }
    setStatus({ kind: "loading", message: "Creating lease..." });
    try {
      const lease = await api.createLease({
        name: String(form.get("leaseName") ?? ""),
        propertyAddress: String(form.get("propertyAddress") ?? ""),
        tenantNames: tenantNames.join("\n"),
        tenantEmail: optionalString(form.get("tenantEmail")),
        tenantPhone: optionalString(form.get("tenantPhone")),
        leaseStartDate: String(form.get("leaseStartDate") ?? ""),
        leaseEndDate: String(form.get("leaseEndDate") ?? ""),
        notes: optionalString(form.get("leaseNotes"))
      });
      formElement.reset();
      setTenantNameInputs([{ firstName: "", lastName: "" }]);
      await refreshLeases();
      await loadLease(lease.id);
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onCreateNotice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedLease) {
      setStatus({ kind: "error", message: "Select or create a lease before adding a notice." });
      return;
    }
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    setStatus({ kind: "loading", message: "Creating notice..." });
    try {
      const deadlineValue = String(form.get("deadlineAt") ?? "");
      const notice = await api.createNotice({
        recipientName: String(form.get("recipientName") ?? ""),
        recipientContactInfo: String(form.get("recipientContactInfo") ?? ""),
        noticeType: String(form.get("noticeType") ?? "RENT_INCREASE") as NoticeType,
        deliveryMethod: String(form.get("deliveryMethod") ?? "REGISTERED_MAIL") as DeliveryMethod,
        leaseId: selectedLease.id,
        deadlineAt: deadlineValue ? new Date(deadlineValue).toISOString() : null,
        notes: String(form.get("notes") ?? "")
      });
      formElement.reset();
      await loadLease(selectedLease.id);
      await loadNotice(notice.id);
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onUpdateStatus(nextStatus: DeliveryAttemptStatus) {
    if (!selectedNotice || !selectedAttempt) {
      return;
    }
    setStatus({ kind: "loading", message: "Updating delivery status..." });
    try {
      const notice = await api.updateDeliveryAttemptStatus(selectedNotice.id, selectedAttempt.id, {
        status: nextStatus
      });
      setSelectedNotice(notice);
      await refreshNotices();
      if (selectedLease) {
        await loadLease(selectedLease.id);
      }
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onSaveEvidence(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedNotice || !selectedAttempt) {
      return;
    }
    const form = new FormData(event.currentTarget);
    setStatus({ kind: "loading", message: "Saving evidence..." });
    try {
      await api.upsertDeliveryEvidence(selectedNotice.id, selectedAttempt.id, {
        carrierName: optionalString(form.get("carrierName")),
        trackingNumber: optionalString(form.get("trackingNumber")),
        trackingUrl: optionalString(form.get("trackingUrl")),
        carrierReceiptRef: optionalString(form.get("carrierReceiptRef")),
        deliveryConfirmation: form.get("deliveryConfirmation") === "on",
        deliveryConfirmationMetadata: optionalString(form.get("deliveryConfirmationMetadata"))
      });
      setAuditEvents(await api.getAuditLog(selectedNotice.id));
      setEvidencePackage(null);
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onUploadDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedNotice || !selectedAttempt) {
      return;
    }
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const file = form.get("file");
    if (!(file instanceof File) || !file.name) {
      setStatus({ kind: "error", message: "Choose a document to upload." });
      return;
    }
    setStatus({ kind: "loading", message: "Uploading document..." });
    try {
      const documentType = String(form.get("documentType") ?? "OTHER") as EvidenceDocumentType;
      await api.uploadEvidenceDocument(selectedNotice.id, selectedAttempt.id, documentType, file);
      setDocuments(await api.listEvidenceDocuments(selectedNotice.id, selectedAttempt.id));
      setAuditEvents(await api.getAuditLog(selectedNotice.id));
      setEvidencePackage(null);
      formElement.reset();
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onGeneratePackage() {
    if (!selectedNotice) {
      return;
    }
    setStatus({ kind: "loading", message: "Generating package..." });
    try {
      const nextPackage = await api.getEvidencePackage(selectedNotice.id);
      setEvidencePackage(nextPackage);
      setAuditEvents(nextPackage.auditEvents);
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  async function onDownloadPdf() {
    if (!selectedNotice) {
      return;
    }
    setStatus({ kind: "loading", message: "Preparing PDF..." });
    try {
      const pdf = await api.downloadEvidencePackagePdf(selectedNotice.id);
      downloadBlob(pdf, `lease-track-evidence-${selectedNotice.id}.pdf`);
      setStatus({ kind: "idle", message: "" });
    } catch (error) {
      setStatus({ kind: "error", message: errorMessage(error) });
    }
  }

  return (
    <main className="workspace">
      <aside className="sidebar" aria-label="Workspace navigation">
        <div className="sidebar-brand">
          <div className="brand-mark small">
            <FileCheck2 aria-hidden="true" size={20} />
          </div>
          <div>
            <strong>Lease Track</strong>
          </div>
        </div>
        <nav>
          <a className="nav-item active" href="#overview">
            Overview
          </a>
          <a className="nav-item" href="#leases">
            Leases
          </a>
          <a className="nav-item" href="#notices">
            Notices
          </a>
        </nav>
      </aside>

      <section className="workspace-main">
        <header className="topbar">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1>Lease operations</h1>
          </div>
          <div className="account-chip">
            <span aria-hidden="true">{initials}</span>
            <div>
              <strong>{user?.displayName ?? "Signed in"}</strong>
              <small>{user?.email ?? "Authenticated session"}</small>
            </div>
            <button type="button" className="icon-button" onClick={logout} aria-label="Log out">
              <LogOut aria-hidden="true" size={18} />
            </button>
          </div>
        </header>

        <section className="status-grid" aria-label="Workspace status">
          <Metric label="Leases" value={String(leases.length)} />
          <Metric label="Open notices" value={String(notices.filter((notice) => notice.status === "OPEN").length)} />
          <Metric label="Evidence" value={evidencePackage?.strongestEvidenceStrength ?? "Pending"} />
        </section>

        <StatusMessage status={status} />

        <section className="operations-grid" id="overview">
          <div className="workspace-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Create</p>
                <h2>New lease</h2>
              </div>
            </div>
            <form className="compact-form" onSubmit={onCreateLease}>
              <Field label="Lease name" htmlFor="leaseName">
                <input id="leaseName" name="leaseName" placeholder="Apt 4B - Tremblay" required />
              </Field>
              <Field label="Property address" htmlFor="propertyAddress">
                <input id="propertyAddress" name="propertyAddress" required />
              </Field>
              <TenantNameFields tenantNames={tenantNameInputs} onChange={setTenantNameInputs} />
              <Field label="Tenant email" htmlFor="tenantEmail">
                <input id="tenantEmail" name="tenantEmail" type="email" />
              </Field>
              <Field label="Tenant phone" htmlFor="tenantPhone">
                <input id="tenantPhone" name="tenantPhone" type="tel" />
              </Field>
              <Field label="Start date" htmlFor="leaseStartDate">
                <input id="leaseStartDate" name="leaseStartDate" type="date" required />
              </Field>
              <Field label="End date" htmlFor="leaseEndDate">
                <input id="leaseEndDate" name="leaseEndDate" type="date" required />
              </Field>
              <Field label="Notes" htmlFor="leaseNotes">
                <textarea id="leaseNotes" name="leaseNotes" rows={3} />
              </Field>
              <button className="primary-action" type="submit">
                <span>Create lease</span>
                <ArrowRight aria-hidden="true" size={18} />
              </button>
            </form>
          </div>

          <div className="workspace-panel" id="leases">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Portfolio</p>
                <h2>Leases</h2>
              </div>
              <button className="icon-button" type="button" onClick={refreshWorkspace} aria-label="Refresh leases">
                <RefreshCw aria-hidden="true" size={18} />
              </button>
            </div>
            <div className="notice-list" aria-label="Notice list">
              {isLoading ? <p className="muted">Loading leases...</p> : null}
              {!isLoading && leases.length === 0 ? <p className="muted">No leases yet.</p> : null}
              {leases.map((lease) => (
                <button
                  className={`notice-row ${selectedLease?.id === lease.id ? "active" : ""}`}
                  key={lease.id}
                  type="button"
                  onClick={() => loadLease(lease.id).catch((error) => setStatus({ kind: "error", message: errorMessage(error) }))}
                >
                  <span>
                    <strong>{lease.name}</strong>
                    <small>{lease.tenantNames}</small>
                    <small>{formatDateOnly(lease.leaseStartDate)} - {formatDateOnly(lease.leaseEndDate)}</small>
                  </span>
                  <StatusPill value={`${lease.openNoticeCount} open`} />
                </button>
              ))}
            </div>
          </div>

          <section className="workspace-panel detail-panel" id="notices" aria-label="Lease notices">
            {selectedLease ? (
              <LeaseNoticeWorkspace
                auditEvents={auditEvents}
                documents={documents}
                evidencePackage={evidencePackage}
                isLoading={isLoading}
                lease={selectedLease}
                notices={notices}
                selectedAttempt={selectedAttempt}
                selectedNotice={selectedNotice}
                onCreateNotice={onCreateNotice}
                onDownloadPdf={onDownloadPdf}
                onGeneratePackage={onGeneratePackage}
                onLoadNotice={loadNotice}
                onSaveEvidence={onSaveEvidence}
                onUpdateStatus={onUpdateStatus}
                onUploadDocument={onUploadDocument}
                setStatus={setStatus}
              />
            ) : (
              <div className="empty-state">
                <Building2 aria-hidden="true" size={28} />
                <h2>Select a lease</h2>
                <p>Lease notices and evidence will appear here.</p>
              </div>
            )}
          </section>
        </section>
      </section>
    </main>
  );
}

function LeaseNoticeWorkspace({
  auditEvents,
  documents,
  evidencePackage,
  lease,
  notices,
  selectedAttempt,
  selectedNotice,
  onCreateNotice,
  onDownloadPdf,
  onGeneratePackage,
  onLoadNotice,
  onSaveEvidence,
  onUpdateStatus,
  onUploadDocument,
  setStatus
}: {
  auditEvents: AuditEventResponse[];
  documents: EvidenceDocumentResponse[];
  evidencePackage: EvidencePackageResponse | null;
  isLoading: boolean;
  lease: LeaseResponse;
  notices: NoticeSummaryResponse[];
  selectedAttempt: DeliveryAttemptResponse | null;
  selectedNotice: NoticeResponse | null;
  onCreateNotice: (event: FormEvent<HTMLFormElement>) => void;
  onDownloadPdf: () => void;
  onGeneratePackage: () => void;
  onLoadNotice: (noticeId: string) => Promise<void>;
  onSaveEvidence: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateStatus: (status: DeliveryAttemptStatus) => void;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => void;
  setStatus: Dispatch<SetStateAction<Status>>;
}) {
  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Lease</p>
          <h2>{lease.name}</h2>
          <p className="muted">{lease.propertyAddress}</p>
        </div>
      </div>

      <div className="detail-meta lease-meta">
        <Metric label="Tenant(s)" value={lease.tenantNames} />
        <Metric label="Period" value={`${formatDateOnly(lease.leaseStartDate)} - ${formatDateOnly(lease.leaseEndDate)}`} />
        <Metric label="Notices" value={String(notices.length)} />
      </div>

      <div className="lease-notice-grid">
        <section>
          <h3>New notice</h3>
          <form className="compact-form" onSubmit={onCreateNotice}>
            <Field label="Recipient" htmlFor="recipientName">
              <input id="recipientName" name="recipientName" defaultValue={firstTenantName(lease.tenantNames)} required />
            </Field>
            <Field label="Contact" htmlFor="recipientContactInfo">
              <input id="recipientContactInfo" name="recipientContactInfo" defaultValue={lease.tenantEmail ?? lease.tenantPhone ?? ""} required />
            </Field>
            <Field label="Notice type" htmlFor="noticeType">
              <select id="noticeType" name="noticeType" defaultValue="RENT_INCREASE">
                {NOTICE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {labelFor(type)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Delivery method" htmlFor="deliveryMethod">
              <select id="deliveryMethod" name="deliveryMethod" defaultValue="REGISTERED_MAIL">
                {DELIVERY_METHODS.map((method) => (
                  <option key={method} value={method}>
                    {labelFor(method)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Deadline" htmlFor="deadlineAt">
              <input id="deadlineAt" name="deadlineAt" type="datetime-local" />
            </Field>
            <Field label="Notes" htmlFor="notes">
              <textarea id="notes" name="notes" rows={3} />
            </Field>
            <button className="primary-action" type="submit">
              <span>Create notice</span>
              <ArrowRight aria-hidden="true" size={18} />
            </button>
          </form>
        </section>

        <section>
          <h3>Lease notices</h3>
          <div className="notice-list">
            {notices.length === 0 ? <p className="muted">No notices for this lease yet.</p> : null}
            {notices.map((notice) => (
              <button
                className={`notice-row ${selectedNotice?.id === notice.id ? "active" : ""}`}
                key={notice.id}
                type="button"
                onClick={() => onLoadNotice(notice.id).catch((error) => setStatus({ kind: "error", message: errorMessage(error) }))}
              >
                <span>
                  <strong>{notice.recipientName}</strong>
                  <small>{labelFor(notice.noticeType)}</small>
                </span>
                <StatusPill value={notice.status} />
              </button>
            ))}
          </div>
        </section>
      </div>

      <div className="notice-detail-shell">
        {selectedNotice && selectedAttempt ? (
          <NoticeDetail
            auditEvents={auditEvents}
            documents={documents}
            evidencePackage={evidencePackage}
            notice={selectedNotice}
            attempt={selectedAttempt}
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
            <p>Notice evidence and audit history will appear here.</p>
          </div>
        )}
      </div>
    </>
  );
}

function TenantNameFields({
  tenantNames,
  onChange
}: {
  tenantNames: TenantNameInput[];
  onChange: Dispatch<SetStateAction<TenantNameInput[]>>;
}) {
  function updateTenantName(index: number, field: keyof TenantNameInput, value: string) {
    onChange((current) =>
      current.map((tenant, currentIndex) =>
        currentIndex === index ? { ...tenant, [field]: value } : tenant
      )
    );
  }

  function addTenantName() {
    onChange((current) => [...current, { firstName: "", lastName: "" }]);
  }

  function removeTenantName(index: number) {
    onChange((current) => {
      const next = current.filter((_, currentIndex) => currentIndex !== index);
      return next.length > 0 ? next : [{ firstName: "", lastName: "" }];
    });
  }

  return (
    <div className="tenant-fields">
      <div className="tenant-fields-heading">
        <span>Tenant name(s)</span>
        <button className="secondary-action compact" type="button" onClick={addTenantName}>
          <Plus aria-hidden="true" size={16} />
          <span>Add</span>
        </button>
      </div>
      <div className="tenant-input-list">
        {tenantNames.map((tenantName, index) => (
          <div className="tenant-input-row" key={index}>
            <input
              aria-label={`Tenant ${index + 1} first name`}
              placeholder="First name"
              value={tenantName.firstName}
              onChange={(event) => updateTenantName(index, "firstName", event.target.value)}
              required={index === 0}
            />
            <input
              aria-label={`Tenant ${index + 1} last name`}
              placeholder="Last name"
              value={tenantName.lastName}
              onChange={(event) => updateTenantName(index, "lastName", event.target.value)}
              required={index === 0}
            />
            <button
              className="icon-button"
              type="button"
              onClick={() => removeTenantName(index)}
              aria-label={`Remove tenant ${index + 1}`}
              disabled={tenantNames.length === 1}
            >
              <X aria-hidden="true" size={16} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function NoticeDetail({
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
  onDownloadPdf: () => void;
  onGeneratePackage: () => void;
  onSaveEvidence: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateStatus: (status: DeliveryAttemptStatus) => void;
  onUploadDocument: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Detail</p>
          <h2>{notice.recipientName}</h2>
          <p className="muted">{notice.recipientContactInfo}</p>
        </div>
        <StatusPill value={notice.status} />
      </div>

      <div className="detail-meta">
        <Metric label="Type" value={labelFor(notice.noticeType)} />
        <Metric label="Delivery" value={labelFor(attempt.deliveryMethod)} />
        <Metric label="Attempt" value={labelFor(attempt.status)} />
      </div>

      <div className="action-strip" aria-label="Delivery status actions">
        {(["SENT", "DELIVERED", "FAILED", "CANCELLED"] as DeliveryAttemptStatus[]).map((status) => (
          <button key={status} className="secondary-action" type="button" onClick={() => onUpdateStatus(status)}>
            <Send aria-hidden="true" size={16} />
            <span>{labelFor(status)}</span>
          </button>
        ))}
      </div>

      <div className="detail-sections">
        <section>
          <h3>Evidence</h3>
          <form className="compact-form two-column" onSubmit={onSaveEvidence}>
            <Field label="Carrier" htmlFor="carrierName">
              <input id="carrierName" name="carrierName" placeholder="Canada Post" />
            </Field>
            <Field label="Tracking number" htmlFor="trackingNumber">
              <input id="trackingNumber" name="trackingNumber" />
            </Field>
            <Field label="Tracking URL" htmlFor="trackingUrl">
              <input id="trackingUrl" name="trackingUrl" type="url" />
            </Field>
            <Field label="Receipt reference" htmlFor="carrierReceiptRef">
              <input id="carrierReceiptRef" name="carrierReceiptRef" />
            </Field>
            <Field label="Confirmation notes" htmlFor="deliveryConfirmationMetadata">
              <textarea id="deliveryConfirmationMetadata" name="deliveryConfirmationMetadata" rows={2} />
            </Field>
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

        <section>
          <h3>Documents</h3>
          <form className="compact-form upload-form" onSubmit={onUploadDocument}>
            <Field label="Document type" htmlFor="documentType">
              <select id="documentType" name="documentType" defaultValue="CARRIER_RECEIPT">
                {DOCUMENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {labelFor(type)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="File" htmlFor="file">
              <input id="file" name="file" type="file" required />
            </Field>
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

        <section>
          <h3>Evidence package</h3>
          <div className="package-actions">
            <button className="secondary-action" type="button" onClick={onGeneratePackage}>
              <PackageCheck aria-hidden="true" size={16} />
              <span>Generate package</span>
            </button>
            <button className="secondary-action" type="button" onClick={onDownloadPdf}>
              <Download aria-hidden="true" size={16} />
              <span>Download PDF</span>
            </button>
          </div>
          {evidencePackage ? (
            <div className="package-summary">
              <Metric label="Strength" value={evidencePackage.strongestEvidenceStrength} />
              <Metric label="Documents" value={String(evidencePackage.evidenceDocuments.length)} />
              <Metric label="Audit events" value={String(evidencePackage.auditEvents.length)} />
            </div>
          ) : null}
        </section>

        <section>
          <h3>Audit log</h3>
          <ol className="audit-list">
            {auditEvents.length === 0 ? <li className="muted">No audit events yet.</li> : null}
            {auditEvents.slice(0, 8).map((event) => (
              <li key={event.id}>
                <strong>{labelFor(event.eventType)}</strong>
                <span>{formatDate(event.createdAt)}</span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </>
  );
}

function FormHeader({
  icon,
  title,
  description
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="form-header">
      <span>{icon}</span>
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
    </div>
  );
}

function Field({
  label,
  htmlFor,
  hint,
  children
}: {
  label: string;
  htmlFor: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="field" htmlFor={htmlFor}>
      <span>
        {label}
        {hint ? <small>{hint}</small> : null}
      </span>
      {children}
    </label>
  );
}

interface Status {
  kind: "idle" | "loading" | "error";
  message: string;
}

function useStatus() {
  return useState<Status>({ kind: "idle", message: "" });
}

function StatusMessage({ status }: { status: Status }) {
  if (status.kind === "idle") {
    return null;
  }
  return (
    <p className={`status-message ${status.kind}`} role={status.kind === "error" ? "alert" : "status"}>
      {status.message}
    </p>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function StatusPill({ value }: { value: string }) {
  return <span className={`status-pill ${value.toLowerCase().replace(/_/g, "-")}`}>{labelFor(value)}</span>;
}

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

function formatDateOnly(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  }).format(new Date(`${value}T00:00:00`));
}

function firstTenantName(value: string) {
  return value
    .split(/\n|,|;/)
    .map((part) => part.trim())
    .find(Boolean) ?? value;
}

function optionalString(value: FormDataEntryValue | null) {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

function initialsFor(value: string) {
  return value
    .split(/\s|@/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    if (
      error instanceof TypeError &&
      /(failed to fetch|networkerror|network request failed)/i.test(error.message)
    ) {
      return "Cannot reach the API at http://localhost:8080. Start the backend (and dependencies) or verify CORS/API base URL settings.";
    }
    return error.message;
  }
  return "Something went wrong.";
}

function useRoute() {
  const [path, setPath] = useState(() => window.location.pathname);

  const syncPath = useCallback(() => {
    setPath(window.location.pathname);
  }, []);

  useEffect(() => {
    function onClick(event: MouseEvent) {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const link = target.closest("a");
      if (!link || link.target || link.origin !== window.location.origin) {
        return;
      }
      if (link.pathname !== "/" && link.pathname !== "/register") {
        return;
      }
      event.preventDefault();
      window.history.pushState({}, "", link.href);
      syncPath();
    }

    window.addEventListener("click", onClick);
    window.addEventListener("popstate", syncPath);
    return () => {
      window.removeEventListener("click", onClick);
      window.removeEventListener("popstate", syncPath);
    };
  }, [syncPath]);

  return path;
}
