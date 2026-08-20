import { Link, useOutletContext, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { formatDateTime } from '../../utils/format'

const NEXT_ACTION = {
  docs_pending: { label: 'Upload my documents', to: 'documents' },
  docs_pending_details: { label: 'Fill in my details', to: 'details' },
  docs_pending_review: { label: 'Review & submit', to: 'review' },
  docs_pending_submitted: { label: 'View what I submitted', to: 'review' },
  docs_approved: { label: 'Review my offer letter', to: 'offer' },
  offer_accepted: { label: 'Sign my bond', to: 'bond' },
}

export function PortalOverviewPage() {
  const { overview } = useOutletContext()
  const { token } = useParams()
  // Details come before uploads while they are still outstanding.
  const next = (() => {
    if (overview.stage !== 'docs_pending') return NEXT_ACTION[overview.stage]
    if (overview.submittedForReview) return NEXT_ACTION.docs_pending_submitted
    if (!overview.profileSubmitted) return NEXT_ACTION.docs_pending_details
    if (overview.readyToSubmit) return NEXT_ACTION.docs_pending_review
    return NEXT_ACTION.docs_pending
  })()

  if (overview.onboardingComplete) {
    return (
      <section className="cf-card overflow-hidden">
        <div className="bg-[#F3FCF7] px-6 py-10 text-center">
          <span className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full
            bg-accent-green text-white">
            <svg viewBox="0 0 24 24" className="h-7 w-7" fill="none" stroke="currentColor" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <h2 className="text-[20px] font-semibold text-ink">Onboarding Complete</h2>
          <p className="mx-auto mt-2 max-w-md text-[13.5px] leading-6 text-ink-muted">
            Every step is done - documents approved, offer accepted and your bond signed on{' '}
            {formatDateTime(overview.completedAt)}. Your HR team will be in touch with joining details.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2.5">
            <Link to={`/portal/${token}/bond`}>
              <Button variant="secondary">View my signed bond</Button>
            </Link>
            <Link to={`/portal/${token}/documents`}>
              <Button variant="ghost">Review my documents</Button>
            </Link>
          </div>
        </div>
      </section>
    )
  }

  return (
    <div className="space-y-5">
      <section className="cf-card p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="text-[16px] font-semibold text-ink">Your next step</h2>
            <p className="mt-1 max-w-lg text-[13px] leading-6 text-ink-muted">{overview.message}</p>
          </div>
          {next && (
            <Link to={`/portal/${token}/${next.to}`}>
              <Button>{next.label}</Button>
            </Link>
          )}
        </div>

        {/* Only what the candidate still has to do - verification counts are
            HR's side of the process. */}
        {overview.currentStep === 'documents' && (
          <dl className="mt-6 grid gap-4 sm:grid-cols-2">
            <div className="rounded-card border border-surface-line bg-surface-offwhite/60 p-4">
              <dt className="text-[11.5px] font-medium uppercase tracking-[0.12em] text-ink-muted">
                Your details
              </dt>
              <dd className="mt-1.5 text-[17px] font-semibold text-ink">
                {overview.profileSubmitted ? 'Submitted' : 'Not submitted'}
              </dd>
              <p className="mt-1 text-[12px] text-ink-muted">
                {overview.profileSubmitted ? (
                  <Link className="font-medium text-brand hover:underline" to={`/portal/${token}/details`}>
                    Review or correct them
                  </Link>
                ) : (
                  'Personal information for your employee record'
                )}
              </p>
            </div>
            <div className="rounded-card border border-surface-line bg-surface-offwhite/60 p-4">
              <dt className="text-[11.5px] font-medium uppercase tracking-[0.12em] text-ink-muted">
                Documents uploaded
              </dt>
              <dd className="mt-1.5 text-[20px] font-semibold text-ink">
                {overview.documents.required - overview.documents.missing}
                <span className="text-[14px] font-normal text-ink-muted">
                  {' '}/ {overview.documents.required}
                </span>
              </dd>
              <ProgressBar
                className="mt-2"
                value={overview.documents.required - overview.documents.missing}
                total={overview.documents.required}
                tone={overview.documents.missing === 0 ? 'green' : 'brand'}
                showLabel={false}
              />
            </div>
          </dl>
        )}
      </section>

    </div>
  )
}
