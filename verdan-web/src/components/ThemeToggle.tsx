import { Moon, Sun, Monitor } from 'lucide-react';
import { useTheme } from '../contexts/ThemeProvider';
import { useState, useRef, useEffect } from 'react';

export function ThemeToggle({ collapsed }: { collapsed?: boolean }) {
  const { theme, setTheme } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const themes = [
    { value: 'light', label: 'Lys', icon: <Sun size={14} /> },
    { value: 'dark', label: 'Mørk', icon: <Moon size={14} /> },
    { value: 'system', label: 'System', icon: <Monitor size={14} /> },
  ] as const;

  const activeOption = themes.find(t => t.value === theme) || themes[0];

  return (
    <div className={`relative ${collapsed ? 'flex justify-center' : ''}`} ref={dropdownRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`flex items-center gap-2 w-full px-3 py-2 rounded-lg text-sm text-text-secondary hover:bg-sidebar-hover hover:text-text-primary transition-colors ${collapsed ? 'justify-center p-2' : ''}`}
        title="Utseende"
      >
        {activeOption.icon}
        {!collapsed && <span>Utseende</span>}
      </button>

      {isOpen && (
        <div className={`absolute bottom-full mb-1 z-50 min-w-[200px] bg-bg-card border border-border shadow-lg rounded-xl overflow-hidden py-1 ${collapsed ? 'left-4' : 'left-0'}`}>
          <div className="px-3 py-1.5 text-xs font-semibold text-text-muted uppercase tracking-wider">
            Utseende
          </div>
          <div className="flex px-2 pb-2 gap-1">
            {themes.map((t) => (
              <button
                key={t.value}
                onClick={() => setTheme(t.value)}
                title={t.label}
                className={`flex-1 flex justify-center items-center py-2 rounded-md transition-colors ${
                  theme === t.value
                    ? 'text-accent bg-accent/10 border border-accent/20'
                    : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary border border-transparent'
                }`}
              >
                {t.icon}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
