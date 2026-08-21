import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { AuditTimeline } from '../../components/hr/AuditTimeline'
import { CandidateDetailsPanel } from '../../components/hr/CandidateDetailsPanel'
import { DocumentReviewPanel } from '../../components/hr/DocumentReviewPanel'
import { PortalLinkCard } from '../../components/hr/PortalLinkCard'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
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
            <CandidateDetailsPanel profile={detail.profile} />
          </section>
          <PortalLinkCard candidate={candidate} onChanged={load} />
        </div>
      )}

      {tab === 'documents' && (
        <section className="cf-card overflow-hidden">
          {candidate.stage === 'docs_pending' && !candidate.submittedForReviewAt && (
            <p className="border-b border-surface-line bg-[#FFF4EC] px-5 py-2.5 text-[12.5px] text-[#B94A18]">
              The candidate has not submitted their pack yet, so this is still a work in progress.
            </p>
          )}
          <DocumentReviewPanel
            documents={detail.documents}
            readOnly={candidate.stage !== 'docs_pending'}
            onChanged={() => load()}
          />
        </section>
      )}

      {tab === 'audit' && (
        <section className="cf-card p-5">
          <h2 className="mb-4 text-[15px] font-semibold text-ink">Audit trail</h2>
          <AuditTimeline events={detail.auditTrail} />
        </section>
      )}
    </>
  )
}
