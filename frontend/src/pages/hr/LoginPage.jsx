import { useCallback, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import '../../styles/login.css'

const STEPS = [
  {
    n: '01',
    title: 'Details & documents',
    body: 'The candidate fills in their record and uploads exactly what you asked for.',
  },
  {
    n: '02',
    title: 'Offer letter',
    body: 'Unlocks the moment every required document is verified. Accepting it completes onboarding.',
  },
]

/**
 * Sign in. The only screen in the app with no data on it, so it carries the
 * brand instead of the console's flat grid.
 *
 * Styled by `styles/login.css` rather than utility classes - this page needs an
 * animated gradient angle, a mask-composited border, pointer-tracked light and
 * real grain, none of which express well as utilities. Nothing here shares a
 * component with the rest of the app, so the console is unaffected.
 */
export function LoginPage() {
  const { login, isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  /* The spotlight is two custom properties; writing them straight to the node
     keeps the pointer out of React state, so moving the mouse never re-renders. */
  const trackPointer = useCallback((event) => {
    const box = event.currentTarget.getBoundingClientRect()
    event.currentTarget.style.setProperty('--mx', `${event.clientX - box.left}px`)
    event.currentTarget.style.setProperty('--my', `${event.clientY - box.top}px`)
  }, [])

  if (isAuthenticated && !isLoading) {
    return <Navigate to={location.state?.from || '/dashboard'} replace />
  }

  const submit = async (event) => {
    event.preventDefault()
    const nextErrors = {}
    if (!email.trim()) nextErrors.email = 'Email is required'
    if (!password) nextErrors.password = 'Password is required'
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) return

    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(location.state?.from || '/dashboard', { replace: true })
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      }
      toast.error('Sign in failed', error.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="lg" onPointerMove={trackPointer}>
      <span className="lg-orb lg-orb--blue" aria-hidden="true" />
      <span className="lg-orb lg-orb--bright" aria-hidden="true" />
      <span className="lg-orb lg-orb--teal" aria-hidden="true" />
      <span className="lg-spot" aria-hidden="true" />

      <header className="lg-head">
        <div className="lg-brand">
          <span className="lg-mark">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M17.5 19H7a4.5 4.5 0 0 1-.4-8.98A6 6 0 0 1 18 9.5a4.75 4.75 0 0 1-.5 9.5Z" />
            </svg>
          </span>
          <span>
            <span className="lg-wordmark">CloudFuze</span>
            <span className="lg-micro">HR Onboarding</span>
          </span>
        </div>

        <span className="lg-status lg-micro">
          <i aria-hidden="true" />
          Server-enforced workflow
        </span>
      </header>

      <main className="lg-body">
        <section className="lg-pitch">
          <p className="lg-eyebrow lg-micro">
            <b aria-hidden="true" />
            Paperless onboarding
          </p>

          <h1 className="lg-title">
            One secure link.
            <span>Zero paperwork.</span>
          </h1>

          <p className="lg-lede">
            Every candidate moves from documents to accepted offer through a single link. No passwords, no
            email attachments, no chasing.
          </p>

          <ol className="lg-flow">
            {STEPS.map((step) => (
              <li key={step.n}>
                <h3>
                  <em className="lg-micro">{step.n}</em>
                  {step.title}
                </h3>
                <p>{step.body}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="lg-card">
          <p className="lg-kicker lg-micro">
            <b aria-hidden="true" />
            HR console
          </p>
          <h2>Sign in</h2>
          <p className="lg-note">Candidates never sign in - they open their own portal link.</p>

          <form className="lg-form" onSubmit={submit} noValidate>
            <div className="lg-field">
              <label className="lg-micro" htmlFor="email">Work email</label>
              <div className="lg-input-wrap">
                <input
                  id="email"
                  className="lg-input"
                  type="email"
                  autoComplete="username"
                  value={email}
                  aria-invalid={errors.email ? 'true' : undefined}
                  placeholder="you@cloudfuze.com"
                  onChange={(event) => setEmail(event.target.value)}
                />
              </div>
              {errors.email && <p className="lg-error">{errors.email}</p>}
            </div>

            <div className="lg-field">
              <label className="lg-micro" htmlFor="password">Password</label>
              <div className="lg-input-wrap">
                <input
                  id="password"
                  className="lg-input lg-input--reveal"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  aria-invalid={errors.password ? 'true' : undefined}
                  placeholder="Your password"
                  onChange={(event) => setPassword(event.target.value)}
                />
                <button
                  type="button"
                  className="lg-reveal"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword((prev) => !prev)}
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
                    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    {showPassword ? (
                      <>
                        <path d="M3 3l18 18" />
                        <path d="M10.6 5.2A9.9 9.9 0 0 1 12 5c5 0 9 4.5 9 7 0 .9-.5 2-1.4 3.1M6.2 6.7C3.9
                          8.2 3 10.2 3 12c0 2.5 4 7 9 7 1.6 0 3-.4 4.3-1.1" />
                        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
                      </>
                    ) : (
                      <>
                        <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" />
                        <circle cx="12" cy="12" r="2.6" />
                      </>
                    )}
                  </svg>
                </button>
              </div>
              {errors.password && <p className="lg-error">{errors.password}</p>}
            </div>

            <button type="submit" className="lg-submit" disabled={submitting}>
              <span>
                {submitting && <i className="lg-spinner" aria-hidden="true" />}
                {submitting ? 'Signing in' : 'Sign in'}
              </span>
            </button>
          </form>

          <p className="lg-fine">
            Sessions expire automatically and no candidate data is kept in your browser. Trouble signing in?
            Contact your CloudFuze IT administrator.
          </p>
        </section>
      </main>
    </div>
  )
}
