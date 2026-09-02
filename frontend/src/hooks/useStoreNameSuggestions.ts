import { useQuery } from "@tanstack/react-query";
import { getStoreNameSuggestions } from "@/lib/api";
import { storeNameSuggestionsKeys } from "@/lib/queryKeys";

/**
 * Ranked (most-used, then most-recently-used), deduplicated, capped-at-20 list of past store
 * names — feeds the manual-entry form's "Sklep / dostawca" combobox as autocomplete hints only
 * (see GET /api/receipts/store-names in docs/openapi.yaml; every suggestion is a hint, never a
 * constraint). Read-mostly reference data that only grows one entry per receipt, so it's cached
 * generously like useCategories.
 */
export function useStoreNameSuggestions() {
  return useQuery({
    queryKey: storeNameSuggestionsKeys.all,
    queryFn: getStoreNameSuggestions,
    staleTime: 60 * 60 * 1000,
  });
}
