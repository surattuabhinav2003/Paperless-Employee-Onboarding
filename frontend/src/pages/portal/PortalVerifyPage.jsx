import { useEffect, useRef, useState } from 'react'
import { Logo } from '../../components/ui/Logo'
import { deviceStore } from '../../services/deviceStore'
import { portalService } from '../../services/portalService'
import '../../styles/verify.css'

const DIGITS = 6

/**
 * The screen a candidate sees the first time they open their link on a new
 * device.
 *
 * <p>Two quick steps. First they confirm the email the link was sent to - the
 * backend refuses to send a code to anyone who cannot name the right address, so
 * a forwarded or pasted link is not enough on its own. Then they type the code,
 * which submits itself on the sixth digit and accepts a pasted value.
 */
export function PortalVerifyPage({ token, onVerified }) {
  const [step, setStep] = useState('email') // 'email' | 'code'
  const [email, setEmail] = useState('')
  const [challenge, setChallenge] = useState(null)
  const [digits, setDigits] = useState(Array(DIGITS).fill(''))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [resendIn, setResendIn] = useState(0)
  const emailInput = useRef(null)
  const inputs = useRef([])

  useEffect(() => { emailInput.current?.focus() }, [])

  useEffect(() => {
    if (resendIn <= 0) return undefined
    const timer = setTimeout(() => setResendIn((current) => current - 1), 1000)
    return () => clearTimeout(timer)
  }, [resendIn])

  /* Step one: name the address. On success the code has already been sent, so
     we move straight to the boxes. */
  const sendCode = async (address) => {
    setBusy(true)
    setError(null)
    try {
      const result = await portalService.requestCode(token, address)
      if (result.alreadyTrusted) {
        onVerified()
        return
      }
      setChallenge(result)
      setStep('code')
      setDigits(Array(DIGITS).fill(''))
      setResendIn(30)
      setTimeout(() => inputs.current[0]?.focus(), 0)
    } catch (caught) {
      setError(caught.message)
    } finally {
      setBusy(false)
    }
  }

  const onEmailSubmit = (event) => {
    event.preventDefault()
    const trimmed = email.trim()
    if (trimmed) sendCode(trimmed)
  }

  const submitCode = async (code) => {
    setBusy(true)
    setError(null)
    try {
      const { deviceToken } = await portalService.verifyCode(token, code)
      deviceStore.set(token, deviceToken)
      onVerified()
    } catch (caught) {
      setError(caught.message)
      setDigits(Array(DIGITS).fill(''))
      setBusy(false)
      inputs.current[0]?.focus()
    }
  }

  const setDigit = (index, value) => {
    const clean = value.replace(/\D/g, '')
    if (!clean) {
      const next = [...digits]
      next[index] = ''
      setDigits(next)
      return
    }

    // A pasted or typed run of digits fills forward from here.
    const next = [...digits]
    for (let i = 0; i < clean.length && index + i < DIGITS; i += 1) {
      next[index + i] = clean[i]
    }
    setDigits(next)

    const landed = Math.min(index + clean.length, DIGITS - 1)
    inputs.current[landed]?.focus()

    // every(), not a length check on the joined string: a gap in the middle
    // collapses on join and would look like a complete code.
    if (next.every(Boolean)) {
      submitCode(next.join(''))
    }
  }

  const onKeyDown = (index, event) => {
    if (event.key === 'Backspace' && !digits[index] && index > 0) {
      inputs.current[index - 1]?.focus()
    }
    if (event.key === 'ArrowLeft' && index > 0) inputs.current[index - 1]?.focus()
    if (event.key === 'ArrowRight' && index < DIGITS - 1) inputs.current[index + 1]?.focus()
  }

  return (
    <div className="pv">
      <div className="pv-card">
        <div className="pv-logo"><Logo subtitle="Onboarding" /></div>

        <span className="pv-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
            strokeLinecap="round" strokeLinejoin="round">
            {step === 'email' ? (
              <>
                <rect x="3" y="11" width="18" height="10" rx="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </>
            ) : (
              <>
                <rect x="3" y="5" width="18" height="14" rx="2" />
                <path d="m3 7 9 6 9-6" />
              </>
            )}
          </svg>
        </span>

        {step === 'email' ? (
          <>
            <h1>Confirm it&apos;s you</h1>
            <p className="pv-lede">
              Enter the email address your onboarding invitation was sent to. We&apos;ll email
              you a 6-digit code to continue.
            </p>

            <form className="pv-form" onSubmit={onEmailSubmit} noValidate>
              <input
                ref={emailInput}
                className={`pv-email${error ? ' is-wrong' : ''}`}
                type="email"
                inputMode="email"
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                disabled={busy}
                aria-label="Your email address"
                onChange={(event) => { setEmail(event.target.value); setError(null) }}
              />
              {error && <p className="pv-error" role="alert">{error}</p>}
              <button type="submit" className="pv-submit" disabled={busy || !email.trim()}>
                {busy ? 'Sending code...' : 'Send me a code'}
              </button>
            </form>

            <p className="pv-note">
              We ask for this once on a new device, then remember it for 30 days.
            </p>
          </>
        ) : (
          <>
            <h1>Check your email</h1>
            <p className="pv-lede">
              We sent a 6-digit code to <b>{challenge?.maskedEmail || 'your email'}</b>. It
              expires in 10 minutes.
            </p>

            <div className="pv-boxes" onPaste={(event) => {
              const pasted = event.clipboardData.getData('text').replace(/\D/g, '')
              if (pasted) {
                event.preventDefault()
                setDigit(0, pasted)
              }
            }}>
              {digits.map((digit, index) => (
                <input
                  key={index}
                  ref={(el) => { inputs.current[index] = el }}
                  className={`pv-box${error ? ' is-wrong' : ''}`}
                  type="text"
                  inputMode="numeric"
                  autoComplete={index === 0 ? 'one-time-code' : 'off'}
                  maxLength={DIGITS}
                  value={digit}
                  disabled={busy}
                  aria-label={`Digit ${index + 1}`}
                  onChange={(event) => setDigit(index, event.target.value)}
                  onKeyDown={(event) => onKeyDown(index, event)}
                />
              ))}
            </div>

            {error && <p className="pv-error" role="alert">{error}</p>}
            {busy && <p className="pv-checking">Checking...</p>}

            <div className="pv-foot">
              <span>Didn&apos;t get it? Check your spam folder.</span>
              <button
                type="button"
                className="pv-resend"
                disabled={resendIn > 0 || busy}
                onClick={() => sendCode(email.trim())}
              >
                {resendIn > 0 ? `Resend in ${resendIn}s` : 'Send it again'}
              </button>
            </div>

            <button type="button" className="pv-back" onClick={() => { setStep('email'); setError(null) }}>
              Use a different email
            </button>
          </>
        )}
      </div>
    </div>
  )
}
