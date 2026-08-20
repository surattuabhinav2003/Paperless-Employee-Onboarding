/**
 * CloudFuze wordmark. On a blue surface the mark is white; on white it is blue -
 * per the brand guidelines.
 */
export function Logo({ onDark = false, subtitle = 'Onboarding', compact = false }) {
  const markBg = onDark ? 'bg-white/12 text-white ring-1 ring-white/25' : 'bg-brand text-white'
  const wordColor = onDark ? 'text-white' : 'text-brand'
  const subColor = onDark ? 'text-white/60' : 'text-ink-muted'

  return (
    <div className="flex items-center gap-2.5">
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-[8px] ${markBg}`}>
        <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="currentColor" aria-hidden="true">
          <path d="M17.2 9.4a5.4 5.4 0 0 0-10.1-1.5A4.5 4.5 0 0 0 3.2 12a4.6 4.6 0 0 0 4.6 4.5h9a3.9 3.9 0 0 0 .4-7.6Zm-6.6 5.1V11l-1.7 1.2 2.9-4.1v3.5l1.7-1.2-2.9 4.1Z" />
        </svg>
      </span>
      {!compact && (
        <span className="leading-tight">
          <span className={`block text-[16px] font-semibold tracking-[-0.01em] ${wordColor}`}>
            CloudFuze
          </span>
          {subtitle && (
            <span className={`block text-[11px] font-medium uppercase tracking-[0.14em] ${subColor}`}>
              {subtitle}
            </span>
          )}
        </span>
      )}
    </div>
  )
}
