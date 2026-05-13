import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";

describe("App auth routes", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
    window.history.pushState({}, "", "/");
  });

  it("shows login on the landing page without first-admin controls", () => {
    renderApp("/");

    expect(screen.getByRole("heading", { name: "Welcome back" })).toBeInTheDocument();
    expect(screen.getByText("Need an account?")).toBeInTheDocument();
    expect(screen.queryByText(/first admin/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/create admin/i)).not.toBeInTheDocument();
  });

  it("shows a public register page without account-type choices", () => {
    renderApp("/register");

    expect(screen.getByRole("heading", { name: "Create your account" })).toBeInTheDocument();
    expect(screen.queryByText("Account type")).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /landlord/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /property manager/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /admin/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /tenant/i })).not.toBeInTheDocument();
  });

  it("registers public users and logs them in", async () => {
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const body = init?.body ? JSON.parse(init.body.toString()) : undefined;
      if (body?.role === "ADMIN" || body?.role === "TENANT") {
        return jsonResponse({ message: "Invalid role" }, 400);
      }
      if (body?.role === "LANDLORD") {
        return jsonResponse({
          id: "user-1",
          displayName: body.displayName,
          email: body.email,
          role: body.role
        });
      }
      return jsonResponse({
        accessToken: "registered-token",
        tokenType: "Bearer",
        expiresInSeconds: 3600
      });
    });
    vi.stubGlobal("fetch", fetchImpl);

    renderApp("/register");
    fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "Avery Manager" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "avery@example.com" } });
    fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /create account/i }));

    await waitFor(() => {
      expect(window.sessionStorage.getItem("lease-track.auth-token")).toBe("registered-token");
    });

    const registerBody = JSON.parse((fetchImpl.mock.calls[0][1]?.body ?? "{}").toString());
    expect(registerBody).toMatchObject({
      displayName: "Avery Manager",
      email: "avery@example.com",
      role: "LANDLORD"
    });
    expect(registerBody.role).not.toBe("PROPERTY_MANAGER");
    expect(registerBody.role).not.toBe("ADMIN");
    expect(registerBody.role).not.toBe("TENANT");
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/login"),
      expect.objectContaining({ method: "POST" })
    );
  });

  it("surfaces backend registration errors without bootstrap copy", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse(
          {
            status: 400,
            error: "Bad Request",
            message: "The first registered user must be an admin"
          },
          400
        )
      )
    );

    renderApp("/register");
    fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "Avery Manager" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "avery@example.com" } });
    fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The first registered user must be an admin");
    expect(screen.queryByText(/bootstrap setup/i)).not.toBeInTheDocument();
  });

  it("loads the workspace dashboard without legacy lease panels", async () => {
    window.sessionStorage.setItem("lease-track.auth-token", "workspace-token");
    window.sessionStorage.setItem(
      "lease-track.auth-user",
      JSON.stringify({
        id: "user-1",
        displayName: "Avery Manager",
        email: "avery@example.com",
        role: "LANDLORD"
      })
    );
    const fetchImpl = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      if (url.endsWith("/api/leases?size=50")) {
        return jsonResponse({
          content: [leaseSummaryResponse()],
          totalElements: 1,
          totalPages: 1,
          size: 50,
          number: 0,
          first: true,
          last: true,
          empty: false
        });
      }
      if (url.endsWith("/api/properties?size=50")) {
        return jsonResponse({
          ...emptyPage(),
          content: [propertySummaryResponse()],
          totalElements: 1,
          totalPages: 1,
          empty: false
        });
      }
      if (url.endsWith("/api/properties/property-1")) {
        return jsonResponse(propertyResponse());
      }
      if (url.endsWith("/api/leases/lease-1")) {
        return jsonResponse(leaseResponse());
      }
      if (url.endsWith("/api/notices?size=50")) {
        return jsonResponse({
          content: [
            {
              id: "notice-1",
              recipientName: "Marie Tremblay",
              noticeType: "RENT_INCREASE",
              status: "OPEN",
              createdAt: "2026-05-12T10:00:00Z",
              updatedAt: "2026-05-12T10:00:00Z"
            }
          ],
          totalElements: 1,
          totalPages: 1,
          size: 50,
          number: 0,
          first: true,
          last: true,
          empty: false
        });
      }
      if (url.endsWith("/api/notices/notice-1")) {
        return jsonResponse(noticeResponse("notice-1", "Marie Tremblay"));
      }
      if (url.includes("/evidence/documents")) {
        return jsonResponse([]);
      }
      if (url.endsWith("/audit-log")) {
        return jsonResponse([]);
      }
      if (url.endsWith("/api/notices") && init?.method === "POST") {
        return jsonResponse(noticeResponse("notice-2", "New Recipient"), 201);
      }
      if (url.endsWith("/api/notices/notice-2")) {
        return jsonResponse(noticeResponse("notice-2", "New Recipient"));
      }
      return jsonResponse({});
    });
    vi.stubGlobal("fetch", fetchImpl);

    renderApp("/");

    expect(await screen.findByRole("heading", { name: "Lease operations" })).toBeInTheDocument();
    expect(screen.queryByText("Maple Court - Unit 4B")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Property management" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "New lease" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /create notice/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("link", { name: "Properties" }));

    expect(await screen.findByRole("heading", { name: "Property management" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Operational command center" })).not.toBeInTheDocument();
  });

  it("shows seeded dashboard data only for test users", async () => {
    window.sessionStorage.setItem("lease-track.auth-token", "workspace-token");
    window.sessionStorage.setItem(
      "lease-track.auth-user",
      JSON.stringify({
        id: "user-1",
        displayName: "Test User",
        email: "test@example.com",
        role: "LANDLORD"
      })
    );
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        if (input.toString().endsWith("/api/leases?size=50")) {
          return jsonResponse(emptyPage());
        }
        if (input.toString().endsWith("/api/properties?size=50")) {
          return jsonResponse(emptyPage());
        }
        return jsonResponse({});
      })
    );

    renderApp("/");

    expect(await screen.findByText("Maple Court - Unit 4B")).toBeInTheDocument();
    expect(screen.getByText("148")).toBeInTheDocument();
  });
});

function renderApp(path: string) {
  window.history.pushState({}, "", path);
  return render(
    <AuthProvider>
      <App />
    </AuthProvider>
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json"
    }
  });
}

function emptyPage() {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 50,
    number: 0,
    first: true,
    last: true,
    empty: true
  };
}

function noticeResponse(id: string, recipientName: string) {
  return {
    id,
    recipientName,
    recipientContactInfo: "marie@example.com",
    noticeType: "RENT_INCREASE",
    status: "OPEN",
    ownerUserId: "user-1",
    leaseId: "lease-1",
    tenantUserId: null,
    notes: "",
    createdAt: "2026-05-12T10:00:00Z",
    updatedAt: "2026-05-12T10:00:00Z",
    closedAt: null,
    deliveryAttempts: [
      {
        id: "attempt-1",
        attemptNumber: 1,
        deliveryMethod: "REGISTERED_MAIL",
        status: "PENDING",
        sentAt: null,
        deliveredAt: null,
        deadlineAt: null,
        trackingSyncStatus: "PENDING",
        lastTrackingCheckedAt: null,
        deadlineReminderSent: false,
        createdAt: "2026-05-12T10:00:00Z",
        updatedAt: "2026-05-12T10:00:00Z"
      }
    ]
  };
}

function leaseSummaryResponse() {
  return {
    id: "lease-1",
    name: "Apt 4B - Tremblay",
    propertyAddress: "123 Rue Example",
    tenantNames: "Marie Tremblay",
    leaseStartDate: "2026-07-01",
    leaseEndDate: "2027-06-30",
    noticeCount: 1,
    openNoticeCount: 1,
    createdAt: "2026-05-12T10:00:00Z",
    updatedAt: "2026-05-12T10:00:00Z"
  };
}

function leaseResponse() {
  return {
    ...leaseSummaryResponse(),
    tenantEmail: "marie@example.com",
    tenantPhone: "514-555-0199",
    ownerUserId: "user-1",
    notes: "",
    notices: [
      {
        id: "notice-1",
        leaseId: "lease-1",
        recipientName: "Marie Tremblay",
        noticeType: "RENT_INCREASE",
        status: "OPEN",
        createdAt: "2026-05-12T10:00:00Z",
        updatedAt: "2026-05-12T10:00:00Z"
      }
    ]
  };
}

function propertySummaryResponse() {
  return {
    id: "property-1",
    name: "Riverside House",
    address: "123 Rainbow Road, Toronto, ON, M4B 1B3",
    unitCount: 2,
    occupiedUnitCount: 1,
    occupancyRate: 50,
    activeNoticeCount: 0,
    createdAt: "2026-05-12T10:00:00Z",
    updatedAt: "2026-05-12T10:00:00Z"
  };
}

function propertyResponse() {
  return {
    id: "property-1",
    name: "Riverside House",
    addressLine1: "123 Rainbow Road",
    addressLine2: null,
    city: "Toronto",
    province: "ON",
    postalCode: "M4B 1B3",
    country: "Canada",
    ownerUserId: "user-1",
    notes: "",
    unitCount: 2,
    occupiedUnitCount: 1,
    vacantUnitCount: 1,
    activeNoticeCount: 0,
    occupancyRate: 50,
    createdAt: "2026-05-12T10:00:00Z",
    updatedAt: "2026-05-12T10:00:00Z",
    units: [
      {
        id: "unit-1",
        propertyId: "property-1",
        unitLabel: "Apt 1",
        status: "OCCUPIED",
        bedrooms: 1,
        bathrooms: 1,
        squareFeet: 600,
        currentTenantNames: "John Doe",
        currentRentCents: 180000,
        createdAt: "2026-05-12T10:00:00Z",
        updatedAt: "2026-05-12T10:00:00Z"
      }
    ]
  };
}
