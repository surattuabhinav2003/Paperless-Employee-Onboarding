import { useMemo } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { Button } from '../../components/ui/Button'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { SkeletonRows } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { hueOf } from '../../utils/avatar'
import { formatRelative, initialsOf } from '../../utils/format'
import { searchCandidates } from '../../utils/search'
import { offerStatusMeta } from '../../utils/status'

/*
 * The offer desk lists only candidates whose documents HR has fully verified -
 * nobody else can be sent an offer, so nobody else belongs here.
 *
 * An offer has two states worth filtering on: out with the candidate, or signed.
 *
 * <p>Search works exactly as it does on the records list, and for the same
 * reason: HR types a half-remembered name fast. It runs over the loaded page so
 * every keystroke is instant, and it tolerates typos, which a SQL `LIKE` cannot.
 */
const FILTERS = [
  { key: 'all', label: 'All verified' },
  { key: 'not_sent', label: 'Not sent' },
  { key: 'sent', label: 'Sent' },
  { key: 'signed', label: 'Signed' },
]

function isVerified(candidate) {
  return candidate.stage === 'docs_approved' || candidate.stage === 'offer_accepted'
}

function offerState(candidate) {
  if (candidate.offerStatus === 'accepted') return 'signed'
  if (candidate.offerPrepared) return 'sent'
  return 'not_sent'
}

function matches(candidate, filter) {
  if (!isVerified(candidate)) return false
  return filter === 'all' || offerState(candidate) === filter
}

export function OffersPage() {
  const navigate = useNavigate()
  const location = useLocation()

  /* In the URL for the same reason as the records list: opening an offer
     unmounts this page. */
  const [params, setParams] = useSearchParams()
  const filter = params.get('state') || 'all'
  const query = params.get('q') || ''

  const update = (changes) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  const setFilter = (value) => update({ state: value === 'all' ? '' : value })
  const setQuery = (value) => update({ q: value })

  const list = useAsync(() => hrService.candidates({ size: 100 }), [])
  const candidates = list.data?.content || []

  const search = useMemo(() => searchCandidates(candidates, query), [candidates, query])

  const filtered = useMemo(
    () => search.results.filter((candidate) => matches(candidate, filter)),
    [search.results, filter],
  )

  /* Counts come off the search result, so each state tells you what is behind
     it for this query rather than for the whole desk. */
  const counts = useMemo(() => {
    const tally = {}
    for (const option of FILTERS) {
      tally[option.key] = search.results.filter((candidate) => matches(candidate, option.key)).length
    }
    return tally
  }, [search.results])

  const searching = query.trim().length > 0

  return (
    <>
      <PageHeader breadcrumb="HR console" title="Offer letters" />

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
              aria-label="Search offers"
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

          <div className="c-segctl" role="group" aria-label="Filter offers">
            {FILTERS.map((option) => (
              <button
                key={option.key}
                type="button"
                className={`c-segbtn${filter === option.key ? ' is-on' : ''}`}
                aria-pressed={filter === option.key}
                onClick={() => setFilter(option.key)}
              >
                {option.label}
                <b>{counts[option.key] ?? 0}</b>
              </button>
            ))}
          </div>
          <span className="c-count">
            <b>{filtered.length}</b>
            {filtered.length === 1 ? ' candidate' : ' candidates'}
          </span>
        </div>

        {/* The query stays put on a miss, so a typo can be corrected rather
            than retyped from scratch. */}
        {searching && (
          <p className="c-searchnote">
            {filtered.length === 0 ? (
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
                {filtered.length === 1 ? 'candidate' : `${filtered.length} candidates`}.
              </>
            ) : (
              <>
                {filtered.length} {filtered.length === 1 ? 'match' : 'matches'} for{' '}
                <b>&ldquo;{query.trim()}&rdquo;</b>, best first.
              </>
            )}
          </p>
        )}

        {list.error && !list.data ? (
          <ErrorState
            message={list.error.message}
            action={<Button onClick={() => list.reload()}>Try again</Button>}
          />
        ) : list.loading && !list.data ? (
          <div style={{ padding: 20 }}>
            <SkeletonRows rows={5} />
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState
            icon="check"
            title={searching ? 'No match on the offer desk' : filter === 'all' ? 'No verified candidates yet' : 'Nothing here'}
            message={
              searching
                ? 'Only candidates with every document verified reach this page - the person you want may still be in Candidate Records.'
                : filter === 'all'
                  ? 'A candidate appears here the moment every one of their documents is verified.'
                  : 'No verified candidate is in this state right now.'
            }
          />
        ) : (
          <>
            <div className="c-lhead o-lhead" aria-hidden="true">
              <span>Candidate</span>
              <span>Role</span>
              <span className="c-lhead-status">Offer</span>
              <span>Updated</span>
              <span />
            </div>

            <ul className="c-list">
              {filtered.map((candidate) => {
                const offer = offerStatusMeta(candidate.offerStatus)
                const state = offerState(candidate)
                return (
                  <li key={candidate.id}>
                    <button
                      type="button"
                      className={`c-lrow o-lrow c-lrow--${offer.tone}`}
                      onClick={() => navigate(`/offers/${candidate.id}`, {
                        state: { from: `${location.pathname}${location.search}` },
                      })}
                      title={
                        state === 'not_sent'
                          ? `Upload an offer letter for ${candidate.name}`
                          : `Open ${candidate.name}'s offer letter`
                      }
                    >
                      <span className="c-who">
                        <span className={`c-ring c-ring--h${hueOf(candidate.name)}`}>
                          {initialsOf(candidate.name)}
                        </span>
                        <span className="c-who-text">
                          <span className="c-name">{candidate.name}</span>
                          <span className="c-mail">{candidate.email}</span>
                        </span>
                      </span>

                      <span className="c-role">
                        <span className="c-role-title">{candidate.role}</span>
                        <span className="c-dept">{candidate.department}</span>
                      </span>

                      <span className="c-stage">
                        <StatusPill label={offer.label} tone={offer.tone} />
                      </span>

                      <span className="c-when">{formatRelative(candidate.updatedAt)}</span>

                      <svg className="c-chev" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                        strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="m9 18 6-6-6-6" />
                      </svg>
                    </button>
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </section>

    </>
  )
}
