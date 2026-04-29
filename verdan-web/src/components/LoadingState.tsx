import { Loader2 } from 'lucide-react';

export function LoadingState({ message = 'Loading...' }: { message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-text-secondary">
      <Loader2 size={32} className="animate-spin text-accent mb-3" />
      <p className="text-sm">{message}</p>
    </div>
  );
}

export function EmptyState({ title = 'No data', message = 'Nothing to show here yet.' }: { title?: string; message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-text-secondary">
      <div className="w-16 h-16 bg-bg-hover rounded-full flex items-center justify-center mb-4">
        <span className="text-2xl">📭</span>
      </div>
      <p className="font-medium text-text-primary">{title}</p>
      <p className="text-sm mt-1">{message}</p>
    </div>
  );
}
