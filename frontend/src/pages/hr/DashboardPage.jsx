import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { CandidateTable } from '../../components/hr/CandidateTable'
import { InviteLinkModal } from '../../components/hr/InviteLinkModal'
import { NewCandidateModal } from '../../components/hr/NewCandidateModal'
import { StatCard } from '../../components/hr/StatCard'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'

export function DashboardPage() {
  const navigate = useNavigate()
  const [createOpen, setCreateOpen] = useState(false)
  const [invitation, setInvitation] = useState(null)

  const stats = useAsync(() => hrService.dashboardStats(), [])
  const metadata = useAsync(() => hrService.metadata(), [])

  const data = stats.data

  const onCreated = (created) => {
    setCreateOpen(false)
    setInvitation({ ...created.invitation, candidateName: created.candidate.name })
    stats.reload().catch(() => {})
  }

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Onboarding dashboard"
        subtitle="Live view of every candidate in the paperless onboarding flow - documents, offers and bonds."
        actions={
          <>
            <Button variant="secondary" onClick={() => navigate('/candidates')}>
              View full pipeline
            </Button>
            <Button onClick={() => setCreateOpen(true)}>New candidate</Button>
          </>
        }
      />

      {stats.error && !data ? (
        <div className="cf-card">
          <ErrorState
            message={stats.error.message}
            action={<Button onClick={() => stats.reload()}>Try again</Button>}
          />
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Active candidates"
              value={data?.activeCandidates ?? 0}
              hint={`${data?.totalCandidates ?? 0} total in the pipeline`}
              icon="users"
              tone="brand"
              loading={stats.loading && !data}
            />
            <StatCard
              label="Documents pending"
              value={data?.documentsPending ?? 0}
              hint="Waiting on candidates to upload or re-upload"
              icon="upload"
              tone="amber"
              loading={stats.loading && !data}
            />
            <StatCard
              label="Awaiting review"
              value={data?.awaitingReview ?? 0}
              hint="Open a candidate to verify or reject their documents"
              icon="review"
              tone="blue"
              loading={stats.loading && !data}
            />
            <StatCard
              label="Bonds signed"
              value={data?.bondsSigned ?? 0}
              hint={`${data?.offersAwaitingAcceptance ?? 0} offers awaiting acceptance`}
              icon="signed"
              tone="green"
              loading={stats.loading && !data}
            />
          </div>

          <section className="mt-6 cf-card overflow-hidden">
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-surface-line
              px-5 py-4">
              <div>
                <h2 className="text-[15px] font-semibold text-ink">Candidate pipeline</h2>
                <p className="mt-0.5 text-[12.5px] text-ink-muted">
                  Most recent candidates and where each one stands
                </p>
              </div>
              <Button variant="subtle" size="sm" onClick={() => navigate('/candidates')}>
                Open pipeline
              </Button>
            </header>
            <CandidateTable
              compact
              candidates={data?.recentCandidates || []}
              loading={stats.loading && !data}
              onOpen={(candidate) => navigate(`/candidates/${candidate.id}`)}
              emptyAction={<Button onClick={() => setCreateOpen(true)}>New candidate</Button>}
            />
          </section>

          <section className="mt-5 cf-card p-5">
            <h2 className="text-[15px] font-semibold text-ink">Stage breakdown</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {Object.entries(data?.stageBreakdown || {}).map(([stage, count]) => (
                <div key={stage} className="rounded border border-surface-line bg-surface-offwhite/60 px-4 py-3">
                  <p className="text-[11.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">
                    {stage.replace(/_/g, ' ')}
                  </p>
                  <p className="mt-1 text-[20px] font-semibold text-ink">{count}</p>
                </div>
              ))}
            </div>
          </section>
        </>
      )}

      <NewCandidateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        documentTypes={metadata.data?.documentTypes || []}
        onCreated={onCreated}
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
