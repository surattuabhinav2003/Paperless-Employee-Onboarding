import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { NocPage } from '../pages/hr/NocPage'
import { ToastProvider } from '../context/ToastContext'

/*
 * The NDA + NOC desk is not fed by the candidate pipeline - HR types in whoever
 * they are sending to - so the list is the only way to find a document, and
 * search is the only way through a long one.
 */

const { PACKETS, hrService } = vi.hoisted(() => {
  const packet = (over) => ({
    id: over.id,
    recipientName: over.recipientName,
    recipientEmail: over.recipientEmail,
    title: over.title ?? 'Consultant',
    status: 'sent',
    statusLabel: 'Out for signature',
    ndaFilename: 'nda.pdf',
    nocFilename: 'noc.pdf',
    pageCount: 4,
    sizeBytes: 120000,
    fields: [{ type: 'signature' }],
    sentAt: '2026-08-27T10:00:00Z',
    ...over,
  })

  const packets = [
    packet({ id: 'n1', recipientName: 'Farhan Qureshi', recipientEmail: 'farhan@cloudfuze.com' }),
    packet({ id: 'n2', recipientName: 'Aditya Rao', recipientEmail: 'aditya@cloudfuze.com' }),
    packet({ id: 'n3', recipientName: 'Divya Menon', recipientEmail: 'divya@cloudfuze.com', title: 'Advisor' }),
  ]

  return {
    PACKETS: packets,
    hrService: {
      nocList: vi.fn().mockResolvedValue({ content: packets }),
      metadata: vi.fn().mockResolvedValue({ nocRecipientDomains: ['cloudfuze.com'] }),
    },
  }
})

vi.mock('../services/hrService', () => ({ hrService }))

/* The new-document modal reaches for a toast, so the provider has to be here
   even though none of these tests open it. */
const renderPage = (at = '/noc') => render(
  <ToastProvider>
    <MemoryRouter initialEntries={[at]}>
      <Routes>
        <Route path="/noc" element={<NocPage />} />
      </Routes>
    </MemoryRouter>
  </ToastProvider>,
)

describe('NocPage search', () => {
  it('narrows the list to the name typed', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    await user.type(screen.getByLabelText('Search NDA and NOC documents'), 'Farhan')

    expect(await screen.findByText('Farhan Qureshi')).toBeInTheDocument()
    expect(screen.queryByText('Aditya Rao')).not.toBeInTheDocument()
  })

  it('finds a recipient by email as well as by name', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Divya Menon')
    await user.type(screen.getByLabelText('Search NDA and NOC documents'), 'divya@cloudfuze')

    expect(await screen.findByText('Divya Menon')).toBeInTheDocument()
    expect(screen.queryByText('Farhan Qureshi')).not.toBeInTheDocument()
  })

  it('forgives a typo rather than coming up empty', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Divya Menon')
    await user.type(screen.getByLabelText('Search NDA and NOC documents'), 'Diyva')

    expect(await screen.findByText('Divya Menon')).toBeInTheDocument()
  })

  it('counts what is on screen, not what came back from the server', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    expect(screen.getByText('3 documents')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search NDA and NOC documents'), 'Farhan')

    expect(await screen.findByText('1 document')).toBeInTheDocument()
  })

  it('says so on a miss and offers a way back', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Aditya Rao')
    await user.type(screen.getByLabelText('Search NDA and NOC documents'), 'Zebediah')

    expect(await screen.findByText('No match')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'clear the search' }))

    expect(await screen.findByText('Aditya Rao')).toBeInTheDocument()
  })

  it('reads the search out of the URL so it survives opening a record', async () => {
    renderPage('/noc?q=Divya&status=sent')

    expect(await screen.findByText('Divya Menon')).toBeInTheDocument()
    expect(screen.getByLabelText('Search NDA and NOC documents')).toHaveValue('Divya')
    // Status stays the server's to apply; it must reach the request unchanged.
    expect(hrService.nocList).toHaveBeenCalledWith({ status: 'sent' })
  })

  it('leaves the empty-list invitation alone when nothing has been created yet', async () => {
    hrService.nocList.mockResolvedValueOnce({ content: [] })
    renderPage()

    // Not "No match" - there is nothing here at all, which is a different thing.
    expect(await screen.findByText('Nothing here yet')).toBeInTheDocument()
    expect(PACKETS).toHaveLength(3)
  })
})
