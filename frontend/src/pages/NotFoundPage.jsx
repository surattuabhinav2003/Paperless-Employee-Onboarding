import { Link } from 'react-router-dom'
import { Button } from '../components/ui/Button'
import { Logo } from '../components/ui/Logo'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-surface-canvas px-4 text-center">
      <Logo subtitle="Onboarding" />
      <div>
        <h1 className="text-[22px] font-semibold text-ink">This page does not exist</h1>
        <p className="mt-2 max-w-md text-[13.5px] leading-6 text-ink-muted">
          Check the link you followed. Candidates should use the secure portal link from their Neutara
          invitation email.
        </p>
      </div>
      <Link to="/login">
        <Button variant="secondary">Go to HR sign in</Button>
      </Link>
    </div>
  )
}
