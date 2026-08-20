/**
 * Shows the step the candidate is on. The backend sends only that step - a stage
 * they have finished, or have not unlocked, is never returned - so there is
 * nothing here to hide, unlock or navigate around.
 */
export function ProgressTracker({ steps = [] }) {
  if (!steps.length) return null

  return (
    <ol className="grid gap-3">
      {steps.map((step) => {
        const isCompleted = step.state === 'completed'
        return (
          <li key={step.key}>
            <div
              className={`flex items-start gap-3 rounded-card border p-3.5 ${
                isCompleted
                  ? 'border-accent-green/35 bg-[#F3FCF7]'
                  : 'border-brand/35 bg-brand-tint/60'
              }`}
            >
              <span
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-white ${
                  isCompleted ? 'bg-accent-green' : 'bg-brand'
                }`}
              >
                <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
                  strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <path d={isCompleted ? 'M20 6 9 17l-5-5' : 'M12 5v14m7-7-7 7-7-7'} />
                </svg>
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="text-[13.5px] font-semibold text-ink">{step.title}</span>
                  {!isCompleted && (
                    <span className="rounded-full bg-brand px-1.5 py-0.5 text-[9.5px] font-semibold
                      uppercase tracking-[0.1em] text-white">
                      Your step now
                    </span>
                  )}
                </span>
                <span className="mt-0.5 block text-[12px] leading-5 text-ink-muted">
                  {step.statusText}
                </span>
              </span>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
