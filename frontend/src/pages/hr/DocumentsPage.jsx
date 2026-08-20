import { useEffect, useMemo, useState } from 'react'
import { PageHeader } from '../../components/PageHeader'
import { DocumentReviewPanel } from '../../components/hr/DocumentReviewPanel'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState, SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { formatRelative, initialsOf } from '../../utils/format'
import { stageMeta } from '../../utils/status'

/**
 * Document Requests: pick a candidate on the left, review each submitted
 * document on the right. Verifying the last mandatory document is what unlocks
 * the offer stage - the backend makes that call, not this page.
 */
export function DocumentsPage() {
  const toast = useToast()
  const [selectedId, setSelectedId] = useState(null)
  const [filter, setFilter] = useState('needs_review')

  const list = useAsync(() => hrService.candidates({ size: 100 }), [])
  const candidates = list.data?.content || []

  const filtered = useMemo(() => {
    if (filter === 'needs_review') {
      return candidates.filter((candidate) => candidate.documentsSubmitted > 0)
    }
    if (filter === 'awaiting_candidate') {
      return candidates.filter(
        (candidate) =>
          candidate.stage === 'docs_pending' &&
          candidate.documentsSubmitted === 0 &&
          (candidate.documentsMissing > 0 || candidate.documentsRejected > 0),
      )
    }
    return candidates
  }, [candidates, filter])

  useEffect(() => {
    if (!selectedId && filtered.length) {
      setSelectedId(filtered[0].id)
    }
  }, [filtered, selectedId])

  const [documents, setDocuments] = useState(null)
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [docsError, setDocsError] = useState(null)

  const selected = candidates.find((candidate) => candidate.id === selectedId) || null

  const loadDocuments = async (candidateId) => {
    if (!candidateId) return
    setLoadingDocs(true)
    setDocsError(null)
    try {
      setDocuments(await hrService.documents(candidateId))
    } catch (error) {
      setDocsError(error)
      toast.apiError(error, 'Could not load documents')
    } finally {
      setLoadingDocs(false)
    }
  }

  useEffect(() => {
    if (selectedId) {
      loadDocuments(selectedId)
    } else {
      setDocuments(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  const onReviewed = (updatedDocuments) => {
    setDocuments(updatedDocuments)
    list.reload().catch(() => {})
  }

  const totalToReview = candidates.reduce((sum, candidate) => sum + candidate.documentsSubmitted, 0)

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Document requests"
        subtitle="Verify or reject each submitted document. Rejections reopen only that document for re-upload."
        actions={
          <span className="rounded-full bg-brand-tint px-3 py-1.5 text-[12.5px] font-medium text-brand">
            {totalToReview} awaiting review
          </span>
        }
      />

      <div className="grid gap-5 lg:grid-cols-[300px_1fr]">
        <section className="cf-card overflow-hidden">
          <div className="border-b border-surface-line p-3">
            <div className="flex gap-1 rounded bg-surface-offwhite p-1">
              {[
                { key: 'needs_review', label: 'To review' },
                { key: 'awaiting_candidate', label: 'With candidate' },
                { key: 'all', label: 'All' },
              ].map((tab) => (
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
                title={filter === 'needs_review' ? 'Nothing to review' : 'No candidates here'}
                message={
                  filter === 'needs_review'
                    ? 'Every submitted document has been reviewed. Nice work.'
                    : 'Try a different filter to see more candidates.'
                }
              />
            ) : (
              <ul>
                {filtered.map((candidate) => {
                  const meta = stageMeta(candidate.stage)
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
                          <span className="flex items-center justify-between gap-2">
                            <span className="truncate text-[13.5px] font-medium text-ink">
                              {candidate.name}
                            </span>
                            {candidate.documentsSubmitted > 0 && (
                              <span className="shrink-0 rounded-full bg-brand px-1.5 py-0.5 text-[10px]
                                font-semibold text-white">
                                {candidate.documentsSubmitted}
                              </span>
                            )}
                          </span>
                          <span className="mt-0.5 block truncate text-[11.5px] text-ink-muted">
                            {candidate.role} &middot; updated {formatRelative(candidate.updatedAt)}
                          </span>
                          <span className="mt-1.5 flex items-center gap-2">
                            <StatusPill label={meta.label} tone={meta.tone} />
                          </span>
                          <span className="mt-2 block">
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

        <section className="cf-card overflow-hidden">
          {!selected ? (
            <EmptyState
              icon="document"
              title="Select a candidate"
              message="Pick a candidate on the left to review their submitted documents."
            />
          ) : (
            <>
              <header className="flex flex-wrap items-start justify-between gap-3 border-b border-surface-line
                px-5 py-4">
                <div>
                  <h2 className="text-[15.5px] font-semibold text-ink">{selected.name}</h2>
                  <p className="mt-0.5 text-[12.5px] text-ink-muted">
                    {selected.email} &middot; {selected.role} &middot; {selected.department}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <StatusPill label={stageMeta(selected.stage).label} tone={stageMeta(selected.stage).tone} />
                  <Button variant="subtle" size="sm" onClick={() => loadDocuments(selected.id)}>
                    Refresh
                  </Button>
                </div>
              </header>

              {selected.stage !== 'docs_pending' && (
                <p className="border-b border-surface-line bg-[#F3FCF7] px-5 py-2.5 text-[12.5px] text-[#0E7A47]">
                  All required documents are approved - this candidate has moved past the document stage.
                </p>
              )}

              {loadingDocs && !documents ? (
                <LoadingState label="Loading documents" />
              ) : docsError && !documents ? (
                <ErrorState
                  message={docsError.message}
                  action={<Button onClick={() => loadDocuments(selected.id)}>Try again</Button>}
                />
              ) : (
                <DocumentReviewPanel
                  documents={documents || []}
                  readOnly={selected.stage !== 'docs_pending'}
                  onChanged={onReviewed}
                />
              )}
            </>
          )}
        </section>
      </div>
    </>
  )
}
