import { useQuery } from "@tanstack/react-query";
import { getCategories } from "@/lib/api";
import { categoriesKeys } from "@/lib/queryKeys";

/**
 * The 11 fixed spend categories, canonical order + Polish labels — always sourced from
 * GET /api/categories, never hardcoded (see CLAUDE.md § Categories / the frontend agent's
 * quality gate). This rarely changes, so it's cached generously.
 */
export function useCategories() {
  return useQuery({
    queryKey: categoriesKeys.all,
    queryFn: getCategories,
    staleTime: 60 * 60 * 1000,
  });
}
