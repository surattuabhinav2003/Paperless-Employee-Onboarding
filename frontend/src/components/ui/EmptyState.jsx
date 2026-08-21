export function EmptyState({ title, message, action, icon = 'inbox', className = '' }) {
  const paths = {
    inbox: 'M3 12h4l2 3h6l2-3h4M5 6h14l2 6v6H3v-6l2-6Z',
    search: 'm21 21-4.35-4.35M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z',
    document: 'M14 3v5h5M6 3h9l5 5v13H6V3Z',
    check: 'M20 6 9 17l-5-5',
    lock: 'M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5V11Z',
  }
  return (
    <div className={`flex flex-col items-center justify-center px-6 py-14 text-center ${className}`}>
      <span className="mb-4 flex h-11 w-11 items-center justify-center rounded bg-brand-tint text-brand ring-1 ring-inset ring-brand/15">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round">
          <path d={paths[icon] || paths.inbox} />
        </svg>
      </span>
      <h3 className="text-[15px] font-semibold text-ink">{title}</h3>
      {message && <p className="mt-1.5 max-w-md text-[13px] leading-6 text-ink-muted">{message}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ title = 'Something went wrong', message, action, className = '' }) {
  return (
    <div className={`flex flex-col items-center justify-center px-6 py-14 text-center ${className}`}>
      <span className="mb-4 flex h-11 w-11 items-center justify-center rounded bg-[#FFECEC] text-accent-red ring-1 ring-inset ring-accent-red/20">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M12 8v5m0 3h.01" />
          <circle cx="12" cy="12" r="9" />
        </svg>
      </span>
      <h3 className="text-[15px] font-semibold text-ink">{title}</h3>
      {message && <p className="mt-1.5 max-w-md text-[13px] leading-6 text-ink-muted">{message}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
