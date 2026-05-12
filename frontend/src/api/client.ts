export type UserRole = "LANDLORD" | "PROPERTY_MANAGER" | "TENANT" | "ADMIN";

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

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body && !headers.has("Content-Type")) {
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
      throw new ApiError(response.status, details?.message ?? response.statusText, details);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return response.json() as Promise<T>;
  }
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
