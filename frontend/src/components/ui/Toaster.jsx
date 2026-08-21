const VARIANTS = {
  success: { ring: 'ring-accent-green/30', bar: 'bg-accent-green', icon: 'M20 6 9 17l-5-5' },
  error: { ring: 'ring-accent-red/30', bar: 'bg-accent-red', icon: 'M18 6 6 18M6 6l12 12' },
  warning: { ring: 'ring-accent-orange/30', bar: 'bg-accent-orange', icon: 'M12 9v4m0 4h.01' },
  info: { ring: 'ring-brand/25', bar: 'bg-brand', icon: 'M12 8h.01M11 12h1v4h1' },
}

export function Toaster({ toasts, onDismiss }) {
  if (!toasts.length) return null
  return (
    <div
      className="pointer-events-none fixed bottom-5 right-5 z-[100] flex w-[min(92vw,380px)] flex-col gap-2.5"
      role="status"
      aria-live="polite"
    >
      {toasts.map((toast) => {
        const variant = VARIANTS[toast.variant] || VARIANTS.info
        return (
          <div
            key={toast.id}
            className={`pointer-events-auto flex animate-slide-up overflow-hidden rounded bg-white
              shadow-pop ring-1 ${variant.ring}`}
          >
            <span className={`w-1 shrink-0 ${variant.bar}`} />
            <div className="flex flex-1 items-start gap-3 p-3.5">
              <svg
                className="mt-0.5 h-4 w-4 shrink-0 text-ink-muted"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
              >
                <path d={variant.icon} />
              </svg>
              <div className="min-w-0 flex-1">
                <p className="text-[13.5px] font-medium text-ink">{toast.title}</p>
                {toast.description && (
                  <p className="mt-0.5 break-words text-[12.5px] leading-5 text-ink-muted">
                    {toast.description}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => onDismiss(toast.id)}
                className="shrink-0 rounded p-1 text-ink-muted transition hover:bg-surface-canvas hover:text-ink"
                aria-label="Dismiss notification"
              >
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        )
      })}
    </div>
  )
}
