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

const navigationItems = [
  { label: "Dashboard", href: "#dashboard", icon: Home },
  { label: "Properties", href: "#properties", icon: Building2 },
  { label: "Leases", href: "#leases", icon: ScrollText },
  { label: "Tenants", href: "#tenants", icon: Users },
  { label: "Notices", href: "#notices", icon: FileCheck2 },
  { label: "Evidence Vault", href: "#evidence-vault", icon: FileArchive },
  { label: "Calendar", href: "#calendar", icon: CalendarDays },
  { label: "Analytics", href: "#analytics", icon: BarChart3 },
  { label: "Settings", href: "#settings", icon: Settings }
];

export function AppShell({
  children,
  initials,
  title,
  user,
  onLogout
}: {
  children: React.ReactNode;
  initials: string;
  title: string;
  user: UserResponse | null;
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
              <a className={`nav-item ${item.href === "#dashboard" ? "active" : ""}`} href={item.href} key={item.href}>
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
