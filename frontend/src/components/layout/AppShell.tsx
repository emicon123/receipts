import type { ReactNode } from "react";
import { BottomNav } from "@/components/layout/BottomNav";

interface AppShellProps {
  title: string;
  children: ReactNode;
}

export function AppShell({ title, children }: AppShellProps) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header
        className="sticky top-0 z-30 border-b border-border bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/80"
        style={{ paddingTop: "env(safe-area-inset-top)" }}
      >
        <h1 className="px-4 py-3 text-lg font-semibold">{title}</h1>
      </header>
      <main className="flex-1 overflow-y-auto px-4 py-4">{children}</main>
      <BottomNav />
    </div>
  );
}
