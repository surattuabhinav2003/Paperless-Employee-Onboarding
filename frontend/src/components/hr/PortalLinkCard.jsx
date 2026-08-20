import { useState } from 'react'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { CopyField } from '../ui/CopyField'
import { StatusPill } from '../ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatDateTime } from '../../utils/format'

/**
 * The candidate's onboarding link.
 * <p>
 * The live link can be shown on demand - it is stored encrypted alongside its
 * lookup hash - so HR can copy or re-send it without invalidating it. Every
 * reveal is audited, and generating a new link still kills the old one.
 */
export function PortalLinkCard({ candidate, onChanged }) {
  const toast = useToast()
  const [link, setLink] = useState(null)
  const [regenerated, setRegenerated] = useState(false)
  const [busy, setBusy] = useState(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const linkActive = candidate.portalLinkActive

  const show = async () => {
    setBusy('show')
    try {
      setLink(await hrService.portalLink(candidate.id))
      setRegenerated(false)
    } catch (error) {
      toast.apiError(error, 'Could not show the link')
    } finally {
      setBusy(null)
    }
  }

  const resend = async () => {
    setBusy('resend')
    try {
      setLink(await hrService.resendInvite(candidate.id))
      setRegenerated(true)
      toast.success('Invitation resent', `A fresh link was emailed to ${candidate.email}.`)
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not resend the invitation')
    } finally {
      setBusy(null)
    }
  }

  const regenerate = async () => {
    setBusy('regenerate')
    try {
      setLink(await hrService.regenerateToken(candidate.id))
      setRegenerated(true)
      toast.success('New link generated', 'The previous link stopped working immediately.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not generate a new link')
    } finally {
      setBusy(null)
      setConfirmOpen(false)
    }
  }

  return (
    <section className="cf-card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-[15px] font-semibold text-ink">Onboarding link</h3>
          <p className="mt-1 max-w-xl text-[12.5px] leading-5 text-ink-muted">
            One link covers documents, offer and bond. Show it to copy or share it directly, or resend the
            invitation email.
          </p>
        </div>
        <StatusPill
          label={linkActive ? 'Link active' : 'Link expired'}
          tone={linkActive ? 'green' : 'red'}
        />
      </div>

      <dl className="mt-4 grid gap-x-6 gap-y-2.5 text-[12.5px] sm:grid-cols-3">
        <div className="min-w-0">
          <dt className="text-ink-muted">Emailed to</dt>
          <dd className="mt-0.5 truncate font-medium text-ink-body" title={candidate.email}>
            {candidate.email}
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Last sent</dt>
          <dd className="mt-0.5 font-medium text-ink-body">
            {candidate.invitationSentAt ? formatDateTime(candidate.invitationSentAt) : 'Not sent'}
          </dd>
        </div>
        <div>
          <dt className="text-ink-muted">Expires</dt>
          <dd className="mt-0.5 font-medium text-ink-body">{formatDateTime(candidate.tokenExpiresAt)}</dd>
        </div>
      </dl>

      {link ? (
        <div
          className={`mt-4 rounded-card border p-4 ${
            regenerated ? 'border-accent-green/30 bg-[#F3FCF7]' : 'border-brand/25 bg-brand-tint/50'
          }`}
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className={`text-[12.5px] font-semibold ${regenerated ? 'text-[#0E7A47]' : 'text-brand'}`}>
              {regenerated ? 'New link - the previous one no longer works' : 'Active link'}
            </p>
            <button
              type="button"
              onClick={() => setLink(null)}
              className="text-[12px] font-medium text-ink-muted transition hover:text-ink"
            >
              Hide
            </button>
          </div>
          <p className="mt-1 text-[12px] leading-5 text-ink-muted">
            Valid until {formatDateTime(link.expiresAt)}. Treat it like a password - it opens this
            candidate's onboarding.
          </p>
          <CopyField className="mt-3" value={link.portalUrl} />
        </div>
      ) : (
        <p className="mt-4 text-[12px] text-ink-muted">
          The link is hidden until you ask for it, and every time it is shown gets recorded in the audit
          trail.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2.5">
        {!link && (
          <Button
            loading={busy === 'show'}
            disabled={!linkActive}
            onClick={show}
            title={linkActive ? undefined : 'The link has expired - generate a new one'}
          >
            Show link
          </Button>
        )}
        <Button variant="subtle" loading={busy === 'resend'} onClick={resend}>
          Resend invitation email
        </Button>
        <Button variant="secondary" loading={busy === 'regenerate'} onClick={() => setConfirmOpen(true)}>
          Generate new link
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="Generate a new onboarding link?"
        confirmLabel="Generate new link"
        loading={busy === 'regenerate'}
        onConfirm={regenerate}
        onClose={() => setConfirmOpen(false)}
        message={`Any link ${candidate.name} already has will stop working immediately, and the new one is
          emailed to them. Use this only if their link needs replacing - to simply copy the current link,
          close this and choose Show link.`}
      />
    </section>
  )
}
