import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { CandidateTable } from '../../components/hr/CandidateTable'
import { BulkInviteModal } from '../../components/hr/BulkInviteModal'
import { InviteLinkModal } from '../../components/hr/InviteLinkModal'
import { NewCandidateModal } from '../../components/hr/NewCandidateModal'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'

const ICONS = {
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  upload: 'M12 16V4m0 0L8 8m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2',
  review: 'M14 3v5h5M6 3h9l5 5v13H6V3Zm3 9h6M9 16h4',
  // A tick inside a shield: checked and settled, as against merely ticked off.
  shield: 'M12 3l7 3v5c0 4.4-2.9 8.3-7 9.5C7.9 19.3 5 15.4 5 11V6l7-3Zm-2.5 8.5 2 2 4-4',
  // A nib over a ruled line - the signature, which is what the tile is about.
  signed: 'M4 20h16M6.5 15.5 15 7a2.1 2.1 0 0 1 3 3l-8.5 8.5-4 1 1-4Z',
}

/**
 * The dashboard is five numbers and the recent candidates - nothing else. The
 * labels are the explanation, so there is no helper copy under them, and the
 * detail lives one click away on the candidate record.
 *
 * <p>Every number is a head-count, and every tile is a link: clicking one opens
 * the candidate list with that filter already selected, showing precisely the
 * people the tile counted. The figure is a way in rather than a fact to act on
 * somewhere else.
 */
export function DashboardPage() {
  const navigate = useNavigate()
  const [createOpen, setCreateOpen] = useState(false)
  const [bulkOpen, setBulkOpen] = useState(false)
  const [invitation, setInvitation] = useState(null)

  const stats = useAsync(() => hrService.dashboardStats(), [])
  const metadata = useAsync(() => hrService.metadata(), [])

  const data = stats.data
  const loading = stats.loading && !data

  /* Each tile carries the filter it opens, so the segment HR lands on is the
     one wearing the same name they just clicked. */
  /*
   * The four stages in the order a candidate passes through them, behind the
   * roster they all belong to. The colour runs with the journey - amber while
   * the ball is in the candidate's court, teal while it is in HR's, then blue
   * and green as it lands - so the row reads left to right as progress.
   *
   * `to` is where the number takes you, and each destination is a list exactly
   * this long. Signed offers go to the offer desk rather than the records list:
   * that page already tracks an offer from not-sent through to signed, so it is
   * where the answer to "who has signed?" belongs.
   */
  const figures = [
    { key: 'total', label: 'All candidates', value: data?.totalCandidates, icon: 'users', tone: 'slate', to: '/candidates' },
    { key: 'pending', label: 'Documents pending', value: data?.documentsPending, icon: 'upload', tone: 'amber', to: '/candidates?filter=docs_pending' },
    { key: 'review', label: 'Awaiting review', value: data?.awaitingReview, icon: 'review', tone: 'teal', to: '/candidates?filter=awaiting_review' },
    { key: 'verified', label: 'Verification done', value: data?.verificationDone, icon: 'shield', tone: 'blue', to: '/candidates?filter=verified' },
    { key: 'signed', label: 'Offer letter signed', value: data?.onboardingComplete, icon: 'signed', tone: 'green', to: '/offers?state=signed' },
  ]

  const onCreated = (created) => {
    setCreateOpen(false)
    setInvitation({ ...created.invitation, candidateName: created.candidate.name })
    stats.reload().catch(() => {})
  }

  if (stats.error && !data) {
    return (
      <div className="cf-card">
        <ErrorState
          message={stats.error.message}
          action={<Button onClick={() => stats.reload()}>Try again</Button>}
        />
      </div>
    )
  }

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Onboarding dashboard"
        actions={
          <>
            <Button variant="secondary" onClick={() => setBulkOpen(true)}>Invite several</Button>
            <Button onClick={() => setCreateOpen(true)}>New candidate</Button>
          </>
        }
      />

      <div className="c-dash">
        <section className="c-figs">
          {figures.map((figure) => (
            <button
              key={figure.key}
              type="button"
              className={`c-fig c-fig--${figure.tone}`}
              onClick={() => navigate(figure.to)}
              aria-label={`${figure.value ?? 0} ${figure.label.toLowerCase()} - show them`}
            >
              <span className="c-fig-mark" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
                  strokeLinecap="round" strokeLinejoin="round">
                  <path d={ICONS[figure.icon]} />
                </svg>
              </span>
              {loading
                ? <span className="c-fig-skel cf-skeleton" />
                : <span className="c-fig-n">{figure.value ?? 0}</span>}
              <span className="c-fig-l">{figure.label}</span>
            </button>
          ))}
        </section>

        <section className="c-panel">
          <header className="c-panel-head">
            <h2>Recent candidates</h2>
            <Button variant="subtle" size="sm" onClick={() => navigate('/candidates')}>
              View all
            </Button>
          </header>
          <CandidateTable
            compact
            candidates={data?.recentCandidates || []}
            loading={loading}
            onOpen={(candidate) => navigate(`/candidates/${candidate.id}`)}
            emptyAction={<Button onClick={() => setCreateOpen(true)}>New candidate</Button>}
          />
        </section>
      </div>

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
        onCreated={() => stats.reload().catch(() => {})}
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
