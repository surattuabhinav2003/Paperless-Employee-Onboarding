import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
 * they want to send to, so anyone can appear here.
 */
export function NocPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState('')
  const [createOpen, setCreateOpen] = useState(false)

  const list = useAsync(() => hrService.nocList({ status: filter }), [filter])
  // The allowed recipient domains are the server's to decide, not the form's.
  const metadata = useAsync(() => hrService.metadata(), [])
  const packets = list.data?.content || []


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
            {packets.length} document{packets.length === 1 ? '' : 's'}
          </span>
        </div>

        {list.loading && !list.data && <SkeletonRows rows={4} />}

        {list.error && (
          <ErrorState message={list.error.message} action={<Button onClick={list.reload}>Try again</Button>} />
        )}

        {!list.loading && !list.error && packets.length === 0 && (
          <EmptyState
            icon="document"
            title="Nothing here yet"
            message="Upload an NDA and an NOC to combine them into one document and send it for signature."
            action={<Button onClick={() => setCreateOpen(true)}>New NDA + NOC</Button>}
          />
        )}

        {packets.length > 0 && (
          <ul className="d-list">
            {packets.map((packet) => (
              <li key={packet.id}>
                {/* The whole row opens the record, matching Candidate Records
                    and Offer Letters - the actions live on the record itself. */}
                <button
                  type="button"
                  onClick={() => navigate(`/noc/record/${packet.id}`, { state: { from: '/noc' } })}
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
