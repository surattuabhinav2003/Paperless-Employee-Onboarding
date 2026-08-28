/**
 * Neutara Technologies wordmark.
 *
 * The mark itself is the real logo asset rather than a redrawn approximation.
 * It ships in brand blue, so on a dark surface it is flipped to white with a
 * filter - per the brand rule that a blue ground takes the white logo and a
 * white ground takes the blue one.
 */
export function Logo({ onDark = false, subtitle = 'Onboarding', compact = false }) {
  const wordColor = onDark ? 'text-white' : 'text-brand'
  const subColor = onDark ? 'text-white/60' : 'text-ink-muted'

  return (
    <div className="flex items-center gap-2.5">
      <img
        src="/neutara-mark.png"
        alt="Neutara Technologies"
        className="h-9 w-9 shrink-0 object-contain"
        style={onDark ? { filter: 'brightness(0) invert(1)' } : undefined}
      />
      {!compact && (
        <span className="leading-tight">
          <span className={`block text-[17px] font-semibold tracking-[-0.01em] ${wordColor}`}>
            Neutara
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
