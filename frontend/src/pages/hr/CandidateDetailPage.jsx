import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { AddRequiredDocumentModal } from '../../components/hr/AddRequiredDocumentModal'
import { AuditTimeline } from '../../components/hr/AuditTimeline'
import { CandidateDetailsEditModal } from '../../components/hr/CandidateDetailsEditModal'
import { CandidateDetailsPanel } from '../../components/hr/CandidateDetailsPanel'
import { CandidateJobEditModal } from '../../components/hr/CandidateJobEditModal'
import { DocumentReviewPanel } from '../../components/hr/DocumentReviewPanel'
import { PortalLinkCard } from '../../components/hr/PortalLinkCard'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useAsync } from '../../hooks/useAsync'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatDate, initialsOf } from '../../utils/format'
import { hueOf } from '../../utils/avatar'
import {
  awaitingReviewCount,
  documentProgressMeta,
  offerStatusMeta,
  pipelineStatusMeta,
} from '../../utils/status'

const TABS = [
  { key: 'details', label: 'Details' },
  { key: 'documents', label: 'Documents' },
  { key: 'audit', label: 'Audit trail' },
]

/**
 * Everything HR needs on one candidate, as a full page rather than a cramped
 * slide-over: identity and progress up top, then the working tabs.
 */
export function CandidateDetailPage() {
  const { candidateId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  /* Back to the filtered list it was opened from, not a bare one. */
  const backTo = location.state?.from || '/candidates'
  const toast = useToast()

  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [tab, setTab] = useState('details')
  const [notifying, setNotifying] = useState(false)
  const [addDocOpen, setAddDocOpen] = useState(false)
  const [editingProfile, setEditingProfile] = useState(false)
  const [editingJob, setEditingJob] = useState(false)
  const metadata = useAsync(() => hrService.metadata(), [])

  const notifyCandidate = async () => {
    setNotifying(true)
    try {
      const result = await hrService.notifyCandidate(candidateId)
      if (result.outcome === 'reupload') {
        toast.success('Candidate notified',
          `We emailed ${result.emailedTo} to re-upload ${result.rejectedCount} document${result.rejectedCount === 1 ? '' : 's'}.`)
      } else {
        toast.success('Candidate notified', `We emailed ${result.emailedTo} that their documents are approved.`)
      }
      load()
    } catch (err) {
      toast.apiError(err, 'Could not notify the candidate')
    } finally {
      setNotifying(false)
    }
  }

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      setDetail(await hrService.candidate(candidateId))
    } catch (err) {
      setError(err)
      if (err.code === 'RESOURCE_NOT_FOUND') {
        toast.error('Candidate not found', 'They may have been removed.')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    setTab('details')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidateId])

  if (loading && !detail) return <LoadingState label="Loading candidate" />

  if (error && !detail) {
    return (
      <div className="cf-card">
        <ErrorState
          message={error.message}
          action={
            <div className="flex flex-wrap justify-center gap-2.5">
              <Button onClick={load}>Try again</Button>
              <Button variant="secondary" onClick={() => navigate(backTo)}>
                Back to records
              </Button>
            </div>
          }
        />
      </div>
    )
  }

  const candidate = detail.candidate
  const status = pipelineStatusMeta(candidate)
  const offer = offerStatusMeta(candidate.offerStatus)
  const awaitingReview = awaitingReviewCount(candidate)
  const docs = documentProgressMeta(candidate)

  // HR's one-shot "done reviewing" action, once the pack is in and there is an
  // outcome to send: documents to re-upload, or approval. When nothing is
  // rejected and every mandatory document is reviewed, this button is also what
  // performs the approval itself - not the act of reviewing the last document -
  // so review stays reopenable right up until HR commits here.
  //
  // Approval is deliberately not repeatable: there is no resend, because HR is
  // expected to check everything before committing. Once approved the banner
  // only reports the outcome.
  const rejectedCount = (detail.documents || []).filter((d) => d.status === 'rejected').length
  const approved = candidate.stage !== 'docs_pending'
  const canNotifyReupload =
    Boolean(candidate.submittedForReviewAt) && candidate.stage === 'docs_pending' && rejectedCount > 0
  const canApprove = !approved && rejectedCount === 0 && candidate.readyForApproval
  const showNotify = canNotifyReupload || canApprove || approved

  return (
    <>
      <Link
        to={backTo}
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] font-medium text-ink-muted
          transition hover:text-brand"
      >
        <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 12H5m7-7-7 7 7 7" />
        </svg>
        Back to records
      </Link>

      <section className="c-panel r-head">
        <div className="r-id">
          <span className={`r-av c-ring--h${hueOf(candidate.name)}`}>
            {initialsOf(candidate.name)}
          </span>

          <div className="r-id-text">
            <div className="r-name-row">
              <h1>{candidate.name}</h1>
              <StatusPill label={status.label} tone={status.tone} />
              <button
                type="button"
                onClick={() => setEditingJob(true)}
                title="Edit name, role and department"
                className="rounded p-1 text-ink-muted transition hover:bg-surface-canvas hover:text-brand"
                aria-label="Edit candidate name, role and department"
              >
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
                  strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
                </svg>
              </button>
            </div>
            <p className="r-contact">
              {candidate.email} &middot; {candidate.role} &middot; {candidate.department}
            </p>

            {/* Three milestones, each labelled - rather than three timestamps
                run together in one muted sentence. */}
            <div className="r-track">
              <div className="r-step">
                <span className="r-step-label">Created</span>
                <span className="r-step-value">{formatDate(candidate.createdAt)}</span>
              </div>
              <div className="r-step">
                <span className="r-step-label">Submitted</span>
                <span className={`r-step-value${candidate.submittedForReviewAt ? '' : ' is-waiting'}`}>
                  {candidate.submittedForReviewAt
                    ? formatDate(candidate.submittedForReviewAt)
                    : 'Waiting'}
                </span>
              </div>
              <div className="r-step">
                <span className="r-step-label">Completed</span>
                <span className={`r-step-value${candidate.completedAt ? '' : ' is-waiting'}`}>
                  {candidate.completedAt ? formatDate(candidate.completedAt) : 'Not yet'}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="r-meta">
          <div>
            <div className="r-meta-row">
              <span className="r-meta-label">Documents</span>
              <span className="r-meta-figure">{docs.uploaded}/{docs.total}</span>
            </div>
            <div style={{ marginTop: 8 }}>
              <ProgressBar value={docs.uploaded} total={docs.total} tone={docs.tone} showLabel={false} />
            </div>
            <span className="r-meta-note" style={{ marginTop: 6, display: 'block' }}>{docs.caption}</span>
          </div>
          <div className="r-meta-row" style={{ borderTop: '1px solid var(--line)', paddingTop: 12 }}>
            <span className="r-meta-label">Offer</span>
            <StatusPill label={offer.label} tone={offer.tone} />
          </div>
        </div>
      </section>

      {awaitingReview > 0 && (
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded
          border border-brand/30 bg-brand-tint/60 px-5 py-3.5">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded
              bg-brand text-white">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor"
                strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0" />
              </svg>
            </span>
            <div>
              <p className="text-[13.5px] font-semibold text-ink">
                {awaitingReview === 1
                  ? '1 document is waiting for your review'
                  : `${awaitingReview} documents are waiting for your review`}
              </p>
              <p className="mt-0.5 text-[12.5px] text-ink-muted">
                {candidate.submittedForReviewAt
                  ? 'The candidate has submitted their pack. Verify or reject each document to move them on.'
                  : 'The candidate is still finishing their pack, but these are ready to look at.'}
              </p>
            </div>
          </div>
          <Button size="sm" onClick={() => setTab('documents')}>
            Review documents
          </Button>
        </section>
      )}

      <nav className="mb-5 flex flex-wrap gap-1 border-b border-surface-line">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setTab(item.key)}
            className={`-mb-px border-b-2 px-4 py-2.5 text-[13.5px] font-medium transition ${
              tab === item.key
                ? 'border-brand text-brand'
                : 'border-transparent text-ink-muted hover:border-surface-line hover:text-ink'
            }`}
          >
            {item.label}
            {item.key === 'documents' && awaitingReview > 0 && (
              <span className="ml-2 rounded-[3px] bg-brand px-1.5 py-0.5 text-[10px] font-semibold text-white">
                {awaitingReview}
              </span>
            )}
          </button>
        ))}
      </nav>

      {tab === 'details' && (
        <div className="grid items-start gap-5 xl:grid-cols-[1.4fr_1fr]">
          <section className="cf-card p-5">
            <CandidateDetailsPanel profile={detail.profile} onEdit={() => setEditingProfile(true)}
              customFields={metadata.data?.customCandidateFields} />
          </section>
          <PortalLinkCard candidate={candidate} onChanged={load} />
        </div>
      )}

      <CandidateDetailsEditModal
        open={editingProfile}
        onClose={() => setEditingProfile(false)}
        candidateId={candidateId}
        candidateName={candidate.name}
        profile={detail.profile}
        meta={metadata.data}
        onSaved={() => { setEditingProfile(false); load() }}
      />

      <CandidateJobEditModal
        open={editingJob}
        onClose={() => setEditingJob(false)}
        candidate={candidate}
        onSaved={() => { setEditingJob(false); load() }}
      />

      {tab === 'documents' && (
        <div className="space-y-4">
          {showNotify && (
            <section className="flex flex-wrap items-center justify-between gap-3 rounded border
              border-brand/30 bg-brand-tint/50 px-5 py-3.5">
              <div>
                <p className="text-[13.5px] font-semibold text-ink">
                  {canNotifyReupload
                    ? `Ready to send ${rejectedCount} document${rejectedCount === 1 ? '' : 's'} back to the candidate`
                    : approved
                      ? 'Candidate approved'
                      : 'Every mandatory document is reviewed'}
                </p>
                <p className="mt-0.5 text-[12.5px] text-ink-muted">
                  {canNotifyReupload
                    ? 'Finish reviewing, then send one email listing everything they need to re-upload.'
                    : approved
                      ? 'They have been emailed, their documents are locked, and the offer letter is unlocked.'
                      : 'Approving marks them verified, emails them, and unlocks the offer letter. '
                        + 'Document review locks for good, so re-verify anything you want another look at first.'}
                </p>
              </div>
              {/* Nothing to act on once approved - it is a one-shot decision. */}
              {!approved && (
                <Button size="sm" onClick={notifyCandidate} disabled={notifying}>
                  {notifying
                    ? 'Sending…'
                    : canNotifyReupload
                      ? 'Email candidate to re-upload'
                      : 'Approve & email candidate'}
                </Button>
              )}
            </section>
          )}

          <section className="cf-card overflow-hidden">
            {candidate.stage === 'docs_pending' && (
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-line
                px-5 py-3.5">
                <div>
                  <h2 className="text-[13.5px] font-semibold text-ink">Requested documents</h2>
                  <p className="mt-0.5 text-[12px] text-ink-muted">
                    Click Mandatory or Optional on any row to change it.
                  </p>
                </div>
                <Button variant="secondary" size="sm" onClick={() => setAddDocOpen(true)}>
                  Add document requirement
                </Button>
              </div>
            )}
            {candidate.stage === 'docs_pending' && !candidate.submittedForReviewAt && (
              <p className="border-b border-surface-line bg-[#FFF4EC] px-5 py-2.5 text-[12.5px] text-[#B94A18]">
                The candidate has not submitted their pack yet, so this is still a work in progress.
              </p>
            )}
            <DocumentReviewPanel
              documents={detail.documents}
              candidateId={candidateId}
              readOnly={candidate.stage !== 'docs_pending'}
              onChanged={() => load()}
            />
          </section>
        </div>
      )}

      <AddRequiredDocumentModal
        open={addDocOpen}
        onClose={() => setAddDocOpen(false)}
        candidateId={candidateId}
        documentTypes={metadata.data?.documentTypes || []}
        existingTypes={(detail.requiredDocuments || []).map((rd) => rd.type)}
        onAdded={() => {
          setAddDocOpen(false)
          load()
        }}
      />

      {tab === 'audit' && (
        <section className="cf-card p-5">
          <h2 className="mb-4 text-[15px] font-semibold text-ink">Audit trail</h2>
          <AuditTimeline events={detail.auditTrail} />
        </section>
      )}
    </>
  )
}
