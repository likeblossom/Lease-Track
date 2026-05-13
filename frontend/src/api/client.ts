export type UserRole = "LANDLORD" | "PROPERTY_MANAGER" | "TENANT" | "ADMIN";
export type NoticeStatus = "OPEN" | "COMPLETED" | "CANCELLED";
export type NoticeType =
  | "RENEWAL_REFUSAL"
  | "RENT_INCREASE"
  | "NON_PAYMENT"
  | "TERMINATION"
  | "LEASE_MODIFICATION"
  | "OTHER";
export type DeliveryMethod =
  | "REGISTERED_MAIL"
  | "HAND_DELIVERY_SIGNATURE"
  | "EMAIL_ACKNOWLEDGEMENT"
  | "BAILIFF";
export type DeliveryAttemptStatus = "PENDING" | "SENT" | "DELIVERED" | "FAILED" | "CANCELLED";
export type TrackingSyncStatus = "NOT_APPLICABLE" | "PENDING" | "SUCCESS" | "FAILED";
export type EvidenceDocumentType =
  | "CARRIER_RECEIPT"
  | "DELIVERY_CONFIRMATION"
  | "SIGNED_ACKNOWLEDGEMENT"
  | "EMAIL_ACKNOWLEDGEMENT"
  | "BAILIFF_AFFIDAVIT"
  | "OTHER";
export type EvidenceStrength = "STRONG" | "MEDIUM" | "WEAK";
export type ActorRole = "LANDLORD" | "PROPERTY_MANAGER" | "TENANT" | "BAILIFF" | "SYSTEM" | "ADMIN";
export type AuditEventType =
  | "NOTICE_CREATED"
  | "DELIVERY_STATUS_UPDATED"
  | "EVIDENCE_ADDED"
  | "EVIDENCE_UPDATED"
  | "EVIDENCE_DOCUMENT_UPLOADED"
  | "EVIDENCE_PACKAGE_GENERATED"
  | "DEADLINE_APPROACHING_PUBLISHED";

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
  role: UserRole;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CreateNoticeRequest {
  recipientName: string;
  recipientContactInfo: string;
  noticeType: NoticeType;
  deliveryMethod: DeliveryMethod;
  leaseId?: string | null;
  tenantUserId?: string | null;
  deadlineAt?: string | null;
  notes?: string | null;
}

export interface CreateLeaseRequest {
  name: string;
  propertyAddress: string;
  tenantNames: string;
  tenantEmail?: string | null;
  tenantPhone?: string | null;
  leaseStartDate: string;
  leaseEndDate: string;
  notes?: string | null;
}

export interface LeaseSummaryResponse {
  id: string;
  name: string;
  propertyAddress: string;
  tenantNames: string;
  leaseStartDate: string;
  leaseEndDate: string;
  noticeCount: number;
  openNoticeCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface LeaseResponse {
  id: string;
  name: string;
  propertyAddress: string;
  tenantNames: string;
  tenantEmail?: string | null;
  tenantPhone?: string | null;
  leaseStartDate: string;
  leaseEndDate: string;
  ownerUserId: string;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  notices: NoticeSummaryResponse[];
}

export interface NoticeResponse {
  id: string;
  recipientName: string;
  recipientContactInfo: string;
  noticeType: NoticeType;
  status: NoticeStatus;
  ownerUserId: string;
  leaseId?: string | null;
  tenantUserId?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
  closedAt?: string | null;
  deliveryAttempts: DeliveryAttemptResponse[];
}

export interface NoticeSummaryResponse {
  id: string;
  leaseId?: string | null;
  recipientName: string;
  noticeType: NoticeType;
  status: NoticeStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ListNoticesParams {
  status?: NoticeStatus;
  noticeType?: NoticeType;
  deliveryMethod?: DeliveryMethod;
  leaseId?: string;
  deadlineAfter?: string;
  deadlineBefore?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface DeliveryAttemptResponse {
  id: string;
  attemptNumber: number;
  deliveryMethod: DeliveryMethod;
  status: DeliveryAttemptStatus;
  sentAt?: string | null;
  deliveredAt?: string | null;
  deadlineAt?: string | null;
  trackingSyncStatus: TrackingSyncStatus;
  lastTrackingCheckedAt?: string | null;
  deadlineReminderSent: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateDeliveryAttemptStatusRequest {
  status: DeliveryAttemptStatus;
}

export interface UpsertDeliveryEvidenceRequest {
  trackingNumber?: string | null;
  carrierName?: string | null;
  trackingUrl?: string | null;
  carrierReceiptRef?: string | null;
  deliveryConfirmation?: boolean | null;
  deliveryConfirmationMetadata?: string | null;
  signedAcknowledgementRef?: string | null;
  emailAcknowledgementRef?: string | null;
  emailAcknowledgementMetadata?: string | null;
  bailiffAffidavitRef?: string | null;
}

export interface DeliveryEvidenceResponse extends UpsertDeliveryEvidenceRequest {
  id: string;
  deliveryAttemptId: string;
  carrierCode?: string | null;
  latestTrackingStatus?: string | null;
  latestTrackingStatusCode?: string | null;
  latestTrackingEventAt?: string | null;
  latestTrackingProviderError?: string | null;
  evidenceStrength: EvidenceStrength;
  createdAt: string;
  updatedAt: string;
}

export interface EvidenceDocumentResponse {
  id: string;
  noticeId: string;
  deliveryAttemptId: string;
  deliveryEvidenceId?: string | null;
  documentType: EvidenceDocumentType;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  storageProvider: string;
  storageKey: string;
  sha256Checksum: string;
  uploadedByUserId?: string | null;
  createdAt: string;
}

export interface AuditEventResponse {
  id: string;
  noticeId: string;
  deliveryAttemptId?: string | null;
  eventType: AuditEventType;
  actorRole: ActorRole;
  actorReference?: string | null;
  details?: string | null;
  createdAt: string;
}

export interface TrackingEventResponse {
  id: string;
  deliveryAttemptId: string;
  trackingNumber: string;
  eventKey: string;
  status?: string | null;
  statusCode?: string | null;
  delivered: boolean;
  eventAt?: string | null;
  checkedAt: string;
  errorMessage?: string | null;
}

export interface EvidencePackageAttemptResponse {
  attempt: DeliveryAttemptResponse;
  evidence?: DeliveryEvidenceResponse | null;
  evidenceDocuments: EvidenceDocumentResponse[];
  trackingHistory: TrackingEventResponse[];
}

export interface EvidencePackageResponse {
  packageId: string;
  packageVersion: string;
  packageHash: string;
  noticeId: string;
  generatedByUserId: string;
  generatedAt: string;
  notice: NoticeResponse;
  attempts: EvidencePackageAttemptResponse[];
  evidence: DeliveryEvidenceResponse[];
  evidenceDocuments: EvidenceDocumentResponse[];
  trackingHistory: TrackingEventResponse[];
  auditEvents: AuditEventResponse[];
  strongestEvidenceStrength: EvidenceStrength;
}

export class ApiError extends Error {
  readonly status: number;
  readonly details?: ApiErrorResponse;

  constructor(status: number, message: string, details?: ApiErrorResponse) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

export interface ApiClientOptions {
  baseUrl?: string;
  getToken?: () => string | null;
  fetchImpl?: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly getToken: () => string | null;
  private readonly fetchImpl: typeof fetch;

  constructor(options: ApiClientOptions = {}) {
    this.baseUrl = resolveBaseUrl(options.baseUrl);
    this.getToken = options.getToken ?? (() => null);
    this.fetchImpl = options.fetchImpl ?? ((input, init) => window.fetch(input, init));
  }

  login(request: LoginRequest): Promise<LoginResponse> {
    return this.request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  register(request: RegisterRequest): Promise<UserResponse> {
    return this.request("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  getHealth(): Promise<{ status: string; service?: string }> {
    return this.request("/api/health");
  }

  listLeases(params: { page?: number; size?: number; sort?: string } = {}): Promise<PageResponse<LeaseSummaryResponse>> {
    return this.request(`/api/leases${queryString(params)}`);
  }

  createLease(request: CreateLeaseRequest): Promise<LeaseResponse> {
    return this.request("/api/leases", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  getLease(leaseId: string): Promise<LeaseResponse> {
    return this.request(`/api/leases/${encodeURIComponent(leaseId)}`);
  }

  listNotices(params: ListNoticesParams = {}): Promise<PageResponse<NoticeSummaryResponse>> {
    return this.request(`/api/notices${queryString(params)}`);
  }

  createNotice(request: CreateNoticeRequest): Promise<NoticeResponse> {
    return this.request("/api/notices", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  getNotice(noticeId: string): Promise<NoticeResponse> {
    return this.request(`/api/notices/${encodeURIComponent(noticeId)}`);
  }

  updateDeliveryAttemptStatus(
    noticeId: string,
    attemptId: string,
    request: UpdateDeliveryAttemptStatusRequest
  ): Promise<NoticeResponse> {
    return this.request(
      `/api/notices/${encodeURIComponent(noticeId)}/attempts/${encodeURIComponent(attemptId)}/status`,
      {
        method: "PATCH",
        body: JSON.stringify(request)
      }
    );
  }

  upsertDeliveryEvidence(
    noticeId: string,
    attemptId: string,
    request: UpsertDeliveryEvidenceRequest
  ): Promise<DeliveryEvidenceResponse> {
    return this.request(
      `/api/notices/${encodeURIComponent(noticeId)}/attempts/${encodeURIComponent(attemptId)}/evidence`,
      {
        method: "POST",
        body: JSON.stringify(request)
      }
    );
  }

  listEvidenceDocuments(noticeId: string, attemptId: string): Promise<EvidenceDocumentResponse[]> {
    return this.request(
      `/api/notices/${encodeURIComponent(noticeId)}/attempts/${encodeURIComponent(attemptId)}/evidence/documents`
    );
  }

  uploadEvidenceDocument(
    noticeId: string,
    attemptId: string,
    documentType: EvidenceDocumentType,
    file: File | Blob,
    filename?: string
  ): Promise<EvidenceDocumentResponse> {
    const body = new FormData();
    body.set("documentType", documentType);
    body.set("file", file, filename);
    return this.request(
      `/api/notices/${encodeURIComponent(noticeId)}/attempts/${encodeURIComponent(attemptId)}/evidence/documents`,
      {
        method: "POST",
        body
      }
    );
  }

  getAuditLog(noticeId: string): Promise<AuditEventResponse[]> {
    return this.request(`/api/notices/${encodeURIComponent(noticeId)}/audit-log`);
  }

  getEvidencePackage(noticeId: string): Promise<EvidencePackageResponse> {
    return this.request(`/api/notices/${encodeURIComponent(noticeId)}/evidence-package`);
  }

  downloadEvidencePackagePdf(noticeId: string): Promise<Blob> {
    return this.requestBlob(`/api/notices/${encodeURIComponent(noticeId)}/evidence-package.pdf`, {
      headers: {
        Accept: "application/pdf"
      }
    });
  }

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    if (!headers.has("Accept")) {
      headers.set("Accept", "application/json");
    }

    const token = this.getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await this.fetchImpl(`${this.baseUrl}${path}`, {
      ...init,
      headers
    });

    if (!response.ok) {
      const details = await parseError(response);
      throw new ApiError(response.status, errorMessageForResponse(response, details), details);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return response.json() as Promise<T>;
  }

  async requestBlob(path: string, init: RequestInit = {}): Promise<Blob> {
    const headers = new Headers(init.headers);
    const token = this.getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await this.fetchImpl(`${this.baseUrl}${path}`, {
      ...init,
      headers
    });

    if (!response.ok) {
      const details = await parseError(response);
      throw new ApiError(response.status, errorMessageForResponse(response, details), details);
    }

    return response.blob();
  }
}

function errorMessageForResponse(response: Response, details?: ApiErrorResponse) {
  if (details?.message) {
    return details.message;
  }
  if (response.status === 401 || response.status === 403) {
    return "Your session is not authorized for this action. Log out, then sign in again with a landlord, property manager, or admin account.";
  }
  return response.statusText || `Request failed with status ${response.status}`;
}

async function parseError(response: Response): Promise<ApiErrorResponse | undefined> {
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    return undefined;
  }

  try {
    return (await response.json()) as ApiErrorResponse;
  } catch {
    return undefined;
  }
}

function normalizeBaseUrl(baseUrl: string): string {
  const trimmed = baseUrl.trim();
  if (!trimmed) {
    return "";
  }
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

function resolveBaseUrl(configuredBaseUrl?: string): string {
  const fromConfig = normalizeBaseUrl(configuredBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? "");
  if (fromConfig) {
    return fromConfig;
  }
  // Keep local development working even when frontend/.env has not been created yet.
  if (import.meta.env.DEV) {
    return "http://localhost:8080";
  }
  return "";
}

function queryString<T extends object>(params: T): string {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (isQueryValue(value)) {
      searchParams.set(key, String(value));
    }
  }
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

function isQueryValue(value: unknown): value is string | number | boolean {
  return value !== undefined && value !== null && value !== "";
}
