import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProgressTracker } from '../components/portal/ProgressTracker'

const CURRENT_DOCUMENTS = {
  key: 'documents',
  title: 'Details & Documents',
  state: 'current',
  statusText: '2 documents still to upload',
}

const COMPLETED_OFFER = {
  key: 'offer',
  title: 'Offer Letter',
  state: 'completed',
  statusText: 'Accepted',
}

describe('ProgressTracker', () => {
  it('shows the step the candidate is on', () => {
    render(<ProgressTracker steps={[CURRENT_DOCUMENTS]} />)
    expect(screen.getByText('Details & Documents')).toBeInTheDocument()
    expect(screen.getByText('2 documents still to upload')).toBeInTheDocument()
    expect(screen.getByText('Your step now')).toBeInTheDocument()
  })

  it('never renders stages the candidate has not reached', () => {
    // The backend only sends the current step, so nothing else can leak into the UI.
    render(<ProgressTracker steps={[CURRENT_DOCUMENTS]} />)
    expect(screen.queryByText('Offer Letter')).not.toBeInTheDocument()
    expect(screen.queryByText(/Available after/)).not.toBeInTheDocument()
  })

  it('marks a finished step as completed rather than current', () => {
    render(<ProgressTracker steps={[COMPLETED_OFFER]} />)
    expect(screen.getByText('Offer Letter')).toBeInTheDocument()
    expect(screen.getByText('Accepted')).toBeInTheDocument()
    expect(screen.queryByText('Your step now')).not.toBeInTheDocument()
  })

  it('renders nothing when there is no step to show', () => {
    const { container } = render(<ProgressTracker steps={[]} />)
    expect(container).toBeEmptyDOMElement()
  })
})
