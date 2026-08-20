import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '../../components/PageHeader'
import { AuditTimeline } from '../../components/hr/AuditTimeline'
import { OfferBondPanel } from '../../components/hr/OfferBondPanel'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { LoadingState, SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { formatRelative, initialsOf } from '../../utils/format'
import { bondStatusMeta, offerStatusMeta, stageMeta } from '../../utils/status'

const FILTERS = [
  { key: 'needs_offer', label: 'Needs offer' },
  { key: 'awaiting_signature', label: 'Awaiting signature' },
  { key: 'all', label: 'All' },
]

/** Offers & Bonds: publish documents and track viewing, acceptance and signing. */
export function OffersBondsPage() {
  const toast = useToast()
  const [filter, setFilter] = useState('all')
  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [detailError, setDetailError] = useState(null)

  const list = useAsync(() => hrService.candidates({ size: 100 }), [])
  const candidates = list.data?.content || []

  const filtered = useMemo(() => {
    if (filter === 'needs_offer') {
      return candidates.filter((candidate) => candidate.stage === 'docs_approved' && !candidate.offerPrepared)
    }
    if (filter === 'awaiting_signature') {
      return candidates.filter(
        (candidate) => candidate.stage === 'offer_accepted' && candidate.bondStatus !== 'signed',
      )
    }
    return candidates
  }, [candidates, filter])

  useEffect(() => {
    if (!selectedId && filtered.length) setSelectedId(filtered[0].id)
  }, [filtered, selectedId])

  const loadDetail = async (candidateId) => {
    if (!candidateId) return
    setLoadingDetail(true)
    setDetailError(null)
    try {
      setDetail(await hrService.candidate(candidateId))
    } catch (error) {
      setDetailError(error)
      toast.apiError(error, 'Could not load the candidate')
    } finally {
      setLoadingDetail(false)
    }
  }

  useEffect(() => {
    if (selectedId) loadDetail(selectedId)
    else setDetail(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const refresh = async () => {
    await loadDetail(selectedId)
    list.reload().catch(() => {})
  }

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Offers & bonds"
        subtitle="Publish offer letters and bond documents, then track viewing, acceptance and SignatureOne signing."
      />

      <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
        <section className="cf-card overflow-hidden">
          <div className="border-b border-surface-line p-3">
            <div className="flex gap-1 rounded bg-surface-offwhite p-1">
              {FILTERS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setFilter(tab.key)}
                  className={`flex-1 whitespace-nowrap rounded px-2 py-1.5 text-[12px] font-medium transition ${
                    filter === tab.key ? 'bg-white text-brand shadow-sm' : 'text-ink-muted hover:text-ink'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="max-h-[640px] overflow-y-auto">
            {list.loading && !list.data ? (
              <div className="p-4">
                <SkeletonRows rows={5} />
              </div>
            ) : filtered.length === 0 ? (
              <EmptyState
                icon="check"
                title="Nothing here"
                message="No candidates match this filter right now."
              />
            ) : (
              <ul>
                {filtered.map((candidate) => {
                  const stage = stageMeta(candidate.stage)
                  const offer = offerStatusMeta(candidate.offerStatus)
                  const bond = bondStatusMeta(candidate.bondStatus)
                  const isActive = candidate.id === selectedId
                  return (
                    <li key={candidate.id}>
                      <button
                        type="button"
                        onClick={() => setSelectedId(candidate.id)}
                        className={`flex w-full items-start gap-3 border-b border-surface-line px-4 py-3.5
                          text-left transition ${isActive ? 'bg-brand-tint/60' : 'hover:bg-surface-offwhite'}`}
                      >
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full
                          bg-brand-tint text-[12px] font-semibold text-brand">
                          {initialsOf(candidate.name)}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-[13.5px] font-medium text-ink">
                            {candidate.name}
                          </span>
                          <span className="mt-0.5 block truncate text-[11.5px] text-ink-muted">
                            {candidate.role} &middot; {formatRelative(candidate.updatedAt)}
                          </span>
                          <span className="mt-1.5 flex flex-wrap gap-1.5">
                            <StatusPill label={stage.label} tone={stage.tone} />
                            <StatusPill label={`Offer: ${offer.label}`} tone={offer.tone} />
                            <StatusPill label={`Bond: ${bond.label}`} tone={bond.tone} />
                          </span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </section>

        <div className="space-y-5">
          {loadingDetail && !detail ? (
            <div className="cf-card">
              <LoadingState label="Loading candidate" />
            </div>
          ) : detailError && !detail ? (
            <div className="cf-card">
              <ErrorState
                message={detailError.message}
                action={<Button onClick={() => loadDetail(selectedId)}>Try again</Button>}
              />
            </div>
          ) : !detail ? (
            <div className="cf-card">
              <EmptyState
                icon="document"
                title="Select a candidate"
                message="Choose someone on the left to manage their offer letter and bond."
              />
            </div>
          ) : (
            <>
              <section className="cf-card px-5 py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-[15.5px] font-semibold text-ink">{detail.candidate.name}</h2>
                    <p className="mt-0.5 text-[12.5px] text-ink-muted">
                      {detail.candidate.email} &middot; {detail.candidate.role} &middot;{' '}
                      {detail.candidate.department}
                    </p>
                  </div>
                  <StatusPill
                    label={stageMeta(detail.candidate.stage).label}
                    tone={stageMeta(detail.candidate.stage).tone}
                  />
                </div>
                {detail.candidate.stage === 'docs_pending' && (
                  <p className="mt-3 rounded border border-[#FE5833]/25 bg-[#FFF4EC] px-3.5 py-2.5
                    text-[12.5px] text-[#B94A18]">
                    Documents are still under review. You can prepare the offer and bond now - the candidate
                    will only see the offer once every required document is verified.
                  </p>
                )}
              </section>

              <OfferBondPanel
                candidate={detail.candidate}
                offer={detail.offer}
                bond={detail.bond}
                onChanged={refresh}
              />

              <section className="cf-card p-5">
                <h3 className="text-[15px] font-semibold text-ink">Offer & bond activity</h3>
                <div className="mt-4">
                  <AuditTimeline
                    events={(detail.auditTrail || []).filter((event) =>
                      event.eventType.startsWith('offer') ||
                      event.eventType.startsWith('bond') ||
                      event.eventType.startsWith('signature') ||
                      event.eventType === 'onboarding_completed',
                    )}
                    emptyMessage="Nothing has happened on the offer or bond yet."
                  />
                </div>
              </section>
            </>
          )}
        </div>
      </div>
    </>
  )
}
