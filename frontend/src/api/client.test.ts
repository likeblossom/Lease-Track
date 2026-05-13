import { describe, expect, it, vi } from "vitest";
import { ApiClient, ApiError } from "./client";

describe("ApiClient", () => {
  it("sends bearer tokens on authenticated requests", async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ status: "UP" }));
    const client = new ApiClient({
      baseUrl: "https://api.example.test/",
      getToken: () => "test-token",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    await client.getHealth();

    expect(fetchImpl).toHaveBeenCalledWith(
      "https://api.example.test/api/health",
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
    const [, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-token");
    expect(headers.get("Accept")).toBe("application/json");
  });

  it("surfaces backend validation messages", async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse(
        {
          status: 400,
          error: "Bad Request",
          message: "The first registered user must be an admin"
        },
        400
      )
    );
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    await expect(
      client.register({
        displayName: "Test User",
        email: "test@example.com",
        password: "password123",
        role: "LANDLORD"
      })
    ).rejects.toThrow(ApiError);
    await expect(
      client.register({
        displayName: "Test User",
        email: "test@example.com",
        password: "password123",
        role: "LANDLORD"
      })
    ).rejects.toThrow("The first registered user must be an admin");
  });

  it("surfaces useful messages for empty forbidden responses", async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 403 }));
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    await expect(client.listLeases({ size: 50 })).rejects.toThrow(
      "Your session is not authorized for this action"
    );
  });

  it("lists notices with filters and pagination", async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 1,
        first: false,
        last: true,
        empty: true
      })
    );
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    await client.listNotices({
      status: "OPEN",
      noticeType: "RENT_INCREASE",
      deliveryMethod: "REGISTERED_MAIL",
      page: 1,
      size: 10
    });

    expect(fetchImpl).toHaveBeenCalledWith(
      "https://api.example.test/api/notices?status=OPEN&noticeType=RENT_INCREASE&deliveryMethod=REGISTERED_MAIL&page=1&size=10",
      expect.objectContaining({ headers: expect.any(Headers) })
    );
  });

  it("creates notices and updates evidence through authenticated JSON requests", async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ id: "notice-1", deliveryAttempts: [] }));
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      getToken: () => "test-token",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    await client.createNotice({
      recipientName: "Marie Tremblay",
      recipientContactInfo: "marie@example.com",
      noticeType: "RENT_INCREASE",
      deliveryMethod: "REGISTERED_MAIL",
      deadlineAt: "2026-06-01T12:00:00Z",
      notes: "Annual increase"
    });
    await client.updateDeliveryAttemptStatus("notice-1", "attempt-1", { status: "SENT" });
    await client.upsertDeliveryEvidence("notice-1", "attempt-1", {
      carrierName: "Canada Post",
      trackingNumber: "1234567890123456",
      deliveryConfirmation: true
    });

    expect(fetchImpl).toHaveBeenNthCalledWith(
      1,
      "https://api.example.test/api/notices",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("Marie Tremblay"),
        headers: expect.any(Headers)
      })
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      "https://api.example.test/api/notices/notice-1/attempts/attempt-1/status",
      expect.objectContaining({
        method: "PATCH",
        body: JSON.stringify({ status: "SENT" }),
        headers: expect.any(Headers)
      })
    );
    expect(fetchImpl).toHaveBeenNthCalledWith(
      3,
      "https://api.example.test/api/notices/notice-1/attempts/attempt-1/evidence",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining("Canada Post"),
        headers: expect.any(Headers)
      })
    );

    for (const [, init] of fetchImpl.mock.calls as unknown as Array<[string, RequestInit]>) {
      const headers = init.headers as Headers;
      expect(headers.get("Authorization")).toBe("Bearer test-token");
      expect(headers.get("Content-Type")).toBe("application/json");
    }
  });

  it("uploads evidence documents as multipart form data without forcing JSON content type", async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ id: "document-1" }));
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      getToken: () => "test-token",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });
    const file = new File(["receipt"], "receipt.txt", { type: "text/plain" });

    await client.uploadEvidenceDocument("notice 1", "attempt 1", "CARRIER_RECEIPT", file);

    expect(fetchImpl).toHaveBeenCalledWith(
      "https://api.example.test/api/notices/notice%201/attempts/attempt%201/evidence/documents",
      expect.objectContaining({
        method: "POST",
        body: expect.any(FormData),
        headers: expect.any(Headers)
      })
    );
    const [, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    const headers = init.headers as Headers;
    const body = init.body as FormData;
    expect(headers.get("Authorization")).toBe("Bearer test-token");
    expect(headers.has("Content-Type")).toBe(false);
    expect(body.get("documentType")).toBe("CARRIER_RECEIPT");
    expect(body.get("file")).toBe(file);
  });

  it("downloads evidence package PDFs as blobs", async () => {
    const pdf = new Blob(["%PDF"], { type: "application/pdf" });
    const fetchImpl = vi.fn(
      async () =>
        new Response(pdf, {
          status: 200,
          headers: { "Content-Type": "application/pdf" }
        })
    );
    const client = new ApiClient({
      baseUrl: "https://api.example.test",
      getToken: () => "test-token",
      fetchImpl: fetchImpl as unknown as typeof fetch
    });

    const response = await client.downloadEvidencePackagePdf("notice-1");

    expect(response.type).toBe("application/pdf");
    expect(fetchImpl).toHaveBeenCalledWith(
      "https://api.example.test/api/notices/notice-1/evidence-package.pdf",
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );
    const [, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    const headers = init.headers as Headers;
    expect(headers.get("Accept")).toBe("application/pdf");
    expect(headers.get("Authorization")).toBe("Bearer test-token");
  });
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json"
    }
  });
}
