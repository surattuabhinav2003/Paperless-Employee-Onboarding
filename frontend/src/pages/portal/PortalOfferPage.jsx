import { useEffect, useState } from 'react'
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
import { formatDateTime } from '../../utils/format'
import { offerStatusMeta } from '../../utils/status'

export function PortalOfferPage() {
  const { overview, reloadOverview, token } = useOutletContext()
  const toast = useToast()
  const [name, setName] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [accepting, setAccepting] = useState(false)
  const [nameError, setNameError] = useState(null)

  const offerStep = overview.steps.find((step) => step.key === 'offer')
  const locked = !overview.offerAvailable

  const { data: offer, error, loading, setData } = useAsync(
    () => (locked ? Promise.resolve(null) : portalService.offer(token)),
    [token, locked],
  )

  // Opening this page counts as viewing the offer, tracked server-side.
  useEffect(() => {
    if (!locked && offer?.prepared && offer.status === 'sent') {
      portalService
        .markOfferViewed(token)
        .then((updated) => setData(updated))
        .catch(() => {})
    }
  }, [locked, offer, token, setData])

  if (locked) {
    return (
      <LockedPanel
        title="Offer locked"
        reason={
          offerStep?.lockReason ||
          'Your documents are currently being reviewed by HR. The offer letter will become available once all required documents are approved.'
        }
      />
    )
  }

  if (loading && !offer) return <LoadingState label="Loading your offer letter" />
  if (error && !offer) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your offer letter" message={error.message} />
      </div>
    )
  }

  if (!offer?.prepared) {
    return (
      <div className="cf-card">
        <EmptyState
          icon="document"
          title="Your offer letter is being prepared"
          message={offer?.message || 'HR is finalising your offer letter. You will be able to review it here.'}
        />
      </div>
    )
  }

  const meta = offerStatusMeta(offer.status)
  const accepted = offer.status === 'accepted'

  const submitAcceptance = async () => {
    setAccepting(true)
    try {
      const updated = await portalService.acceptOffer(token, name.trim())
      setData(updated)
      setConfirmOpen(false)
      await reloadOverview()
      toast.success('Offer accepted', 'Bond signing is now unlocked as your final step.')
    } catch (err) {
      setConfirmOpen(false)
      toast.apiError(err, 'We could not record your acceptance')
    } finally {
      setAccepting(false)
    }
  }

  const startAcceptance = () => {
    if (name.trim().length < 3) {
      setNameError('Type your full name exactly as it appears on your offer letter')
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
              <h2 className="text-[16px] font-semibold text-ink">Offer letter</h2>
              <StatusPill label={meta.label} tone={meta.tone} />
            </div>
            <p className="mt-1 text-[12.5px] text-ink-muted">
              {offer.role} &middot; {offer.department} &middot; sent {formatDateTime(offer.sentAt)}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <a href={fileUrl(offer.downloadUrl)} target="_blank" rel="noreferrer">
              <Button variant="secondary" size="sm">
                Open in new tab
              </Button>
            </a>
          </div>
        </header>

        <div className="bg-surface-offwhite/70 p-3">
          <iframe
            title="Offer letter"
            src={fileUrl(offer.downloadUrl)}
            className="h-[520px] w-full rounded border border-surface-line bg-white"
          />
          <p className="mt-2 text-center text-[11.5px] text-ink-muted">
            Preview not loading? Use <span className="font-medium text-ink-body">Open in new tab</span> above
            to download and read the document.
          </p>
        </div>
      </section>

      {accepted ? (
        <section className="cf-card border-accent-green/40 bg-[#F3FCF7] p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full
              bg-accent-green text-white">
              <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2.4"
                strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
            <div>
              <h3 className="text-[15px] font-semibold text-ink">Offer accepted</h3>
              <p className="mt-1 text-[13px] leading-6 text-ink-body">
                Accepted by {offer.acceptedAt ? formatDateTime(offer.acceptedAt) : 'you'}. Bond signing is
                unlocked - it is the final step of your onboarding.
              </p>
            </div>
          </div>
        </section>
      ) : (
        <section className="cf-card p-5 sm:p-6">
          <h3 className="text-[15px] font-semibold text-ink">Accept your offer</h3>
          <p className="mt-1.5 text-[13px] leading-6 text-ink-muted">
            Read the offer letter above. When you are ready, type your full name and confirm - this is
            recorded as your electronic acceptance, with a timestamp and audit entry.
          </p>

          <div className="mt-5 grid gap-4 sm:max-w-md">
            <Field label="Your full name" htmlFor="acknowledgement-name" required error={nameError}>
              <TextInput
                id="acknowledgement-name"
                value={name}
                error={nameError}
                placeholder={overview.candidateName}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
              />
            </Field>
            <Checkbox
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
              label="I have read and accept the terms of this offer letter"
              description="You will still need to sign your employment bond as the final step."
            />
          </div>

          <div className="mt-5">
            <Button disabled={!agreed} onClick={startAcceptance}>
              Accept offer
            </Button>
          </div>
        </section>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="Confirm offer acceptance"
        confirmLabel="Yes, accept offer"
        loading={accepting}
        onConfirm={submitAcceptance}
        onClose={() => setConfirmOpen(false)}
        message={`We will record that ${name.trim()} accepted this offer letter now. This cannot be undone from the portal.`}
      />
    </div>
  )
}
