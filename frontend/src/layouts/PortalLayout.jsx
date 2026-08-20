import { Navigate, Outlet, useLocation, useParams } from 'react-router-dom'
import { ProgressTracker } from '../components/portal/ProgressTracker'
import { ErrorState } from '../components/ui/EmptyState'
import { Logo } from '../components/ui/Logo'
import { LoadingState } from '../components/ui/Spinner'
import { StatusPill } from '../components/ui/StatusPill'
import { useAsync } from '../hooks/useAsync'
import { portalService } from '../services/portalService'
import { formatDate } from '../utils/format'
import { stageMeta } from '../utils/status'

/**
 * Which sub-pages belong to each step. Anything else is a stage the candidate has
 * either finished or not reached, so they are sent back to the step they are on
 * instead of being shown a locked or stale page.
 */
const STEP_PAGES = {
  documents: ['details', 'documents', 'review'],
  offer: ['offer'],
  bond: ['bond'],
}

function pageFrom(pathname) {
  const last = pathname.replace(/\/+$/, '').split('/').pop()
  return ['details', 'documents', 'review', 'offer', 'bond'].includes(last) ? last : null
}

/**
 * Chrome for the candidate portal: identity, live stage, and the three-step
 * tracker. The overview response drives everything, and is reloaded after any
 * action so the gates stay in sync with the backend.
 */
export function PortalLayout() {
  const { token } = useParams()
  const location = useLocation()
  const { data: overview, error, loading, reload } = useAsync(
    () => portalService.overview(token),
    [token],
  )

  if (loading && !overview) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface-offwhite">
        <LoadingState label="Opening your onboarding portal" />
      </div>
    )
  }

  if (error && !overview) {
    const expired = error.code === 'PORTAL_TOKEN_EXPIRED'
    return (
      <div className="min-h-screen bg-surface-offwhite">
        <PortalTopBar />
        <div className="mx-auto max-w-xl px-4 py-16">
          <div className="cf-card">
            <ErrorState
              title={expired ? 'This onboarding link has expired' : 'This onboarding link is not valid'}
              message={
                error.message ||
                'Please use the link from your CloudFuze invitation email, or contact your HR contact for a fresh link.'
              }
            />
          </div>
        </div>
      </div>
    )
  }

  const meta = stageMeta(overview.stage)
  const page = pageFrom(location.pathname)
  const allowedPages = STEP_PAGES[overview.currentStep] || []

  // A stale or not-yet-reachable page redirects to the step they are on.
  if (page && !allowedPages.includes(page)) {
    return <Navigate to={`/portal/${token}`} replace />
  }

  // Review is the last stop before HR: only reachable once every required detail
  // and document is in (or afterwards, to look back at what was submitted).
  if (page === 'review' && !overview.readyToSubmit && !overview.submittedForReview) {
    return <Navigate to={`/portal/${token}/documents`} replace />
  }

  return (
    <div className="min-h-screen bg-surface-offwhite">
      <PortalTopBar candidateName={overview.candidateName} stageLabel={meta.label} tone={meta.tone} />

      <div className="mx-auto w-full max-w-4xl px-4 pb-16 pt-6 sm:px-6">
        <section className="mb-6 overflow-hidden rounded-card bg-gradient-to-br from-brand-ink via-[#021A63]
          to-brand p-6 text-white shadow-card sm:p-7">
          <p className="text-[11.5px] font-medium uppercase tracking-[0.16em] text-white/60">
            {overview.role} &middot; {overview.department}
          </p>
          <h1 className="mt-2 text-[22px] font-semibold leading-tight sm:text-[26px]">
            {overview.headline}
          </h1>
          <p className="mt-2.5 max-w-2xl text-[13.5px] leading-6 text-white/80">{overview.message}</p>
          {overview.linkExpiresAt && !overview.onboardingComplete && (
            <p className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-white/12 px-3 py-1
              text-[11.5px] text-white/85 ring-1 ring-inset ring-white/20">
              <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="9" />
                <path d="M12 7v5l3 2" />
              </svg>
              This secure link stays active until {formatDate(overview.linkExpiresAt)}
            </p>
          )}
        </section>

        <div className="mb-6">
          <ProgressTracker steps={overview.steps} />
        </div>

        <Outlet context={{ overview, reloadOverview: reload, token }} />

        <footer className="mt-10 flex flex-col items-center gap-1 text-center text-[12px] text-ink-muted">
          <p>
            Need help? Contact{' '}
            <a className="font-medium text-brand hover:underline" href={`mailto:${overview.supportContact}`}>
              {overview.supportContact}
            </a>
          </p>
          <p className="text-[11px]">
            This link is personal to you. CloudFuze will never ask for your password or payment details.
          </p>
        </footer>
      </div>
    </div>
  )
}

function PortalTopBar({ candidateName, stageLabel, tone }) {
  return (
    <header className="sticky top-0 z-20 border-b border-surface-line bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-4xl items-center gap-3 px-4 sm:px-6">
        <Logo subtitle="Onboarding" />
        <div className="ml-auto flex items-center gap-3">
          {candidateName && (
            <span className="hidden text-right sm:block">
              <span className="block text-[13px] font-medium text-ink">{candidateName}</span>
              <span className="block text-[11px] text-ink-muted">Candidate</span>
            </span>
          )}
          {stageLabel && <StatusPill label={stageLabel} tone={tone} />}
        </div>
      </div>
    </header>
  )
}
