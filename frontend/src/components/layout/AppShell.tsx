import { ChevronLeft } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { BottomNav } from "@/components/layout/BottomNav";

interface AppShellProps {
  title: string;
  /** Optional back-navigation target (e.g. the dashboard, from a category drill-down). Renders
   * a leading chevron button in the header when set; omitted entirely otherwise. */
  backTo?: string;
  children: ReactNode;
}

export function AppShell({ title, backTo, children }: AppShellProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header
        className="sticky top-0 z-30 border-b border-border bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/80"
        style={{ paddingTop: "env(safe-area-inset-top)" }}
      >
        <div className="flex items-center gap-1 px-2 py-2">
          {backTo && (
            <Link
              to={backTo}
              aria-label="Wróć"
              className="-ml-1 flex size-9 shrink-0 items-center justify-center rounded-full text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <ChevronLeft className="size-5" />
            </Link>
          )}
          <h1 className={backTo ? "truncate py-1 text-lg font-semibold" : "truncate px-2 py-1 text-lg font-semibold"}>
            {title}
          </h1>
        </div>
      </header>
      <main className="flex-1 overflow-y-auto px-4 py-4">{children}</main>
      <BottomNav />
    </div>
  );
}
