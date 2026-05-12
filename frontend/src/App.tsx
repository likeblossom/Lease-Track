import {
  ArrowRight,
  BadgeCheck,
  BellRing,
  FileCheck2,
  LogOut,
  Mail,
  ShieldCheck,
  UserPlus
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ApiError } from "./api/client";
import { useAuth } from "./auth/AuthContext";

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
  const { user, logout } = useAuth();
  const initials = useMemo(() => initialsFor(user?.displayName ?? user?.email ?? "Lease Track"), [user]);

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
          <a className="nav-item" href="#notices">
            Notices
          </a>
          <a className="nav-item" href="#evidence">
            Evidence
          </a>
        </nav>
      </aside>

      <section className="workspace-main">
        <header className="topbar">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1>Operations dashboard</h1>
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
          <Metric label="Notices" value="Ready" />
          <Metric label="Evidence" value="Configured" />
          <Metric label="Tracking" value="Queued" />
        </section>

        <section className="empty-workspace" id="overview">
          <div>
            <p className="eyebrow">Part 2 complete</p>
            <h2>Auth and app shell are ready.</h2>
            <p>
              Notice workflows, evidence upload, and package generation will be added in the next parts without
              changing this authentication foundation.
            </p>
          </div>
        </section>
      </section>
    </main>
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
    if (error.message.includes("first registered user must be an admin")) {
      return "This deployment needs its first admin account created through the bootstrap setup before public sign-up is available.";
    }
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
