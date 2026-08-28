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
  done: 'M20 6 9 17l-5-5',
}

/**
 * The dashboard is four numbers and the recent candidates - nothing else. The
 * labels are the explanation, so there is no helper copy under them, and the
 * detail lives one click away on the candidate record.
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

  const figures = [
    { key: 'active', label: 'Active candidates', value: data?.activeCandidates, icon: 'users', tone: 'blue' },
    { key: 'pending', label: 'Documents pending', value: data?.documentsPending, icon: 'upload', tone: 'amber' },
    { key: 'review', label: 'Awaiting review', value: data?.awaitingReview, icon: 'review', tone: 'teal' },
    { key: 'done', label: 'Onboarding complete', value: data?.onboardingComplete, icon: 'done', tone: 'green' },
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
            <div key={figure.key} className={`c-fig c-fig--${figure.tone}`}>
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
            </div>
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
