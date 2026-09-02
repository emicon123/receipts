import { useState } from "react";
import { Command, CommandGroup, CommandItem, CommandList } from "@/components/ui/command";
import { Input } from "@/components/ui/input";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { useStoreNameSuggestions } from "@/hooks/useStoreNameSuggestions";

interface StoreNameComboboxProps {
  id?: string;
  value: string;
  onValueChange: (value: string) => void;
  placeholder?: string;
}

/**
 * Free-text store/vendor field with autocomplete suggestions from past receipts
 * (GET /api/receipts/store-names). Every suggestion is a hint, never a constraint — the
 * bound `value` is always exactly what's in the input, whether typed or picked from the
 * dropdown, so callers keep treating this like a plain text field (see ManualEntryRoute's
 * `storeName`/`setStoreName`).
 */
export function StoreNameCombobox({ id, value, onValueChange, placeholder }: StoreNameComboboxProps) {
  const { data: suggestions } = useStoreNameSuggestions();
  const [open, setOpen] = useState(false);

  const filtered = (suggestions ?? []).filter((name) =>
    name.toLowerCase().includes(value.trim().toLowerCase()),
  );
  const showSuggestions = open && filtered.length > 0;

  return (
    <Popover open={showSuggestions} onOpenChange={setOpen}>
      <PopoverAnchor asChild>
        <Input
          id={id}
          value={value}
          onChange={(e) => {
            onValueChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder={placeholder}
          autoComplete="off"
          role="combobox"
          aria-expanded={showSuggestions}
          aria-autocomplete="list"
        />
      </PopoverAnchor>
      <PopoverContent align="start" onOpenAutoFocus={(e) => e.preventDefault()}>
        <Command shouldFilter={false}>
          <CommandList>
            <CommandGroup>
              {filtered.map((name) => (
                <CommandItem
                  key={name}
                  value={name}
                  onMouseDown={(e) => e.preventDefault()}
                  onSelect={() => {
                    onValueChange(name);
                    setOpen(false);
                  }}
                >
                  {name}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
