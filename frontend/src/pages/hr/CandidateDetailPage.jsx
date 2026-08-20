import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AuditTimeline } from '../../components/hr/AuditTimeline'
import { CandidateDetailsPanel } from '../../components/hr/CandidateDetailsPanel'
import { DocumentReviewPanel } from '../../components/hr/DocumentReviewPanel'
import { OfferBondPanel } from '../../components/hr/OfferBondPanel'
import { PortalLinkCard } from '../../components/hr/PortalLinkCard'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatDateTime, initialsOf } from '../../utils/format'
import { bondStatusMeta, offerStatusMeta, stageMeta } from '../../utils/status'

const TABS = [
  { key: 'details', label: 'Details' },
  { key: 'documents', label: 'Documents' },
  { key: 'offer-bond', label: 'Offer & Bond' },
  { key: 'audit', label: 'Audit trail' },
]

/**
 * Everything HR needs on one candidate, as a full page rather than a cramped
 * slide-over: identity and progress up top, then the working tabs.
 */
export function CandidateDetailPage() {
  const { candidateId } = useParams()
  const navigate = useNavigate()
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
              <Button variant="secondary" onClick={() => navigate('/candidates')}>
                Back to pipeline
              </Button>
            </div>
          }
        />
      </div>
    )
  }

  const candidate = detail.candidate
  const stage = stageMeta(candidate.stage)
  const offer = offerStatusMeta(candidate.offerStatus)
  const bond = bondStatusMeta(candidate.bondStatus)

  return (
    <>
      <Link
        to="/candidates"
        className="mb-4 inline-flex items-center gap-1.5 text-[13px] font-medium text-ink-muted
          transition hover:text-brand"
      >
        <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 12H5m7-7-7 7 7 7" />
        </svg>
        Back to pipeline
      </Link>

      <section className="cf-card mb-5 p-5 sm:p-6">
        <div className="flex flex-wrap items-start gap-4">
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-tint
            text-[17px] font-semibold text-brand">
            {initialsOf(candidate.name)}
          </span>

          <div className="min-w-[240px] flex-1">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-[22px] font-semibold tracking-[-0.01em] text-ink">{candidate.name}</h1>
              <StatusPill label={stage.label} tone={stage.tone} />
            </div>
            <p className="mt-1 text-[13.5px] text-ink-muted">
              {candidate.email} &middot; {candidate.role} &middot; {candidate.department}
            </p>
            <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-[12px] text-ink-muted">
              <span>Created {formatDateTime(candidate.createdAt)}</span>
              <span>
                {candidate.submittedForReviewAt
                  ? `Submitted ${formatDateTime(candidate.submittedForReviewAt)}`
                  : 'Not submitted by the candidate yet'}
              </span>
              {candidate.completedAt && <span>Completed {formatDateTime(candidate.completedAt)}</span>}
            </div>
          </div>

          <dl className="grid w-full max-w-[300px] gap-2.5 text-[12.5px]">
            <div>
              <dt className="mb-1 text-ink-muted">Documents verified</dt>
              <dd>
                <ProgressBar
                  value={candidate.documentsVerified}
                  total={candidate.documentsRequired}
                  tone={
                    candidate.documentsVerified === candidate.documentsRequired
                      ? 'green'
                      : candidate.documentsRejected > 0
                        ? 'amber'
                        : 'brand'
                  }
                />
              </dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-ink-muted">Offer</dt>
              <dd><StatusPill label={offer.label} tone={offer.tone} /></dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-ink-muted">Bond</dt>
              <dd><StatusPill label={bond.label} tone={bond.tone} /></dd>
            </div>
          </dl>
        </div>
      </section>

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
          </button>
        ))}
      </nav>

      {tab === 'details' && (
        <div className="grid gap-5 xl:grid-cols-[1.4fr_1fr]">
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

      {tab === 'offer-bond' && (
        <OfferBondPanel
          candidate={candidate}
          offer={detail.offer}
          bond={detail.bond}
          onChanged={load}
        />
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
