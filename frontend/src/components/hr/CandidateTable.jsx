import { ProgressBar } from '../ui/ProgressBar'
import { StatusPill } from '../ui/StatusPill'
import { SkeletonRows } from '../ui/Spinner'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, initialsOf } from '../../utils/format'
import { stageMeta } from '../../utils/status'

/**
 * The HR pipeline. A whole row is the click target - offer and bond detail lives
 * on the candidate page rather than as extra columns here.
 */
export function CandidateTable({ candidates = [], loading = false, onOpen, emptyAction, compact = false }) {
  if (loading) {
    return (
      <div className="p-5">
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
    <div className="cf-scroll-x">
      <table className="w-full min-w-[720px] border-collapse">
        <thead className="bg-surface-offwhite/80">
          <tr>
            <th className="cf-th">Candidate</th>
            <th className="cf-th">Role</th>
            <th className="cf-th">Stage</th>
            <th className="cf-th">Documents</th>
            {!compact && <th className="cf-th">Created</th>}
            <th className="cf-th w-8" aria-label="Open candidate" />
          </tr>
        </thead>
        <tbody>
          {candidates.map((candidate) => {
            const stage = stageMeta(candidate.stage)
            const open = () => onOpen?.(candidate)
            return (
              <tr
                key={candidate.id}
                role="button"
                tabIndex={0}
                onClick={open}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    open()
                  }
                }}
                className="group cursor-pointer border-t border-surface-line transition-colors
                  hover:bg-brand-tint/40 focus:bg-brand-tint/40 focus:outline-none"
                title={`Open ${candidate.name}`}
              >
                <td className="cf-td">
                  <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full
                      bg-brand-tint text-[12px] font-semibold text-brand">
                      {initialsOf(candidate.name)}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate font-medium text-ink group-hover:text-brand">
                        {candidate.name}
                      </span>
                      <span className="block truncate text-[12px] text-ink-muted">{candidate.email}</span>
                    </span>
                  </div>
                </td>
                <td className="cf-td">
                  <span className="block whitespace-nowrap text-ink-body">{candidate.role}</span>
                  <span className="block text-[12px] text-ink-muted">{candidate.department}</span>
                </td>
                <td className="cf-td">
                  <StatusPill label={stage.label} tone={stage.tone} />
                </td>
                <td className="cf-td w-[190px]">
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
                  <span className="mt-1 block text-[11.5px] text-ink-muted">
                    {candidate.documentsSubmitted > 0 && `${candidate.documentsSubmitted} to review`}
                    {candidate.documentsSubmitted > 0 && candidate.documentsRejected > 0 && ' · '}
                    {candidate.documentsRejected > 0 && `${candidate.documentsRejected} rejected`}
                    {candidate.documentsSubmitted === 0 &&
                      candidate.documentsRejected === 0 &&
                      (candidate.documentsMissing > 0
                        ? `${candidate.documentsMissing} awaiting upload`
                        : 'All verified')}
                  </span>
                </td>
                {!compact && (
                  <td className="cf-td whitespace-nowrap text-[12.5px] text-ink-muted">
                    {formatDate(candidate.createdAt)}
                  </td>
                )}
                <td className="cf-td pr-4 text-right">
                  <svg
                    viewBox="0 0 24 24"
                    className="ml-auto h-4 w-4 text-ink-muted transition group-hover:translate-x-0.5
                      group-hover:text-brand"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="m9 18 6-6-6-6" />
                  </svg>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
