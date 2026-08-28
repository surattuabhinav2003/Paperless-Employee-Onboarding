import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { OffersPage } from '../pages/hr/OffersPage'

/*
 * The offer desk only lists candidates whose documents are fully verified, so
 * search has to respect that gate: finding someone by name must never pull an
 * unverified candidate onto a page where HR can send them an offer.
 */

const { CANDIDATES } = vi.hoisted(() => {
  const row = (over) => ({
    id: over.id, name: over.name, email: `${over.id}@example.com`,
    role: 'UX Designer', department: 'Design',
    stage: 'docs_approved', stageLabel: 'Verification done',
    documentsRequired: 2, documentsVerified: 2, documentsSubmitted: 0,
    documentsRejected: 0, documentsMissing: 0,
    offerStatus: null, offerPrepared: false,
    updatedAt: '2026-08-27T10:00:00Z',
    ...over,
  })

  return {
    CANDIDATES: [
      row({ id: 'c1', name: 'Farhan Qureshi' }),
      row({ id: 'c2', name: 'Aditya Rao', offerPrepared: true, offerStatus: 'sent' }),
      row({ id: 'c3', name: 'Divya Menon', stage: 'offer_accepted', offerPrepared: true, offerStatus: 'accepted' }),
      // Still collecting documents - must never surface here, search or not.
      row({ id: 'c4', name: 'Farhan Sheikh', stage: 'docs_pending', documentsVerified: 0, documentsMissing: 2 }),
    ],
  }
})

vi.mock('../services/hrService', () => ({
  hrService: {
    candidates: vi.fn().mockResolvedValue({ content: CANDIDATES }),
  },
}))

const renderPage = (at = '/offers') => render(
  <MemoryRouter initialEntries={[at]}>
    <Routes>
      <Route path="/offers" element={<OffersPage />} />
    </Routes>
  </MemoryRouter>,
)

describe('OffersPage search', () => {
  it('narrows the desk to the name typed', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    await user.type(screen.getByLabelText('Search offers'), 'Farhan')

    expect(await screen.findByText('Farhan Qureshi')).toBeInTheDocument()
    expect(screen.queryByText('Aditya Rao')).not.toBeInTheDocument()
  })

  it('will not pull an unverified candidate onto the desk', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Farhan Qureshi')
    await user.type(screen.getByLabelText('Search offers'), 'Farhan Sheikh')

    // Both share a first name; only the verified one may appear.
    expect(screen.queryByText('Farhan Sheikh')).not.toBeInTheDocument()
  })

  it('forgives a typo rather than coming up empty', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Divya Menon')
    await user.type(screen.getByLabelText('Search offers'), 'Diyva')

    expect(await screen.findByText('Divya Menon')).toBeInTheDocument()
  })

  it('counts each offer state for the query, not for the whole desk', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    expect(screen.getByRole('button', { name: /^All verified/ })).toHaveTextContent('3')

    await user.type(screen.getByLabelText('Search offers'), 'Farhan')

    expect(screen.getByRole('button', { name: /^All verified/ })).toHaveTextContent('1')
    expect(screen.getByRole('button', { name: /^Signed/ })).toHaveTextContent('0')
  })

  it('says so on a miss, and hands back a way to clear it', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    await user.type(screen.getByLabelText('Search offers'), 'Zebediah')

    expect(await screen.findByText('No match on the offer desk')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'clear the search' }))

    expect(await screen.findByText('Aditya Rao')).toBeInTheDocument()
  })

  it('keeps the search in the URL so it survives opening an offer', async () => {
    renderPage('/offers?q=Divya&state=signed')

    expect(await screen.findByText('Divya Menon')).toBeInTheDocument()
    expect(screen.getByLabelText('Search offers')).toHaveValue('Divya')
    expect(screen.getByRole('button', { name: /^Signed/ })).toHaveAttribute('aria-pressed', 'true')
  })
})
