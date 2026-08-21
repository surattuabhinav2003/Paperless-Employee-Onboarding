import { ProgressBar } from '../ui/ProgressBar'
import { StatusPill } from '../ui/StatusPill'
import { SkeletonRows } from '../ui/Spinner'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatRelative, initialsOf } from '../../utils/format'
import { documentProgressMeta, pipelineStatusMeta } from '../../utils/status'

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
      <table className="w-full min-w-[700px] border-collapse text-left">
        <thead>
          <tr className="border-b border-surface-line bg-surface-offwhite/70">
            <th className="cf-th py-2.5 pl-5">Candidate</th>
            <th className="cf-th py-2.5">Role</th>
            <th className="cf-th py-2.5">Stage</th>
            <th className="cf-th py-2.5">Documents</th>
            {!compact && <th className="cf-th py-2.5">Created</th>}
            <th className="cf-th w-10 py-2.5" aria-label="Open candidate" />
          </tr>
        </thead>
        <tbody>
          {candidates.map((candidate) => {
            const status = pipelineStatusMeta(candidate)
            const docs = documentProgressMeta(candidate)
            const complete = docs.total > 0 && docs.verified === docs.total
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
                className="group cursor-pointer border-b border-surface-line/70 transition-colors
                  last:border-0 hover:bg-brand-tint/25 focus:bg-brand-tint/25 focus:outline-none"
                title={`Open ${candidate.name}`}
              >
                <td className="relative py-3 pl-5 pr-4 align-middle">
                  {/* Accent edge marks the hovered row without shifting anything. */}
                  <span
                    className="absolute left-0 top-0 h-full w-[3px] bg-brand opacity-0 transition-opacity
                      group-hover:opacity-100 group-focus:opacity-100"
                    aria-hidden="true"
                  />
                  <div className="flex items-center gap-3">
                    <span
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full
                        bg-gradient-to-br from-brand to-brand-bright text-[12.5px] font-semibold text-white
                        ring-2 ring-white"
                    >
                      {initialsOf(candidate.name)}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-[14px] font-medium leading-5 text-ink
                        group-hover:text-brand">
                        {candidate.name}
                      </span>
                      <span className="block truncate text-[12px] leading-5 text-ink-muted">
                        {candidate.email}
                      </span>
                    </span>
                  </div>
                </td>

                <td className="py-3 pr-4 align-middle">
                  <span className="block truncate text-[13.5px] leading-5 text-ink-body">
                    {candidate.role}
                  </span>
                  <span className="mt-0.5 inline-flex max-w-full items-center truncate rounded-full
                    bg-surface-offwhite px-2 py-0.5 text-[11px] leading-4 text-ink-muted
                    ring-1 ring-inset ring-surface-line">
                    {candidate.department}
                  </span>
                </td>

                <td className="py-3 pr-4 align-middle">
                  <StatusPill label={status.label} tone={status.tone} />
                </td>

                <td className="w-[184px] py-3 pr-4 align-middle" title={docs.caption}>
                  <div className="flex items-center gap-2.5">
                    <ProgressBar
                      className="flex-1"
                      value={docs.uploaded}
                      total={docs.total}
                      tone={docs.tone}
                      showLabel={false}
                    />
                    <span
                      className={`shrink-0 text-[12px] font-medium tabular-nums ${
                        complete ? 'text-[#0E7A47]' : 'text-ink-body'
                      }`}
                    >
                      {docs.uploaded}/{docs.total}
                    </span>
                  </div>
                </td>

                {!compact && (
                  <td className="py-3 pr-4 align-middle" title={formatDate(candidate.createdAt)}>
                    <span className="whitespace-nowrap text-[12.5px] text-ink-muted">
                      {formatRelative(candidate.createdAt)}
                    </span>
                  </td>
                )}

                <td className="py-3 pr-5 align-middle text-right">
                  <svg
                    viewBox="0 0 24 24"
                    className="ml-auto h-4 w-4 text-ink-muted/50 transition-all
                      group-hover:translate-x-0.5 group-hover:text-brand"
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
