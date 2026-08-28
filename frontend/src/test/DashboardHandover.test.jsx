import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { DashboardPage } from '../pages/hr/DashboardPage'
import { CandidatesPage } from '../pages/hr/CandidatesPage'
import { ToastProvider } from '../context/ToastContext'
import { CANDIDATE_FILTERS, candidateFilter } from '../utils/candidateFilters'

/*
 * The point of the tiles is that the number is a way in: clicking one has to
 * land on exactly the people it counted, with that filter showing as selected.
 * A tile that navigates to an unfiltered list, or to a filter meaning something
 * slightly different, is worse than no link at all - it quietly misreports who
 * is outstanding.
 */

/* Hoisted, because vi.mock's factory is lifted above the file and the fixtures
   have to exist by the time it runs. */
const { NEEDS_UPLOAD, IN_REVIEW, REJECTED, APPROVED, DONE, CANDIDATES } = vi.hoisted(() => {
  const row = (over) => ({
    id: over.id, name: over.name, email: `${over.id}@example.com`,
    role: 'Engineer', department: 'Product',
    stage: 'docs_pending', stageLabel: 'Documents Pending',
    documentsRequired: 3, documentsVerified: 0, documentsSubmitted: 0,
    documentsRejected: 0, documentsMissing: 0,
    offerStatus: null, offerPrepared: false, portalLinkActive: true,
    ...over,
  })

  const needsUpload = row({ id: 'c1', name: 'Asha Nair', documentsMissing: 2 })
  const inReview = row({ id: 'c2', name: 'Bala Reddy', documentsSubmitted: 1 })
  const rejected = row({ id: 'c3', name: 'Chetan Rao', documentsRejected: 1 })
  const approved = row({ id: 'c4', name: 'Kiran Shah', stage: 'docs_approved', documentsVerified: 3 })
  const done = row({ id: 'c5', name: 'Divya Menon', stage: 'offer_accepted', documentsVerified: 3 })

  return {
    NEEDS_UPLOAD: needsUpload,
    IN_REVIEW: inReview,
    REJECTED: rejected,
    APPROVED: approved,
    DONE: done,
    CANDIDATES: [needsUpload, inReview, rejected, approved, done],
  }
})

vi.mock('../services/hrService', () => ({
  hrService: {
    dashboardStats: vi.fn().mockResolvedValue({
      totalCandidates: 5,
      documentsPending: 2,
      awaitingReview: 1,
      verificationDone: 1,
      onboardingComplete: 1,
      recentCandidates: [],
    }),
    candidates: vi.fn().mockResolvedValue({ content: CANDIDATES }),
    metadata: vi.fn().mockResolvedValue({ documentTypes: [] }),
  },
}))

function OfferDeskStub() {
  return <p>offer desk: {useLocation().search}</p>
}

const renderConsole = (at = '/dashboard') => render(
  <ToastProvider>
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/candidates" element={<CandidatesPage />} />
        {/* Stood in for, so the test asserts where the tile sends you without
            dragging the whole offer desk and its data into this file. */}
        <Route path="/offers" element={<OfferDeskStub />} />
      </Routes>
    </MemoryRouter>
  </ToastProvider>,
)

const matches = (value) => CANDIDATES.filter(candidateFilter(value).match)

describe('candidate filters', () => {
  it('counts people waiting to upload, not documents', () => {
    // Two names behind the tile, though they owe three documents between them.
    expect(matches('docs_pending').map((c) => c.name)).toEqual(['Asha Nair', 'Chetan Rao'])
  })

  it('treats a rejected document as still owed', () => {
    expect(candidateFilter('docs_pending').match(NEEDS_UPLOAD)).toBe(true)
    expect(candidateFilter('docs_pending').match(REJECTED)).toBe(true)
  })

  it('keeps someone whose documents are all in the queue out of pending', () => {
    expect(candidateFilter('docs_pending').match(IN_REVIEW)).toBe(false)
    expect(candidateFilter('awaiting_review').match(IN_REVIEW)).toBe(true)
  })

  it('keeps a signed candidate out of verification done', () => {
    expect(candidateFilter('verified').match(APPROVED)).toBe(true)
    expect(candidateFilter('verified').match(DONE)).toBe(false)
  })

  /* Signed offers belong to the offer desk, which already sorts not-sent from
     sent from signed. A segment here would be a second place to keep in step. */
  it('offers no signed option, and falls back to all if asked for one', () => {
    expect(CANDIDATE_FILTERS.map((f) => f.value)).not.toContain('complete')
    expect(matches('complete')).toHaveLength(CANDIDATES.length)
  })

  /* Dropped on purpose: someone whose documents are all verified is unfinished
     but not waiting on anyone, so "active" claimed people who were already
     sitting under Verified. */
  it('offers no active option', () => {
    expect(CANDIDATE_FILTERS.map((f) => f.value)).not.toContain('active')
  })

  it('leaves an unknown filter showing everyone rather than nobody', () => {
    expect(matches('nonsense')).toHaveLength(CANDIDATES.length)
  })
})

describe('clicking a dashboard figure', () => {
  it('opens the candidate list narrowed to the people it counted', async () => {
    const user = userEvent.setup()
    renderConsole()

    await user.click(await screen.findByRole('button', { name: /documents pending/i }))

    expect(await screen.findByText('Asha Nair')).toBeInTheDocument()
    expect(screen.getByText('Chetan Rao')).toBeInTheDocument()
    expect(screen.queryByText('Bala Reddy')).not.toBeInTheDocument()
    expect(screen.queryByText('Divya Menon')).not.toBeInTheDocument()
  })

  it('arrives with that filter showing as the selected one', async () => {
    const user = userEvent.setup()
    renderConsole()

    await user.click(await screen.findByRole('button', { name: /awaiting review/i }))

    const segment = await screen.findByRole('button', { name: /^Awaiting review/ })
    expect(segment).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^All/ })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows the same number on the segment as the tile it came from', async () => {
    const user = userEvent.setup()
    renderConsole()

    // The tile said 2; the segment it opens has to say 2 and list two names.
    await user.click(await screen.findByRole('button', { name: /documents pending/i }))

    const segment = await screen.findByRole('button', { name: /^Documents pending/ })
    expect(segment).toHaveTextContent('2')
    // The count sits in its own element beside the word, so read the pair.
    expect(screen.getByText('records')).toHaveTextContent('2 records')
  })

  it('opens the whole list from the first tile', async () => {
    const user = userEvent.setup()
    renderConsole()

    await user.click(await screen.findByRole('button', { name: /all candidates/i }))

    expect(await screen.findByText('Asha Nair')).toBeInTheDocument()
    expect(screen.getByText('Divya Menon')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^All/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('sends the offer letter tile to the offer desk, not the records list', async () => {
    const user = userEvent.setup()
    renderConsole()

    await user.click(await screen.findByRole('button', { name: /offer letter signed/i }))

    expect(await screen.findByText('offer desk: ?state=signed')).toBeInTheDocument()
  })

  it('lets HR change their mind with the same control', async () => {
    const user = userEvent.setup()
    renderConsole('/candidates?filter=docs_pending')

    await screen.findByText('Asha Nair')
    expect(screen.queryByText('Divya Menon')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Verification done/ }))

    expect(await screen.findByText('Kiran Shah')).toBeInTheDocument()
    expect(screen.queryByText('Asha Nair')).not.toBeInTheDocument()
  })
})
