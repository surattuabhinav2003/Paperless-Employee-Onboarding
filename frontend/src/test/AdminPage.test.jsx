import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdminPage } from '../pages/hr/AdminPage'
import { ToastProvider } from '../context/ToastContext'

/* Everything an admin configures now lives behind one sidebar entry, so the
   tabs are the only way to reach the panels - if they stop switching, the
   settings become unreachable rather than merely awkward. */
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { fullName: 'Abhinav', email: 'abhinav@cloudfuze.com' }, logout: vi.fn() }),
}))

vi.mock('../services/hrService', () => ({
  hrService: {
    adminUsers: vi.fn().mockResolvedValue([
      { id: 'u1', email: 'erik@cloudfuze.com', fullName: 'Erik', role: 'admin', roleLabel: 'Administrator' },
    ]),
    setUserRole: vi.fn(),
    candidateFields: vi.fn().mockResolvedValue([
      { code: 'blood_group', label: 'Blood group', groupLabel: 'Personal', enabled: true, required: false },
    ]),
    setCandidateField: vi.fn(),
    customFields: vi.fn().mockResolvedValue([
      {
        id: 'f1', code: 't_shirt_size', label: 'T-shirt size', type: 'select',
        typeLabel: 'Choice from a list', options: ['Small', 'Large'], group: 'additional',
        groupLabel: 'Additional details', enabled: true, required: true, archived: false,
      },
    ]),
    addCustomField: vi.fn(),
    updateCustomField: vi.fn(),
    removeCustomField: vi.fn(),
    restoreCustomField: vi.fn(),
    documentTypes: vi.fn().mockResolvedValue([
      {
        id: 'd1', code: 'police_verification', label: 'Police Verification',
        group: 'identity', groupLabel: 'Identity & personal', description: null,
        enabled: true, archived: false, position: 0,
      },
    ]),
    addDocumentType: vi.fn(),
    updateDocumentType: vi.fn(),
    removeDocumentType: vi.fn(),
    restoreDocumentType: vi.fn(),
  },
}))

const renderPage = () => render(
  <ToastProvider>
    <AdminPage />
  </ToastProvider>,
)

describe('AdminPage', () => {
  it('opens on people & access', async () => {
    renderPage()
    expect(await screen.findByText('erik@cloudfuze.com')).toBeInTheDocument()
    expect(screen.queryByText('Blood group')).not.toBeInTheDocument()
  })

  it('switches to the detail fields without leaving the page', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('erik@cloudfuze.com')

    await user.click(screen.getByRole('button', { name: 'Candidate detail fields' }))

    expect(await screen.findByText('Blood group')).toBeInTheDocument()
    // The admin-created fields share the tab with the built-in ones.
    expect(await screen.findByText('T-shirt size')).toBeInTheDocument()
    expect(screen.queryByText('erik@cloudfuze.com')).not.toBeInTheDocument()
    // One heading for the whole area, not one per panel.
    await waitFor(() => expect(screen.getByText('Admin settings')).toBeInTheDocument())
  })

  it('reaches the document types from the same page', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('erik@cloudfuze.com')

    await user.click(screen.getByRole('button', { name: 'Document types' }))

    expect(await screen.findByText('Police Verification')).toBeInTheDocument()
    expect(screen.queryByText('erik@cloudfuze.com')).not.toBeInTheDocument()
  })
})
