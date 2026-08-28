import { useState } from 'react'
import { Link, useOutletContext, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { DocumentViewer } from '../../components/ui/DocumentViewer'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { portalService } from '../../services/portalService'
import { customFieldRows } from '../../utils/profileForm'
import { formatDate, formatDateTime } from '../../utils/format'

const NEXT_ACTION = {
  docs_pending: { label: 'Upload my documents', to: 'documents' },
  docs_pending_details: { label: 'Fill in my details', to: 'details' },
  docs_pending_review: { label: 'Review & submit', to: 'review' },
  docs_approved: { label: 'Review my offer letter', to: 'offer' },
}

export function PortalOverviewPage() {
  const { overview } = useOutletContext()
  const { token } = useParams()

  // Submitted and nothing sent back: this is their home while HR reviews. They
  // see the exact details and documents they submitted, and just wait.
  const awaitingReview =
    overview.currentStep === 'documents' &&
    overview.submittedForReview &&
    overview.documents.rejected === 0

  // Details come before uploads while they are still outstanding.
  const next = (() => {
    if (overview.stage !== 'docs_pending') return NEXT_ACTION[overview.stage]
    // A sent-back document is the most urgent thing, even after submitting.
    if (overview.documents.rejected > 0) {
      return { label: 'Re-upload the requested document', to: 'documents' }
    }
    if (awaitingReview) return null
    if (!overview.profileSubmitted) return NEXT_ACTION.docs_pending_details
    if (overview.readyToSubmit) return NEXT_ACTION.docs_pending_review
    return NEXT_ACTION.docs_pending
  })()

  if (overview.onboardingComplete) {
    return (
      <section className="cf-card overflow-hidden">
        <div className="bg-[#F3FCF7] px-6 py-10 text-center">
          <span className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded
            bg-accent-green text-white">
            <svg viewBox="0 0 24 24" className="h-7 w-7" fill="none" stroke="currentColor" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <h2 className="text-[20px] font-semibold text-ink">Onboarding Complete</h2>
          <p className="mx-auto mt-2 max-w-md text-[13.5px] leading-6 text-ink-muted">
            Every step is done - documents approved and your offer accepted on{' '}
            {formatDateTime(overview.completedAt)}. Your HR team will be in touch with joining details.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2.5">
            <Link to={`/portal/${token}/offer`}>
              <Button variant="secondary">View my offer letter</Button>
            </Link>
          </div>
        </div>
      </section>
    )
  }

  // Once the pack is with HR, the home page IS the record of what they submitted.
  if (awaitingReview) {
    return <SubmittedSummary overview={overview} token={token} />
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
            <div className="rounded border border-surface-line bg-surface-canvas p-4">
              <dt className="text-[11.5px] font-medium uppercase tracking-[0.12em] text-ink-muted">
                Your details
              </dt>
              <dd className="mt-1.5 text-[17px] font-semibold text-ink">
                {overview.profileSubmitted ? 'Submitted' : 'Not submitted'}
              </dd>
              <p className="mt-1 text-[12px] text-ink-muted">
                {overview.profileSubmitted ? (
                  <Link className="font-medium text-brand hover:underline" to={`/portal/${token}/details`}>
                    {overview.profileEditable ? 'Review or correct them' : 'Review what you submitted'}
                  </Link>
                ) : (
                  'Personal information for your employee record'
                )}
              </p>
            </div>
            <div className="rounded border border-surface-line bg-surface-canvas p-4">
              <dt className="text-[11.5px] font-medium uppercase tracking-[0.12em] text-ink-muted">
                {overview.documents.rejected > 0 ? 'Documents ready' : 'Documents uploaded'}
              </dt>
              <dd className="mt-1.5 text-[20px] font-semibold text-ink">
                {/* A sent-back document is no longer "ready", so it does not count. */}
                {overview.documents.required - overview.documents.missing - overview.documents.rejected}
                <span className="text-[14px] font-normal text-ink-muted">
                  {' '}/ {overview.documents.required}
                </span>
              </dd>
              <ProgressBar
                className="mt-2"
                value={overview.documents.required - overview.documents.missing - overview.documents.rejected}
                total={overview.documents.required}
                tone={
                  overview.documents.rejected > 0
                    ? 'amber'
                    : overview.documents.missing === 0
                      ? 'green'
                      : 'brand'
                }
                showLabel={false}
              />
            </div>
          </dl>
        )}
      </section>
    </div>
  )
}

/**
 * The candidate's home once their pack is with HR: a read-only record of the
 * details and documents they submitted, so they can look back at exactly what
 * they sent while they wait to hear from HR.
 */
function SubmittedSummary({ overview, token }) {
  const [viewing, setViewing] = useState(null)
  const profile = useAsync(() => portalService.profile(token), [token])
  const documents = useAsync(() => portalService.documents(token), [token])
  /* Only for the labels of admin-created fields; /api/meta is public. */
  const meta = useAsync(() => hrService.metadata(), [])

  const data = profile.data
  const docs = documents.data?.documents || []

  return (
    <div className="space-y-5">
      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      <section className="cf-card overflow-hidden">
        <div className="bg-[#F3FCF7] px-6 py-8 text-center">
          <span className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded
            bg-accent-green text-white">
            <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2.2"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          </span>
          <h2 className="text-[19px] font-semibold text-ink">Your documents are with HR</h2>
          <p className="mx-auto mt-2 max-w-md text-[13.5px] leading-6 text-ink-muted">
            {overview.submittedForReviewAt
              ? `Submitted on ${formatDateTime(overview.submittedForReviewAt)}. `
              : ''}
            We are reviewing everything now - there is nothing more for you to do. We will email you as
            soon as the next step is ready. You can look back at what you sent below.
          </p>
        </div>
      </section>

      <section className="cf-card p-5 sm:p-6">
        <h3 className="text-[14.5px] font-semibold text-ink">Your details</h3>
        {data ? (
          <dl className="mt-4 grid gap-x-6 gap-y-3 sm:grid-cols-2">
            <Row label="Full name (as per Aadhaar)" value={data.fullNameAsPerAadhaar} />
            <Row label="Father's name" value={data.fathersName} />
            <Row label="Personal email" value={data.personalEmail} />
            <Row label="Contact number" value={data.contactNumber} />
            <Row label="Alternate contact" value={data.alternateContactNumber} />
            <Row label="Date of birth" value={formatDate(data.dateOfBirth)} />
            <Row label="Gender" value={data.genderLabel} />
            <Row label="Blood group" value={data.bloodGroupLabel} />
            <Row label="Permanent address" value={data.permanentAddress} span />
            <Row label="Aadhaar number" value={data.aadhaarNumber} />
            <Row label="PAN number" value={data.panNumber} />
            <Row label="Emergency contact" value={data.emergencyContactName} />
            <Row label="Relation" value={data.emergencyContactRelationLabel} />
            <Row label="Emergency contact number" value={data.emergencyContactNumber} />
            {/* Anything an admin added - shown here too, so a candidate can check
                every answer they gave, not just the built-in ones. */}
            {customFieldRows(meta.data?.customCandidateFields, data.customFields)
              .map((row) => (
                <Row key={row.key} label={row.label} value={row.value} span={row.wide} />
              ))}
          </dl>
        ) : (
          <p className="mt-3 text-[13px] text-ink-muted">
            {profile.loading ? 'Loading your details…' : 'Your details are not available right now.'}
          </p>
        )}
      </section>

      <section className="cf-card overflow-hidden">
        <div className="px-5 py-4">
          <h3 className="text-[14.5px] font-semibold text-ink">Your documents</h3>
        </div>
        <ul className="divide-y divide-surface-line border-t border-surface-line">
          {docs.map((doc) => (
            <li key={doc.type} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
              <div className="min-w-0">
                <p className="flex flex-wrap items-center gap-2 text-[13.5px] font-medium text-ink">
                  {doc.typeLabel}
                  {doc.courseLabel && (
                    <span className="rounded bg-brand-tint px-2 py-0.5 text-[11px] font-medium text-brand">
                      {doc.courseLabel}
                    </span>
                  )}
                </p>
                <p className="mt-0.5 truncate text-[12.5px] text-ink-muted">
                  {doc.filename || 'Not uploaded'}
                </p>
              </div>
              {doc.downloadUrl && (
                <Button variant="subtle" size="sm" onClick={() => setViewing(doc)}>
                  View
                </Button>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function Row({ label, value, span }) {
  return (
    <div className={span ? 'sm:col-span-2' : undefined}>
      <dt className="text-[11.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-[13.5px] text-ink-body">{value || '—'}</dd>
    </div>
  )
}
