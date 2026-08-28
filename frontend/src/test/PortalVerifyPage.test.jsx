import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PortalVerifyPage } from '../pages/portal/PortalVerifyPage'
import { deviceStore } from '../services/deviceStore'
import { portalService } from '../services/portalService'

const TOKEN = 'tok_abc123'
const EMAIL = 'asha@example.com'

const CHALLENGE = {
  candidateName: 'Asha Rao',
  maskedEmail: 'a***@example.com',
  digits: 6,
  expiresInSeconds: 600,
  alreadyTrusted: false,
}

async function reachCodeStep(user) {
  await user.type(screen.getByLabelText('Your email address'), EMAIL)
  await user.click(screen.getByRole('button', { name: /Send me a code/ }))
  await screen.findByText('a***@example.com')
}

describe('PortalVerifyPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    deviceStore.clear(TOKEN)
  })

  it('starts by asking for the email and does not send a code unprompted', () => {
    const requestCode = vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)
    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)

    expect(screen.getByLabelText('Your email address')).toBeInTheDocument()
    expect(requestCode).not.toHaveBeenCalled()
  })

  it('sends the entered email to the backend, then shows the code boxes', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)
    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)

    const user = userEvent.setup()
    await reachCodeStep(user)

    expect(portalService.requestCode).toHaveBeenCalledWith(TOKEN, EMAIL)
    expect(screen.getAllByRole('textbox')).toHaveLength(6)
  })

  it('keeps the candidate on the email step and explains a mismatch', async () => {
    vi.spyOn(portalService, 'requestCode').mockRejectedValue(
      new Error("That email doesn't match this invitation."),
    )
    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Your email address'), 'wrong@example.com')
    await user.click(screen.getByRole('button', { name: /Send me a code/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent("doesn't match this invitation")
    expect(screen.getByLabelText('Your email address')).toBeInTheDocument()
    expect(screen.queryByLabelText('Digit 1')).not.toBeInTheDocument()
  })

  it('submits itself on the sixth digit and remembers the device', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)
    vi.spyOn(portalService, 'verifyCode').mockResolvedValue({ deviceToken: 'marker-1' })
    const onVerified = vi.fn()

    render(<PortalVerifyPage token={TOKEN} onVerified={onVerified} />)
    const user = userEvent.setup()
    await reachCodeStep(user)

    const boxes = screen.getAllByRole('textbox')
    for (let i = 0; i < 6; i += 1) {
      await user.type(boxes[i], String(i + 1))
    }

    await waitFor(() => expect(portalService.verifyCode).toHaveBeenCalledWith(TOKEN, '123456'))
    expect(deviceStore.get(TOKEN)).toBe('marker-1')
    expect(onVerified).toHaveBeenCalled()
  })

  it('accepts a code pasted into the first box', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)
    vi.spyOn(portalService, 'verifyCode').mockResolvedValue({ deviceToken: 'marker-2' })

    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)
    const user = userEvent.setup()
    await reachCodeStep(user)

    await user.click(screen.getAllByRole('textbox')[0])
    await user.paste('998877')

    await waitFor(() => expect(portalService.verifyCode).toHaveBeenCalledWith(TOKEN, '998877'))
  })

  it('clears the boxes and explains a wrong code', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)
    vi.spyOn(portalService, 'verifyCode').mockRejectedValue(new Error('That code is not right.'))

    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)
    const user = userEvent.setup()
    await reachCodeStep(user)

    await user.click(screen.getAllByRole('textbox')[0])
    await user.paste('111111')

    expect(await screen.findByRole('alert')).toHaveTextContent('That code is not right.')
    screen.getAllByRole('textbox').forEach((box) => expect(box).toHaveValue(''))
  })

  it('skips both steps when the device is already trusted', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue({ ...CHALLENGE, alreadyTrusted: true })
    const onVerified = vi.fn()

    render(<PortalVerifyPage token={TOKEN} onVerified={onVerified} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Your email address'), EMAIL)
    await user.click(screen.getByRole('button', { name: /Send me a code/ }))

    await waitFor(() => expect(onVerified).toHaveBeenCalled())
  })

  it('holds the resend button back for a moment so a slow email is not re-sent', async () => {
    vi.spyOn(portalService, 'requestCode').mockResolvedValue(CHALLENGE)

    render(<PortalVerifyPage token={TOKEN} onVerified={() => {}} />)
    const user = userEvent.setup()
    await reachCodeStep(user)

    expect(screen.getByRole('button', { name: /Resend in/ })).toBeDisabled()
  })
})
