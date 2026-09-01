import { Camera, LayoutGrid, PencilLine, Receipt } from "lucide-react";
import { NavLink } from "react-router-dom";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: typeof Camera;
  end?: boolean;
}

const items: NavItem[] = [
  { to: "/", label: "Skanuj", icon: Camera, end: true },
  { to: "/receipts", label: "Paragony", icon: Receipt },
  { to: "/dashboard", label: "Wydatki", icon: LayoutGrid },
  { to: "/receipts/manual", label: "Ręcznie", icon: PencilLine },
];

export function BottomNav() {
  return (
    <nav
      aria-label="Główna"
      className="sticky bottom-0 z-40 border-t border-border bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/80"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <ul className="grid grid-cols-4">
        {items.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center gap-1 py-2.5 text-xs font-medium text-muted-foreground",
                  "transition-colors hover:text-foreground",
                  isActive && "text-primary",
                )
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className="size-5" strokeWidth={isActive ? 2.5 : 2} />
                  {label}
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
