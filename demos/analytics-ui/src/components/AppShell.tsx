import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export type NavItem = {
  id: string;
  label: string;
  icon: LucideIcon;
};

type AppShellProps = {
  active: string;
  navItems: NavItem[];
  onSelect: (id: string) => void;
  title: string;
  subtitle: string;
  actions?: ReactNode;
  children: ReactNode;
};

export function AppShell({ active, navItems, onSelect, title, subtitle, actions, children }: AppShellProps) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">A</div>
          <div>
            <strong>{title}</strong>
            <span>Demo app</span>
          </div>
        </div>
        <nav aria-label="Analytics workspace">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                aria-current={active === item.id ? "page" : undefined}
                className="nav-button"
                key={item.id}
                onClick={() => onSelect(item.id)}
                type="button"
              >
                <Icon size={17} />
                {item.label}
              </button>
            );
          })}
        </nav>
      </aside>
      <main className="main-surface">
        <header className="topbar">
          <div>
            <h1>{title}</h1>
            <p>{subtitle}</p>
          </div>
          <div className="topbar-actions">{actions}</div>
        </header>
        <div className="content-surface">{children}</div>
      </main>
    </div>
  );
}
