import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DocumentReviewPanel } from '../components/hr/DocumentReviewPanel'
import { ToastProvider } from '../context/ToastContext'

const DOCUMENTS = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    type: 'aadhaar_id',
    typeLabel: 'Aadhaar / Government ID',
    mandatory: true,
    status: 'submitted',
    filename: 'aadhaar.pdf',
    sizeBytes: 20480,
    version: 1,
    uploadedAt: '2026-08-01T10:00:00Z',
    downloadUrl: '/api/hr/documents/11111111-1111-1111-1111-111111111111/file',
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    type: 'pan_card',
    typeLabel: 'PAN Card',
    mandatory: true,
    status: 'verified',
    filename: 'pan.pdf',
    sizeBytes: 15360,
    version: 1,
    uploadedAt: '2026-08-01T10:05:00Z',
    reviewedBy: 'hr@cloudfuze.com',
    reviewedAt: '2026-08-01T11:00:00Z',
    downloadUrl: '/api/hr/documents/22222222-2222-2222-2222-222222222222/file',
  },
  {
    id: null,
    type: 'education_certificate',
    typeLabel: 'Education Certificate',
    mandatory: true,
    status: 'pending',
    downloadUrl: null,
  },
]

function renderPanel(props = {}) {
  return render(
    <ToastProvider>
      <DocumentReviewPanel documents={DOCUMENTS} {...props} />
    </ToastProvider>,
  )
}

describe('DocumentReviewPanel', () => {
  it('offers verify and reject only for submitted documents', () => {
    renderPanel()
    expect(screen.getAllByRole('button', { name: 'Verify' })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: 'Reject' })).toHaveLength(1)
  })

  it('shows who reviewed a checked-off document', () => {
    renderPanel()
    expect(screen.getByText(/Reviewed by hr@cloudfuze\.com on /)).toBeInTheDocument()
    // Not "Verified" yet - HR has not approved the candidate.
    expect(screen.getByText('Reviewed')).toBeInTheDocument()
    expect(screen.queryByText('Verified')).not.toBeInTheDocument()
  })

  it('upgrades the label to Verified once HR has approved', () => {
    renderPanel({ readOnly: true })
    expect(screen.getByText('Verified')).toBeInTheDocument()
    expect(screen.queryByText('Reviewed')).not.toBeInTheDocument()
  })

  it('marks documents that have not been reviewed yet', () => {
    renderPanel()
    // The status pill carries this now; the old "Not reviewed yet" line said
    // the same thing twice in the same row.
    expect(screen.getAllByText('Awaiting review').length).toBeGreaterThan(0)
  })

  it('shows documents still waiting on the candidate', () => {
    renderPanel()
    expect(screen.getByText('Waiting on candidate')).toBeInTheDocument()
  })

  it('hides review actions once the document stage is closed', () => {
    renderPanel({ readOnly: true })
    expect(screen.queryByRole('button', { name: 'Verify' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Re-verify' })).not.toBeInTheDocument()
  })

  it('lets HR undo a verify before the document stage closes', () => {
    renderPanel()
    expect(screen.getAllByRole('button', { name: 'Re-verify' })).toHaveLength(1)
  })
})
