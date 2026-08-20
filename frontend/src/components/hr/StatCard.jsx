const ICONS = {
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  upload: 'M12 16V4m0 0L8 8m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2',
  review: 'M14 3v5h5M6 3h9l5 5v13H6V3Zm3 9h6M9 16h4',
  signed: 'M9 12l2 2 4-4m-7 9h6a5 5 0 0 0 5-5V7a5 5 0 0 0-5-5H8a5 5 0 0 0-5 5v6a5 5 0 0 0 5 5Z',
}

const TONES = {
  brand: 'bg-brand-tint text-brand',
  amber: 'bg-[#FFF4EC] text-[#B94A18]',
  blue: 'bg-[#E8F1FF] text-accent-blue',
  green: 'bg-[#E8FAF1] text-[#0E7A47]',
}

export function StatCard({ label, value, hint, icon = 'users', tone = 'brand', loading = false }) {
  return (
    <div className="cf-card p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] font-medium uppercase tracking-[0.1em] text-ink-muted">{label}</p>
          {loading ? (
            <div className="cf-skeleton mt-2 h-8 w-16" />
          ) : (
            <p className="mt-1.5 text-[28px] font-semibold leading-none tracking-[-0.02em] text-ink">
              {value}
            </p>
          )}
          {hint && <p className="mt-2 text-[12px] leading-5 text-ink-muted">{hint}</p>}
        </div>
        <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-card ${TONES[tone]}`}>
          <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" stroke="currentColor"
            strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <path d={ICONS[icon]} />
          </svg>
        </span>
      </div>
    </div>
  )
}
