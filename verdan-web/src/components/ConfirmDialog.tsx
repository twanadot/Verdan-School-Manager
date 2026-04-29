import { AlertTriangle, Archive, X } from 'lucide-react';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  loadingLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'warning' | 'accent';
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
}

const VARIANT_STYLES = {
  danger: {
    icon: <AlertTriangle size={20} className="text-danger" />,
    iconBg: 'bg-danger/10',
    button: 'bg-danger hover:bg-danger-hover text-white',
  },
  warning: {
    icon: <AlertTriangle size={20} className="text-yellow-400" />,
    iconBg: 'bg-yellow-400/10',
    button: 'bg-yellow-500 hover:bg-yellow-600 text-white',
  },
  accent: {
    icon: <Archive size={20} className="text-accent" />,
    iconBg: 'bg-accent/10',
    button: 'bg-accent hover:bg-accent-hover text-white',
  },
};

export function ConfirmDialog({
  open, title, message,
  confirmLabel = 'Bekreft',
  loadingLabel,
  cancelLabel = 'Avbryt',
  variant = 'danger',
  onConfirm, onCancel, loading,
}: ConfirmDialogProps) {
  if (!open) return null;

  const style = VARIANT_STYLES[variant];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onCancel} />
      <div className="relative bg-bg-secondary border border-border rounded-xl p-6 w-full max-w-md shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        <button onClick={onCancel} className="absolute top-3 right-3 text-text-muted hover:text-text-primary transition-colors">
          <X size={18} />
        </button>
        <div className="flex items-start gap-4">
          <div className={`w-10 h-10 ${style.iconBg} rounded-full flex items-center justify-center flex-shrink-0`}>
            {style.icon}
          </div>
          <div>
            <h3 className="text-lg font-semibold text-text-primary">{title}</h3>
            <p className="text-sm text-text-secondary mt-1">{message}</p>
          </div>
        </div>
        <div className="flex justify-end gap-3 mt-6">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm font-medium rounded-lg border border-border text-text-secondary hover:bg-bg-hover transition-colors"
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className={`px-4 py-2 text-sm font-medium rounded-lg ${style.button} transition-colors disabled:opacity-50`}
          >
            {loading ? (loadingLabel || confirmLabel + '...') : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
