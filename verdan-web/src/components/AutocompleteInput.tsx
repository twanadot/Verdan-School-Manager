import { useState, useRef, useEffect } from 'react';

export interface AutocompleteOption {
  value: string;
  label: string;
  sublabel?: string;
}

interface AutocompleteInputProps {
  value: string;
  onChange: (value: string) => void;
  options: AutocompleteOption[];
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  className?: string;
}

export function AutocompleteInput({
  value,
  onChange,
  options,
  placeholder,
  required,
  disabled,
  className,
}: AutocompleteInputProps) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  // Close on click outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const displaySearch = open ? search : value;

  const filtered = options.filter(o =>
    o.value.toLowerCase().includes((open ? search : value).toLowerCase()) ||
    o.label.toLowerCase().includes((open ? search : value).toLowerCase()) ||
    (o.sublabel?.toLowerCase().includes((open ? search : value).toLowerCase()) ?? false)
  ).slice(0, 8);

  return (
    <div ref={ref} className="relative">
      <input
        value={displaySearch}
        onChange={e => {
          setSearch(e.target.value);
          if (!open) setOpen(true);
          // Also update the actual value on typing
          onChange(e.target.value);
        }}
        onFocus={() => {
          setOpen(true);
          setSearch(value);
        }}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        autoComplete="off"
        className={className || 'w-full px-3 py-2 bg-bg-input border border-border rounded-lg text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:border-border-focus'}
      />
      {open && filtered.length > 0 && (
        <ul className="absolute z-50 w-full mt-1 max-h-48 overflow-y-auto bg-bg-secondary border border-border rounded-lg shadow-xl">
          {filtered.map(o => (
            <li
              key={o.value}
              onMouseDown={(e) => {
                e.preventDefault();
                onChange(o.value);
                setSearch(o.value);
                setOpen(false);
              }}
              className={`px-3 py-2 text-sm cursor-pointer transition-colors hover:bg-bg-hover ${o.value === value ? 'bg-accent/10 text-accent' : 'text-text-primary'}`}
            >
              <span className="font-medium">{o.label}</span>
              {o.sublabel && <span className="ml-2 text-text-muted text-xs font-mono">{o.sublabel}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
