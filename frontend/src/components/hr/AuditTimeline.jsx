import { EmptyState } from '../ui/EmptyState'
import { formatDateTime } from '../../utils/format'

const ACTOR_TONE = {
  hr: 'bg-brand',
  candidate: 'bg-accent-teal',
  system: 'bg-ink-muted',
  signature_provider: 'bg-accent-blue',
}

const ACTOR_LABEL = {
  hr: 'HR',
  candidate: 'Candidate',
  system: 'System',
  signature_provider: 'SignatureOne',
}

/** Chronological audit trail for one candidate (or the whole account). */
export function AuditTimeline({ events = [], showMetadata = true, emptyMessage }) {
  if (!events.length) {
    return (
      <EmptyState
        icon="search"
        title="No activity yet"
        message={emptyMessage || 'Events appear here as soon as something happens.'}
      />
    )
  }

  return (
    <ol className="relative space-y-4 pl-5">
      <span className="absolute left-[5px] top-2 h-[calc(100%-16px)] w-px bg-surface-line" aria-hidden="true" />
      {events.map((event) => (
        <li key={event.id} className="relative">
          <span
            className={`absolute -left-5 top-1.5 h-2.5 w-2.5 rounded-full ring-2 ring-white ${
              ACTOR_TONE[event.actorType] || 'bg-ink-muted'
            }`}
          />
          <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <p className="text-[13.5px] font-medium text-ink">{event.eventLabel}</p>
            <span className="rounded-full bg-surface-offwhite px-1.5 py-0.5 text-[10.5px] font-medium
              uppercase tracking-[0.08em] text-ink-muted">
              {ACTOR_LABEL[event.actorType] || event.actorType}
            </span>
          </div>
          <p className="mt-0.5 text-[12px] text-ink-muted">
            {formatDateTime(event.occurredAt)} &middot; {event.actor}
            {event.ipAddress ? ` · ${event.ipAddress}` : ''}
          </p>
          {showMetadata && event.metadata && Object.keys(event.metadata).length > 0 && (
            <dl className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 rounded bg-surface-offwhite/70 px-2.5 py-2">
              {Object.entries(event.metadata)
                .filter(([, value]) => value !== null && value !== undefined && value !== '')
                .slice(0, 6)
                .map(([key, value]) => (
                  <div key={key} className="flex min-w-0 gap-1.5 text-[11.5px]">
                    <dt className="shrink-0 text-ink-muted">{key}</dt>
                    <dd className="truncate font-medium text-ink-body" title={String(value)}>
                      {Array.isArray(value) ? value.join(', ') : String(value)}
                    </dd>
                  </div>
                ))}
            </dl>
          )}
        </li>
      ))}
    </ol>
  )
}
