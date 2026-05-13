import {
  BarChart3,
  Building2,
  CalendarDays,
  FileArchive,
  FileCheck2,
  Home,
  LogOut,
  ScrollText,
  Settings,
  Users
} from "lucide-react";
import type { UserResponse } from "../../api/client";

export type WorkspacePage =
  | "dashboard"
  | "properties"
  | "leases"
  | "tenants"
  | "notices"
  | "evidence-vault"
  | "calendar"
  | "analytics"
  | "settings";

const navigationItems = [
  { label: "Dashboard", page: "dashboard", icon: Home },
  { label: "Properties", page: "properties", icon: Building2 },
  { label: "Leases", page: "leases", icon: ScrollText },
  { label: "Tenants", page: "tenants", icon: Users },
  { label: "Notices", page: "notices", icon: FileCheck2 },
  { label: "Evidence Vault", page: "evidence-vault", icon: FileArchive },
  { label: "Calendar", page: "calendar", icon: CalendarDays },
  { label: "Analytics", page: "analytics", icon: BarChart3 },
  { label: "Settings", page: "settings", icon: Settings }
] satisfies Array<{
  label: string;
  page: WorkspacePage;
  icon: React.ComponentType<{ size?: number; "aria-hidden"?: boolean }>;
}>;

export function AppShell({
  activePage,
  children,
  initials,
  title,
  user,
  onNavigate,
  onLogout
}: {
  activePage: WorkspacePage;
  children: React.ReactNode;
  initials: string;
  title: string;
  user: UserResponse | null;
  onNavigate: (page: WorkspacePage) => void;
  onLogout: () => void;
}) {
  return (
    <main className="workspace">
      <aside className="sidebar" aria-label="Workspace navigation">
        <div className="sidebar-brand">
          <div className="brand-mark small">
            <FileCheck2 aria-hidden="true" size={20} />
          </div>
          <div>
            <strong>Lease Track</strong>
            <span>Operations</span>
          </div>
        </div>
        <nav>
          {navigationItems.map((item) => {
            const Icon = item.icon;
            return (
              <a
                className={`nav-item ${item.page === activePage ? "active" : ""}`}
                href={`#${item.page}`}
                key={item.page}
                onClick={(event) => {
                  event.preventDefault();
                  onNavigate(item.page);
                }}
              >
                <Icon aria-hidden="true" size={17} />
                <span>{item.label}</span>
              </a>
            );
          })}
        </nav>
      </aside>

      <section className="workspace-main">
        <header className="topbar">
          <div>
            <p className="eyebrow">Workspace</p>
            <h1>{title}</h1>
          </div>
          <div className="topbar-actions">
            <div className="account-chip">
              <span aria-hidden="true">{initials}</span>
              <div>
                <strong>{user?.displayName ?? "Signed in"}</strong>
                <small>{user?.email ?? "Authenticated session"}</small>
              </div>
              <button type="button" className="icon-button" onClick={onLogout} aria-label="Log out">
                <LogOut aria-hidden="true" size={18} />
              </button>
            </div>
          </div>
        </header>
        {children}
      </section>
    </main>
  );
}
