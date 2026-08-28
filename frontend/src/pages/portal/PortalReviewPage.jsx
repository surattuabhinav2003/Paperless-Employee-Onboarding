import { useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { DocumentViewer } from '../../components/ui/DocumentViewer'
import { ErrorState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/ui/Spinner'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { portalService } from '../../services/portalService'
import { customFieldRows } from '../../utils/profileForm'
import { formatDate, formatDateTime } from '../../utils/format'

/**
 * The last stop before HR sees anything: the candidate checks their details and
 * documents, then submits. The backend refuses the submit until every mandatory
 * item is in, and refuses edits afterwards.
 */
export function PortalReviewPage() {
  const { overview, reloadOverview, token } = useOutletContext()
  const navigate = useNavigate()
  const toast = useToast()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [viewing, setViewing] = useState(null)

  const profile = useAsync(() => portalService.profile(token), [token])
  const documents = useAsync(() => portalService.documents(token), [token])
  /* Only for the labels of admin-created fields; /api/meta is public. */
  const meta = useAsync(() => hrService.metadata(), [])

  if ((profile.loading && !profile.data) || (documents.loading && !documents.data)) {
    return <LoadingState label="Loading your onboarding pack" />
  }
  if (documents.error && !documents.data) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your pack" message={documents.error.message} />
      </div>
    )
  }

  const submitted = overview.submittedForReview
  const docs = documents.data?.documents || []

  const submit = async () => {
    setSubmitting(true)
    try {
      await portalService.submitForReview(token)
      setConfirmOpen(false)
      await reloadOverview()
      // Land back on the home page - that is where their status and any document
      // HR sends back will show, so they are never stuck on this review screen.
      navigate(`/portal/${token}`, { replace: true })
      toast.success('Submitted to HR', 'We are reviewing your documents - we will email you when the next step is ready.')
    } catch (error) {
      setConfirmOpen(false)
      toast.apiError(error, 'We could not submit your pack')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-5">
      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      <section className="cf-card p-5 sm:p-6">
        <h2 className="text-[16px] font-semibold text-ink">
          {submitted ? 'What you submitted' : 'Check everything before you submit'}
        </h2>
        <p className="mt-1 max-w-2xl text-[13px] leading-6 text-ink-muted">
          {submitted
            ? `Submitted on ${formatDateTime(overview.submittedForReviewAt)}. We are reviewing your `
              + 'documents. If HR needs any of them re-uploaded, you will be able to do it here - and '
              + 'we will email you as soon as the next step is ready.'
            : 'Read through your details and documents. Once you submit, they go to HR and you will not be '
              + 'able to change them here.'}
        </p>
      </section>

      <section className="cf-card p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h3 className="text-[14.5px] font-semibold text-ink">Your details</h3>
          {!submitted && (
            <Link to={`/portal/${token}/details`}>
              <Button variant="secondary" size="sm">Edit details</Button>
            </Link>
          )}
        </div>

        {profile.data ? (
          <dl className="mt-4 grid gap-x-6 gap-y-3 sm:grid-cols-2">
            <Row label="Full name (as per Aadhaar)" value={profile.data.fullNameAsPerAadhaar} />
            <Row label="Father's name" value={profile.data.fathersName} />
            <Row label="Personal email" value={profile.data.personalEmail} />
            <Row label="Contact number" value={profile.data.contactNumber} />
            <Row label="Alternate contact" value={profile.data.alternateContactNumber} />
            <Row label="Date of birth" value={formatDate(profile.data.dateOfBirth)} />
            <Row label="Gender" value={profile.data.genderLabel} />
            <Row label="Blood group" value={profile.data.bloodGroupLabel} />
            <Row label="Permanent address" value={profile.data.permanentAddress} span />
            <Row label="Aadhaar number" value={profile.data.aadhaarNumber} />
            <Row label="PAN number" value={profile.data.panNumber} />
            <Row label="Emergency contact" value={profile.data.emergencyContactName} />
            <Row label="Relation" value={profile.data.emergencyContactRelationLabel} />
            <Row label="Emergency contact number" value={profile.data.emergencyContactNumber} />
            {/* Anything an admin added - shown here too, so a candidate can check
                every answer they gave, not just the built-in ones. */}
            {customFieldRows(meta.data?.customCandidateFields, profile.data.customFields)
              .map((row) => (
                <Row key={row.key} label={row.label} value={row.value} span={row.wide} />
              ))}
          </dl>
        ) : (
          <p className="mt-3 text-[13px] text-ink-muted">Your details are not available right now.</p>
        )}
      </section>

      <section className="cf-card overflow-hidden">
        <div className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
          <h3 className="text-[14.5px] font-semibold text-ink">Your documents</h3>
          {!submitted && (
            <Link to={`/portal/${token}/documents`}>
              <Button variant="secondary" size="sm">Edit documents</Button>
            </Link>
          )}
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
                  {!doc.mandatory && (
                    <span className="text-[10.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">
                      Optional
                    </span>
                  )}
                </p>
                <p className="mt-0.5 truncate text-[12.5px] text-ink-muted">
                  {doc.filename || 'Not uploaded'}
                </p>
              </div>
              {doc.downloadUrl ? (
                <Button variant="subtle" size="sm" onClick={() => setViewing(doc)}>
                  View
                </Button>
              ) : (
                <span className="text-[12px] font-medium text-accent-orange">Missing</span>
              )}
            </li>
          ))}
        </ul>
      </section>

      {!submitted && (
        <section className="cf-card p-5 sm:p-6">
          <h3 className="text-[14.5px] font-semibold text-ink">Everything looks complete</h3>
          <p className="mt-1 text-[13px] leading-6 text-ink-muted">
            Submitting sends your details and documents to HR for review. You will not be able to change them
            afterwards, so check them once more above.
          </p>
          <div className="mt-5">
            <Button size="lg" onClick={() => setConfirmOpen(true)}>
              Submit to HR
            </Button>
          </div>
        </section>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="Submit your onboarding pack?"
        confirmLabel="Yes, submit to HR"
        loading={submitting}
        onConfirm={submit}
        onClose={() => setConfirmOpen(false)}
        message="Your details and documents go to HR for review. You will not be able to change them here
          afterwards - HR will ask you if anything needs correcting."
      />
    </div>
  )
}

function Row({ label, value, span = false }) {
  return (
    <div className={span ? 'sm:col-span-2' : undefined}>
      <dt className="text-[11.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-[13.5px] text-ink-body">
        {value === null || value === undefined || value === '' ? (
          <span className="text-ink-muted">Not provided</span>
        ) : (
          value
        )}
      </dd>
    </div>
  )
}
