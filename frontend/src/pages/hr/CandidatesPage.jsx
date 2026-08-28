import { useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { CandidateTable } from '../../components/hr/CandidateTable'
import { InviteLinkModal } from '../../components/hr/InviteLinkModal'
import { BulkInviteModal } from '../../components/hr/BulkInviteModal'
import { NewCandidateModal } from '../../components/hr/NewCandidateModal'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { CANDIDATE_FILTERS, candidateFilter } from '../../utils/candidateFilters'
import { searchCandidates } from '../../utils/search'

/**
 * Candidate records.
 *
 * Search and filtering both run on the loaded page rather than the server: it
 * makes results instant per keystroke, lets the filter show live counts, and -
 * the reason it matters - allows typo-tolerant matching, which a SQL `LIKE`
 * cannot do. The API still caps the page at 100 records.
 *
 * <p>The dashboard hands over through `?filter=`: a tile links here with the
 * group it counted, which arrives as that segment already selected. There is no
 * second mechanism for it - the tile presses the same control HR would have
 * pressed, so the list looks the same however you got to it.
 */
export function CandidatesPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [createOpen, setCreateOpen] = useState(false)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [invitation, setInvitation] = useState(null)

  /*
   * Search and filter live in the URL, not in component state. Opening a
   * candidate unmounts this page, so anything held in state is gone by the time
   * you come back - and a filtered list is also worth being able to share or
   * bookmark. `replace` keeps each keystroke out of the history stack.
   */
  const [params, setParams] = useSearchParams()
  const query = params.get('q') || ''
  const selected = params.get('filter') || ''
  const active = candidateFilter(selected)

  const update = (changes) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  const setQuery = (value) => update({ q: value })
  const setFilter = (value) => update({ filter: value })

  const metadata = useAsync(() => hrService.metadata(), [])
  const page = useAsync(() => hrService.candidates({ size: 100 }), [])

  const all = page.data?.content || []

  const search = useMemo(() => searchCandidates(all, query), [all, query])

  /* Counts come off the search result, so the filter tells you what is actually
     behind each option for this query - not for the whole account. */
  const counts = useMemo(() => {
    const tally = {}
    for (const filter of CANDIDATE_FILTERS) {
      tally[filter.value] = search.results.filter(filter.match).length
    }
    return tally
  }, [search.results])

  const visible = useMemo(
    () => search.results.filter(active.match),
    [search.results, active],
  )

  const onCreated = (created) => {
    setCreateOpen(false)
    setInvitation({ ...created.invitation, candidateName: created.candidate.name })
    page.reload().catch(() => {})
  }

  const searching = query.trim().length > 0

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Candidate Records"
        actions={
          <>
            <Button variant="secondary" onClick={() => setBulkOpen(true)}>Invite several</Button>
            <Button onClick={() => setCreateOpen(true)}>New candidate</Button>
          </>
        }
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
              placeholder="Search name, email, role, department"
              aria-label="Search candidates"
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

          {/* One control rather than loose chips: the options belong together,
              and each carries its own count. This is also where a dashboard
              tile lands - it selects a segment here rather than filtering the
              list some other way. */}
          <div className="c-segctl" role="group" aria-label="Filter candidates">
            {CANDIDATE_FILTERS.map((filter) => (
              <button
                key={filter.value || 'all'}
                type="button"
                className={`c-segbtn${selected === filter.value ? ' is-on' : ''}`}
                aria-pressed={selected === filter.value}
                onClick={() => setFilter(filter.value)}
              >
                {filter.label}
                <b>{counts[filter.value] ?? 0}</b>
              </button>
            ))}
          </div>

          <span className="c-count">
            <b>{visible.length}</b>
            {visible.length === 1 ? ' record' : ' records'}
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
                {visible.length === 1 ? 'record' : `${visible.length} records`}.
              </>
            ) : (
              <>
                {visible.length} {visible.length === 1 ? 'match' : 'matches'} for{' '}
                <b>&ldquo;{query.trim()}&rdquo;</b>, best first.
              </>
            )}
          </p>
        )}

        {page.error && !page.data ? (
          <ErrorState
            message={page.error.message}
            action={<Button onClick={() => page.reload()}>Try again</Button>}
          />
        ) : (
          <CandidateTable
            candidates={visible}
            loading={page.loading && !page.data}
            onOpen={(candidate) => navigate(`/candidates/${candidate.id}`, {
              state: { from: `${location.pathname}${location.search}` },
            })}
            emptyAction={
              selected
                ? <Button variant="secondary" onClick={() => setFilter('')}>Show all candidates</Button>
                : <Button onClick={() => setCreateOpen(true)}>New candidate</Button>
            }
          />
        )}
      </section>

      <NewCandidateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        documentTypes={metadata.data?.documentTypes || []}
        onCreated={onCreated}
      />

      <BulkInviteModal
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        documentTypes={metadata.data?.documentTypes || []}
        /* Refresh only - the modal decides when to close, because it stays open
           on a partial batch to show which rows failed. */
        onCreated={() => page.reload().catch(() => {})}
      />

      <InviteLinkModal
        open={Boolean(invitation)}
        invitation={invitation}
        candidateName={invitation?.candidateName}
        onClose={() => setInvitation(null)}
      />
    </>
  )
}
