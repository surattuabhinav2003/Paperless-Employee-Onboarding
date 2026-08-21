import { StatusPill } from '../ui/StatusPill'
import { SkeletonRows } from '../ui/Spinner'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatRelative, initialsOf } from '../../utils/format'
import { documentProgressMeta, pipelineStatusMeta, statusMeaning } from '../../utils/status'
import { hueOf } from '../../utils/avatar'

/* Above this many requested documents the segments get too thin to read, so
   the meter falls back to a proportional bar. */
const MAX_SEGMENTS = 10

/**
 * One document per segment, coloured by that document's own state. A single bar
 * can only say "how far along"; this says what the progress is made of - which
 * documents are verified, which are waiting on HR, which were sent back. The
 * exact breakdown is on the cell's tooltip rather than spelled out in the row.
 */
function DocumentMeter({ candidate }) {
  const docs = documentProgressMeta(candidate)
  const total = docs.total
  const verified = candidate.documentsVerified || 0
  const submitted = candidate.documentsSubmitted || 0
  const rejected = candidate.documentsRejected || 0
  const done = total > 0 && verified === total

  return (
    <span className="c-meter" title={docs.caption}>
      {total > 0 && total <= MAX_SEGMENTS ? (
        <span className="c-meter-track" role="img" aria-label={docs.caption}>
          {Array.from({ length: total }, (_, index) => {
            let kind = ''
            if (index < verified) kind = ' c-seg--verified'
            else if (index < verified + submitted) kind = ' c-seg--review'
            else if (index < verified + submitted + rejected) kind = ' c-seg--rejected'
            return <span key={index} className={`c-seg${kind}`} />
          })}
        </span>
      ) : (
        <span className="c-meter-bar">
          <span
            style={{
              width: total > 0 ? `${Math.round((docs.uploaded / total) * 100)}%` : '0%',
              background: done ? 'var(--green-500)' : 'var(--blue-500)',
            }}
          />
        </span>
      )}
      <span className={`c-meter-n${done ? ' is-done' : ''}`}>
        {docs.uploaded}/{total}
      </span>
    </span>
  )
}

/**
 * The candidate record list. Grid-aligned rather than a <table>, so each row can
 * carry a stage-coloured edge and a ringed avatar, and so the same rows can
 * restack as cards on a narrow screen instead of scrolling sideways.
 */
export function CandidateTable({ candidates = [], loading = false, onOpen, emptyAction, compact = false }) {
  if (loading) {
    return (
      <div style={{ padding: 20 }}>
        <SkeletonRows rows={6} />
      </div>
    )
  }

  if (!candidates.length) {
    return (
      <EmptyState
        icon="users"
        title="No candidates yet"
        message="Create a candidate to generate their secure onboarding link and start the paperless flow."
        action={emptyAction}
      />
    )
  }

  return (
    <div>
      <div className="c-lhead" aria-hidden="true">
        <span>Candidate</span>
        <span>Role</span>
        <span className="c-lhead-status">Status</span>
        <span>Documents</span>
        <span>{compact ? '' : 'Added'}</span>
        <span />
      </div>

      <ul className="c-list">
        {candidates.map((candidate) => {
          const status = pipelineStatusMeta(candidate)
          return (
            <li key={candidate.id}>
              <button
                type="button"
                className={`c-lrow c-lrow--${status.tone}`}
                onClick={() => onOpen?.(candidate)}
                title={`Open ${candidate.name}`}
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

                <span className="c-stage" title={statusMeaning(status.label)}>
                  <StatusPill label={status.label} tone={status.tone} />
                </span>

                <DocumentMeter candidate={candidate} />

                <span className="c-when" title={formatDate(candidate.createdAt)}>
                  {compact ? '' : formatRelative(candidate.createdAt)}
                </span>

                <svg className="c-chev" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="m9 18 6-6-6-6" />
                </svg>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
