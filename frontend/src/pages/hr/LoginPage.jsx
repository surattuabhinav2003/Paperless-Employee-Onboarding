import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { Field, TextInput } from '../../components/ui/Field'
import { Logo } from '../../components/ui/Logo'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'

export function LoginPage() {
  const { login, isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

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
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-ink via-[#021A63] to-brand
        px-12 py-14 text-white lg:flex lg:flex-col">
        {/* Decorative brand texture, kept subtle per the guidelines. */}
        <svg className="pointer-events-none absolute inset-0 h-full w-full opacity-[0.15]" aria-hidden="true">
          <defs>
            <pattern id="cf-dots" width="28" height="28" patternUnits="userSpaceOnUse">
              <circle cx="2" cy="2" r="1.4" fill="white" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#cf-dots)" />
        </svg>

        <Logo onDark subtitle="HR Onboarding" />

        <div className="relative mt-auto max-w-lg">
          <h1 className="text-[34px] font-semibold leading-[1.25] tracking-[-0.02em]">
            Paperless onboarding, from documents to signed bond.
          </h1>
          <p className="mt-4 text-[14.5px] leading-7 text-white/75">
            One secure link per candidate covers document collection, offer acceptance and bond signing
            through SignatureOne - with every stage gated and audited server-side.
          </p>
          <ul className="mt-8 space-y-3.5">
            {[
              'Documents verified individually, with re-upload only where rejected',
              'Offer unlocks the moment every required document is approved',
              'Bond signing is always the final, audited step',
            ].map((line) => (
              <li key={line} className="flex items-start gap-3 text-[13.5px] text-white/85">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full
                  bg-white/15 ring-1 ring-white/25">
                  <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none" stroke="currentColor"
                    strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                </span>
                {line}
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="flex items-center justify-center bg-white px-5 py-12 sm:px-10">
        <div className="w-full max-w-[400px]">
          <div className="lg:hidden">
            <Logo subtitle="HR Onboarding" />
          </div>
          <h2 className="mt-8 text-[24px] font-semibold tracking-[-0.01em] text-ink lg:mt-0">
            Sign in to the HR console
          </h2>
          <p className="mt-2 text-[13.5px] leading-6 text-ink-muted">
            Use your CloudFuze HR account. Candidates do not sign in - they use their personal portal link.
          </p>

          <form className="mt-8 space-y-4" onSubmit={submit} noValidate>
            <Field label="Work email" htmlFor="email" error={errors.email}>
              <TextInput
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                error={errors.email}
                placeholder="admin@cloudfuze.com"
                onChange={(event) => setEmail(event.target.value)}
              />
            </Field>
            <Field label="Password" htmlFor="password" error={errors.password}>
              <TextInput
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                error={errors.password}
                placeholder="••••••••"
                onChange={(event) => setPassword(event.target.value)}
              />
            </Field>
            <Button type="submit" className="w-full" size="lg" loading={submitting}>
              Sign in
            </Button>
          </form>

          <p className="mt-8 text-[12px] leading-5 text-ink-muted">
            Trouble signing in? Contact your CloudFuze IT administrator. Sessions expire automatically and
            no candidate data is stored in your browser.
          </p>
        </div>
      </div>
    </div>
  )
}
