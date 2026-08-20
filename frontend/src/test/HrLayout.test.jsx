import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { HrLayout } from '../layouts/HrLayout'

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { fullName: 'CloudFuze HR Admin', email: 'admin@cloudfuze.com' },
    logout: vi.fn(),
  }),
}))

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route element={<HrLayout />}>
          <Route path="/dashboard" element={<p>Dashboard content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('HrLayout sidebar', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('starts expanded with labels and the signed-in user', () => {
    renderLayout()
    expect(screen.getByText('Document Requests')).toBeInTheDocument()
    expect(screen.getByText('Verify and reject uploads')).toBeInTheDocument()
    expect(screen.getByText('CloudFuze HR Admin')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Collapse navigation' })).toBeInTheDocument()
  })

  it('collapses to an icon rail, keeping the links reachable by title', async () => {
    const user = userEvent.setup()
    renderLayout()

    await user.click(screen.getByRole('button', { name: 'Collapse navigation' }))

    // Labels and hints go, but every destination is still there and titled.
    expect(screen.queryByText('Verify and reject uploads')).not.toBeInTheDocument()
    expect(screen.getByTitle('Document Requests')).toBeInTheDocument()
    expect(screen.getByTitle('Log out')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Expand navigation' })).toBeInTheDocument()
    // The page itself keeps rendering while the rail animates.
    expect(screen.getByText('Dashboard content')).toBeInTheDocument()
  })

  it('remembers the collapsed choice across renders', async () => {
    const user = userEvent.setup()
    const first = renderLayout()
    await user.click(screen.getByRole('button', { name: 'Collapse navigation' }))
    expect(window.localStorage.getItem('cf_hr_sidebar_collapsed')).toBe('1')
    first.unmount()

    renderLayout()
    expect(screen.getByRole('button', { name: 'Expand navigation' })).toBeInTheDocument()
    expect(screen.queryByText('Verify and reject uploads')).not.toBeInTheDocument()
  })

  it('expands again from the rail', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem('cf_hr_sidebar_collapsed', '1')
    renderLayout()

    await user.click(screen.getByRole('button', { name: 'Expand navigation' }))
    expect(screen.getByText('Verify and reject uploads')).toBeInTheDocument()
    expect(window.localStorage.getItem('cf_hr_sidebar_collapsed')).toBe('0')
  })
})
