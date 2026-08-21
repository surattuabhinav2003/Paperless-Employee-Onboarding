export function ProgressBar({ value = 0, total = 0, tone = 'brand', showLabel = true, className = '' }) {
  const percent = total > 0 ? Math.min(100, Math.round((value / total) * 100)) : 0
  const tones = {
    brand: 'bg-brand-mid',
    green: 'bg-[#35B37E]',
    slate: 'bg-soft-blue',
    amber: 'bg-amber',
  }
  return (
    <div className={className}>
      <div className="flex items-center gap-2">
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-hair">
          <div
            className={`h-full rounded-full transition-all duration-500 ${tones[tone] || tones.brand}`}
            style={{ width: `${percent}%` }}
          />
        </div>
        {showLabel && (
          <span className="shrink-0 text-[11.5px] font-medium tabular-nums text-ink-muted">
            {value}/{total}
          </span>
        )}
      </div>
    </div>
  )
}
