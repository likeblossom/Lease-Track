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

  it("shows bootstrap guidance when public registration is attempted before first admin setup", async () => {
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

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "first admin account created through the bootstrap setup"
    );
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
