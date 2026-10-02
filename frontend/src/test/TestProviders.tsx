import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";

/** The providers every routed component needs: TanStack Query + an in-memory router. */
export function TestProviders({
  client,
  route = "/",
  children,
}: {
  client: QueryClient;
  route?: string;
  children: ReactNode;
}) {
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}
