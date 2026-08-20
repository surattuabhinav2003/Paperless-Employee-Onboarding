import { Link, useParams } from 'react-router-dom'
import { Button } from '../ui/Button'

/**
 * Shown when the candidate opens a stage they have not unlocked yet. The
 * backend refuses the underlying API too - this only explains why.
 */
export function LockedPanel({ title, reason, backLabel = 'Back to my onboarding' }) {
  const { token } = useParams()
  return (
    <section className="cf-card p-8 text-center">
      <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-surface-offwhite
        text-ink-muted ring-1 ring-surface-line">
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M7 11V8a5 5 0 0 1 10 0v3M5 11h14v10H5V11Z" />
        </svg>
      </span>
      <h2 className="text-[17px] font-semibold text-ink">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-[13.5px] leading-6 text-ink-muted">{reason}</p>
      <div className="mt-6">
        <Link to={`/portal/${token}`}>
          <Button variant="secondary">{backLabel}</Button>
        </Link>
      </div>
    </section>
  )
}
