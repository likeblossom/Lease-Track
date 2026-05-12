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
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json"
    }
  });
}
