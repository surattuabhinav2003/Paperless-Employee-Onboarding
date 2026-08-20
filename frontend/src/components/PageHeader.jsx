export function PageHeader({ title, subtitle, actions, breadcrumb, className = '' }) {
  return (
    <div className={`mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between ${className}`}>
      <div className="min-w-0">
        {breadcrumb && (
          <p className="mb-1.5 text-[11.5px] font-medium uppercase tracking-[0.14em] text-ink-muted">
            {breadcrumb}
          </p>
        )}
        <h1 className="text-[24px] font-semibold tracking-[-0.01em] text-ink">{title}</h1>
        {subtitle && <p className="mt-1.5 max-w-2xl text-[13.5px] leading-6 text-ink-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2.5">{actions}</div>}
    </div>
  )
}
