import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'
import { CandidateTable } from '../../components/hr/CandidateTable'
import { InviteLinkModal } from '../../components/hr/InviteLinkModal'
import { NewCandidateModal } from '../../components/hr/NewCandidateModal'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { Select, TextInput } from '../../components/ui/Field'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'

export function CandidatesPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [stage, setStage] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [invitation, setInvitation] = useState(null)

  const metadata = useAsync(() => hrService.metadata(), [])
  const page = useAsync(() => hrService.candidates({ query: search, stage, size: 100 }), [search, stage])

  const candidates = page.data?.content || []

  const onCreated = (created) => {
    setCreateOpen(false)
    setInvitation({ ...created.invitation, candidateName: created.candidate.name })
    page.reload().catch(() => {})
  }

  return (
    <>
      <PageHeader
        breadcrumb="HR console"
        title="Candidate pipeline"
        subtitle="Every candidate and where they stand. Open a row for their details, documents, offer and bond."
        actions={<Button onClick={() => setCreateOpen(true)}>New candidate</Button>}
      />

      <section className="cf-card overflow-hidden">
        <header className="flex flex-wrap items-center gap-3 border-b border-surface-line px-5 py-4">
          <form
            className="flex flex-1 flex-wrap items-center gap-2.5"
            onSubmit={(event) => {
              event.preventDefault()
              setSearch(query.trim())
            }}
          >
            <div className="relative min-w-[220px] flex-1 sm:max-w-xs">
              <TextInput
                value={query}
                placeholder="Search name, email, role, department"
                onChange={(event) => setQuery(event.target.value)}
                className="pl-9"
              />
              <svg
                viewBox="0 0 24 24"
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-muted"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.9"
              >
                <path d="m21 21-4.35-4.35M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z" />
              </svg>
            </div>
            <Select
              value={stage}
              onChange={(event) => setStage(event.target.value)}
              className="w-full sm:w-[200px]"
              aria-label="Filter by stage"
            >
              <option value="">All stages</option>
              {(metadata.data?.stages || []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
            <Button type="submit" variant="subtle">
              Search
            </Button>
            {(search || stage) && (
              <Button
                variant="ghost"
                onClick={() => {
                  setQuery('')
                  setSearch('')
                  setStage('')
                }}
              >
                Clear
              </Button>
            )}
          </form>
          <span className="text-[12.5px] text-ink-muted">
            {page.data ? `${page.data.totalElements} candidate${page.data.totalElements === 1 ? '' : 's'}` : ''}
          </span>
        </header>

        {page.error && !page.data ? (
          <ErrorState
            message={page.error.message}
            action={<Button onClick={() => page.reload()}>Try again</Button>}
          />
        ) : (
          <CandidateTable
            candidates={candidates}
            loading={page.loading && !page.data}
            onOpen={(candidate) => navigate(`/candidates/${candidate.id}`)}
            emptyAction={<Button onClick={() => setCreateOpen(true)}>New candidate</Button>}
          />
        )}
      </section>

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
