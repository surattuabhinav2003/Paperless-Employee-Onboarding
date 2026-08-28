import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { NewNocModal } from '../../components/hr/NewNocModal'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { hueOf } from '../../utils/avatar'
import { formatBytes, formatRelative, initialsOf } from '../../utils/format'
import { searchCandidates } from '../../utils/search'

const STATUS_TONE = {
  draft: 'amber',
  sent: 'blue',
  viewed: 'teal',
  signed: 'green',
}

const FILTERS = [
  { key: '', label: 'All' },
  { key: 'draft', label: 'Draft' },
  { key: 'sent', label: 'Out for signature' },
  { key: 'signed', label: 'Signed' },
]

/**
 * The NDA + NOC desk.
 *
 * Each row is one combined document sent to one person. Unlike the offer desk
 * this is not driven by the candidate pipeline - HR types in whichever address
 * they want to send to, so anyone can appear here. That makes search matter
 * more here than anywhere else: there is no pipeline to browse down, only a
 * list of names that grows.
 *
 * <p>Status is filtered by the server, search runs over what came back. Both
 * live in the URL, as on the other two lists - opening a record unmounts this
 * page, and a filtered view is worth being able to share.
 */
export function NocPage() {
  const navigate = useNavigate()
  const [createOpen, setCreateOpen] = useState(false)

  const [params, setParams] = useSearchParams()
  const filter = params.get('status') || ''
  const query = params.get('q') || ''

  const update = (changes) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  const setFilter = (value) => update({ status: value })
  const setQuery = (value) => update({ q: value })

  const list = useAsync(() => hrService.nocList({ status: filter }), [filter])
  // The allowed recipient domains are the server's to decide, not the form's.
  const metadata = useAsync(() => hrService.metadata(), [])
  const packets = list.data?.content || []

  /*
   * A packet is not a candidate, so it is projected onto the three fields the
   * scorer reads before being handed over. Reusing it rather than writing a
   * second matcher is the point: HR gets the same typo tolerance here as on the
   * other lists, and there is one place where "search" is defined.
   */
  const search = useMemo(() => {
    const projected = packets.map((packet) => ({
      packet,
      name: packet.recipientName,
      email: packet.recipientEmail,
      role: packet.title,
    }))
    const result = searchCandidates(projected, query)
    return { results: result.results.map((row) => row.packet), fuzzy: result.fuzzy }
  }, [packets, query])

  const visible = search.results
  const searching = query.trim().length > 0


  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="NDA & NOC"
        subtitle="Upload an NDA and an NOC together. They are combined into one document, signed in one pass, and returned to you as one."
        actions={<Button onClick={() => setCreateOpen(true)}>New NDA + NOC</Button>}
      />

      <section className="c-panel">
        <div className="c-toolbar">
          <div className="c-search">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
              strokeLinecap="round" aria-hidden="true">
              <path d="m21 21-4.35-4.35M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z" />
            </svg>
            <input
              className="cf-input cf-input--icon"
              type="text"
              value={query}
              placeholder="Search name, email, title"
              aria-label="Search NDA and NOC documents"
              onChange={(event) => setQuery(event.target.value)}
            />
            {searching && (
              <button
                type="button"
                className="c-search-clear"
                onClick={() => setQuery('')}
                aria-label="Clear search"
                title="Clear search"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                  strokeLinecap="round" aria-hidden="true">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            )}
          </div>

          <div className="c-segctl" role="group" aria-label="Filter by status">
            {FILTERS.map((option) => (
              <button
                key={option.key || 'all'}
                type="button"
                className={`c-segbtn${filter === option.key ? ' is-on' : ''}`}
                onClick={() => setFilter(option.key)}
              >
                {option.label}
              </button>
            ))}
          </div>
          <span className="c-count">
            {visible.length} document{visible.length === 1 ? '' : 's'}
          </span>
        </div>

        {/* The query stays put on a miss, so a typo can be corrected rather
            than retyped from scratch. */}
        {searching && (
          <p className="c-searchnote">
            {visible.length === 0 ? (
              <>
                Nothing matches <b>&ldquo;{query.trim()}&rdquo;</b>. Check the spelling, or{' '}
                <button type="button" className="c-linkbtn" onClick={() => setQuery('')}>
                  clear the search
                </button>
                .
              </>
            ) : search.fuzzy ? (
              <>
                No exact match for <b>&ldquo;{query.trim()}&rdquo;</b> &ndash; showing the closest{' '}
                {visible.length === 1 ? 'document' : `${visible.length} documents`}.
              </>
            ) : (
              <>
                {visible.length} {visible.length === 1 ? 'match' : 'matches'} for{' '}
                <b>&ldquo;{query.trim()}&rdquo;</b>, best first.
              </>
            )}
          </p>
        )}

        {list.loading && !list.data && <SkeletonRows rows={4} />}

        {list.error && (
          <ErrorState message={list.error.message} action={<Button onClick={list.reload}>Try again</Button>} />
        )}

        {!list.loading && !list.error && visible.length === 0 && (
          searching ? (
            <EmptyState
              icon="document"
              title="No match"
              message="No NDA + NOC on this list is addressed to that name or email."
            />
          ) : (
            <EmptyState
              icon="document"
              title="Nothing here yet"
              message="Upload an NDA and an NOC to combine them into one document and send it for signature."
              action={<Button onClick={() => setCreateOpen(true)}>New NDA + NOC</Button>}
            />
          )
        )}

        {visible.length > 0 && (
          <ul className="d-list">
            {visible.map((packet) => (
              <li key={packet.id}>
                {/* The whole row opens the record, matching Candidate Records
                    and Offer Letters - the actions live on the record itself. */}
                <button
                  type="button"
                  onClick={() => navigate(`/noc/record/${packet.id}`, {
                    state: { from: `/noc${params.toString() ? `?${params}` : ''}` },
                  })}
                  className={`d-row d-row--${packet.status === 'signed' ? 'verified' : 'submitted'}
                    w-full cursor-pointer text-left`}
                >
                  <span className={`r-av c-ring--h${hueOf(packet.recipientName)}`} aria-hidden="true">
                    {initialsOf(packet.recipientName)}
                  </span>

                  <div className="d-body">
                    <div className="d-title-row">
                      <span className="d-title">{packet.recipientName}</span>
                      <StatusPill label={packet.statusLabel} tone={STATUS_TONE[packet.status] || 'grey'} />
                      {packet.title && <span className="d-course">{packet.title}</span>}
                    </div>
                    <p className="d-meta">
                      {packet.recipientEmail}
                      {' · '}
                      {/* Says plainly that two files became one. */}
                      {packet.ndaFilename} + {packet.nocFilename} → {packet.pageCount} page
                      {packet.pageCount === 1 ? '' : 's'}
                      {' · '}{formatBytes(packet.sizeBytes)}
                      {packet.signedAt
                        ? ` · Signed by ${packet.signedByName} ${formatRelative(packet.signedAt)}`
                        : packet.sentAt ? ` · Sent ${formatRelative(packet.sentAt)}` : ''}
                    </p>
                  </div>

                  <div className="d-actions">
                    {packet.status === 'draft' && packet.fields.length === 0 && (
                      <span className="text-[12px] text-amber-ink">Needs fields</span>
                    )}
                    <svg viewBox="0 0 24 24" className="h-4 w-4 text-ink-faint" fill="none"
                      stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                      <path d="m9 18 6-6-6-6" />
                    </svg>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <NewNocModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        allowedDomains={metadata.data?.nocRecipientDomains || []}
        onCreated={(created) => {
          setCreateOpen(false)
          // Straight to the record: placing fields is the next thing HR must do,
          // and the document cannot be sent until they have.
          navigate(`/noc/record/${created.id}`, { state: { from: '/noc' } })
        }}
      />


    </>
  )
}
