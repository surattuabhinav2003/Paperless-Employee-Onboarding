import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { LockedPanel } from '../../components/portal/LockedPanel'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../../components/ui/EmptyState'
import { Checkbox, Field, TextInput } from '../../components/ui/Field'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { fileUrl } from '../../services/apiClient'
import { portalService } from '../../services/portalService'
import { formatDateTime, titleCase } from '../../utils/format'
import { bondStatusMeta } from '../../utils/status'

export function PortalBondPage() {
  const { overview, reloadOverview, token } = useOutletContext()
  const toast = useToast()
  const [name, setName] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [signing, setSigning] = useState(false)
  const [nameError, setNameError] = useState(null)

  const bondStep = overview.steps.find((step) => step.key === 'bond')
  const locked = !overview.bondAvailable

  const { data: bond, error, loading, setData } = useAsync(
    () => (locked ? Promise.resolve(null) : portalService.bond(token)),
    [token, locked],
  )

  if (locked) {
    return (
      <LockedPanel
        title="Bond locked"
        reason={
          bondStep?.lockReason || 'Please accept your offer letter before proceeding to bond signing.'
        }
      />
    )
  }

  if (loading && !bond) return <LoadingState label="Loading your bond document" />
  if (error && !bond) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your bond" message={error.message} />
      </div>
    )
  }

  if (!bond?.prepared) {
    return (
      <div className="cf-card">
        <EmptyState
          icon="document"
          title="Your bond is being prepared"
          message={bond?.message || 'HR is preparing your employment bond for signature.'}
        />
      </div>
    )
  }

  const meta = bondStatusMeta(bond.status)
  const signed = bond.status === 'signed'

  const sign = async () => {
    setSigning(true)
    try {
      const updated = await portalService.signBond(token, name.trim())
      setData(updated)
      setConfirmOpen(false)
      await reloadOverview()
      toast.success('Bond signed', 'Your onboarding is complete. Welcome to CloudFuze.')
    } catch (err) {
      setConfirmOpen(false)
      toast.apiError(err, 'Signing did not complete')
    } finally {
      setSigning(false)
    }
  }

  const startSigning = () => {
    if (name.trim().length < 3) {
      setNameError('Type your full legal name to sign')
      return
    }
    setNameError(null)
    setConfirmOpen(true)
  }

  return (
    <div className="space-y-5">
      <section className="cf-card overflow-hidden">
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-surface-line px-5 py-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-[16px] font-semibold text-ink">Employment bond</h2>
              <StatusPill label={meta.label} tone={meta.tone} />
            </div>
            <p className="mt-1 text-[12.5px] text-ink-muted">
              Document version {bond.documentVersion} &middot; signing by{' '}
              {titleCase(bond.signatureProvider === 'mock' ? 'SignatureOne (sandbox)' : 'SignatureOne')}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <a href={fileUrl(bond.downloadUrl)} target="_blank" rel="noreferrer">
              <Button variant="secondary" size="sm">
                Open in new tab
              </Button>
            </a>
            {bond.signedDownloadUrl && (
              <a href={fileUrl(bond.signedDownloadUrl)} target="_blank" rel="noreferrer">
                <Button size="sm">Download signed copy</Button>
              </a>
            )}
          </div>
        </header>

        <div className="bg-surface-offwhite/70 p-3">
          <iframe
            title="Employment bond"
            src={fileUrl(bond.downloadUrl)}
            className="h-[520px] w-full rounded border border-surface-line bg-white"
          />
          <p className="mt-2 text-center text-[11.5px] text-ink-muted">
            Preview not loading? Use <span className="font-medium text-ink-body">Open in new tab</span> above
            to download and read the document.
          </p>
        </div>
      </section>

      {signed ? (
        <section className="cf-card border-accent-green/40 bg-[#F3FCF7] p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full
              bg-accent-green text-white">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2.4"
                strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
            <div className="min-w-0">
              <h3 className="text-[15px] font-semibold text-ink">Onboarding Complete</h3>
              <p className="mt-1 text-[13px] leading-6 text-ink-body">
                Signed by {bond.signerName} on {formatDateTime(bond.signedAt)}.
              </p>
              <dl className="mt-3 grid gap-x-6 gap-y-1.5 text-[12.5px] sm:grid-cols-2">
                <div className="flex gap-2">
                  <dt className="text-ink-muted">Signature reference</dt>
                  <dd className="font-mono text-[11.5px] text-ink-body">{bond.signatureRef}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="text-ink-muted">Document version</dt>
                  <dd className="text-ink-body">{bond.documentVersion}</dd>
                </div>
              </dl>
            </div>
          </div>
        </section>
      ) : (
        <section className="cf-card p-5 sm:p-6">
          <h3 className="text-[15px] font-semibold text-ink">Sign with SignatureOne</h3>
          <p className="mt-1.5 text-[13px] leading-6 text-ink-muted">
            Read the bond above. Typing your full legal name and confirming creates a legally binding
            electronic signature through SignatureOne. Your name, timestamp, IP address and document
            version are recorded in the signature audit trail.
          </p>

          {bond.status === 'failed' && bond.message && (
            <p className="mt-4 rounded border border-accent-red/30 bg-[#FFECEC] px-3.5 py-2.5 text-[12.5px]
              text-[#8E1010]">
              {bond.message}
            </p>
          )}

          <div className="mt-5 grid gap-4 sm:max-w-md">
            <Field label="Your full legal name" htmlFor="signer-name" required error={nameError}>
              <TextInput
                id="signer-name"
                value={name}
                error={nameError}
                placeholder={overview.candidateName}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
                className="font-medium"
              />
            </Field>
            <div className="rounded border border-surface-line bg-surface-offwhite/60 px-3.5 py-3">
              <p className="text-[11.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">
                Signature preview
              </p>
              <p className="mt-1.5 truncate font-serif text-[22px] italic text-brand">
                {name.trim() || overview.candidateName}
              </p>
            </div>
            <Checkbox
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
              label="I have read the bond and agree to sign it electronically"
              description="This is the final step of your onboarding."
            />
          </div>

          <div className="mt-5">
            <Button disabled={!agreed} onClick={startSigning}>
              Sign bond
            </Button>
          </div>
        </section>
      )}

      {bond.auditTrail?.length > 0 && (
        <section className="cf-card p-5 sm:p-6">
          <h3 className="text-[14.5px] font-semibold text-ink">Signature activity</h3>
          <ol className="mt-4 space-y-3.5">
            {bond.auditTrail.map((event, index) => (
              <li key={`${event.eventType}-${index}`} className="flex gap-3">
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand" />
                <div className="min-w-0">
                  <p className="text-[13px] font-medium text-ink">{titleCase(event.eventType)}</p>
                  <p className="text-[12px] text-ink-muted">
                    {formatDateTime(event.occurredAt)}
                    {event.actor ? ` · ${event.actor}` : ''}
                  </p>
                  {event.detail && (
                    <p className="mt-0.5 text-[12.5px] leading-5 text-ink-body">{event.detail}</p>
                  )}
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="Confirm electronic signature"
        confirmLabel="Sign bond now"
        loading={signing}
        onConfirm={sign}
        onClose={() => setConfirmOpen(false)}
        message={`${name.trim()} will electronically sign bond ${bond.documentVersion} through SignatureOne. This completes your onboarding and cannot be undone.`}
      />
    </div>
  )
}
