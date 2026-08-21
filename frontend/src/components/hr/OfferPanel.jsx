import { useState } from 'react'
import { Button } from '../ui/Button'
import { DocumentViewer } from '../ui/DocumentViewer'
import { FileDropzone } from '../ui/FileDropzone'
import { Field, TextInput } from '../ui/Field'
import { StatusPill } from '../ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatBytes, formatDateTime } from '../../utils/format'
import { offerStatusMeta } from '../../utils/status'

/**
 * Offer letter management for one candidate. HR can prepare it at any time; the
 * backend keeps it invisible to the candidate until their documents are approved.
 * Acceptance is the last step of onboarding, so an accepted offer is final - the
 * upload controls disappear rather than silently failing server-side.
 */
export function OfferPanel({ candidate, offer, onChanged }) {
  const toast = useToast()
  const [notes, setNotes] = useState(offer?.notes || '')
  const [uploading, setUploading] = useState(false)
  const [viewing, setViewing] = useState(null)

  const uploadOffer = async (file) => {
    setUploading(true)
    try {
      await hrService.uploadOffer(candidate.id, file, notes)
      toast.success('Offer letter published', 'It unlocks for the candidate once documents are approved.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not upload the offer letter')
    } finally {
      setUploading(false)
    }
  }

  const offerMeta = offerStatusMeta(offer?.status)
  const documentsApproved = candidate.stage !== 'docs_pending'
  const offerAccepted = offer?.status === 'accepted'

  return (
    <>
      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.path}
        title={viewing?.title}
        filename={viewing?.filename}
      />

      <section className={`cf-card p-5 ${offerAccepted ? 'cf-spine cf-spine-green' : 'cf-spine'}`}>
        <header className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-[15px] font-semibold text-ink">Offer letter</h3>
            <p className="mt-0.5 text-[12px] text-ink-muted">
              The final step - accepting it completes this candidate&apos;s onboarding.
            </p>
          </div>
          <StatusPill label={offerMeta.label} tone={offerMeta.tone} />
        </header>

        {offer ? (
          <div className="mt-4 space-y-3">
            <div className="rounded border border-surface-line bg-surface-canvas px-3.5 py-3">
              <p className="truncate text-[13.5px] font-medium text-ink">{offer.filename}</p>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                {formatBytes(offer.sizeBytes)} &middot; published {formatDateTime(offer.sentAt)} by{' '}
                {offer.uploadedBy}
              </p>
            </div>
            <dl className="grid gap-2 text-[12.5px] sm:grid-cols-2 sm:gap-x-8">
              <Row
                label="Viewed by candidate"
                value={offer.viewedAt ? formatDateTime(offer.viewedAt) : 'Not yet'}
              />
              <Row
                label="Accepted"
                value={
                  offer.acceptedAt
                    ? `${formatDateTime(offer.acceptedAt)} · ${offer.acceptedByName}`
                    : 'Not yet'
                }
              />
              {offer.notes && <Row label="Internal notes" value={offer.notes} />}
            </dl>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="subtle"
                size="sm"
                onClick={() => setViewing({
                  path: offer.downloadUrl,
                  title: 'Offer letter',
                  filename: offer.filename,
                })}
              >
                View offer
              </Button>
              {!offerAccepted && (
                <span className="text-[12px] text-ink-muted">Uploading a new file replaces this one.</span>
              )}
            </div>
          </div>
        ) : (
          <p className="mt-3 text-[13px] leading-6 text-ink-muted">
            No offer letter yet.{' '}
            {documentsApproved
              ? 'This candidate is waiting on it - their offer stage is already unlocked.'
              : 'You can prepare it now; the candidate only sees it once their documents are approved.'}
          </p>
        )}

        {offerAccepted ? (
          <p className="mt-4 rounded border border-accent-green/30 bg-[#F3FCF7] px-3.5 py-2.5
            text-[12.5px] text-[#0E7A47]">
            Accepted and locked. Onboarding is complete for this candidate.
          </p>
        ) : (
          <div className="mt-4 space-y-3">
            <Field label="Internal note (optional)" htmlFor={`offer-notes-${candidate.id}`}>
              <TextInput
                id={`offer-notes-${candidate.id}`}
                value={notes}
                placeholder="Compensation band, joining window, approvals"
                onChange={(event) => setNotes(event.target.value)}
              />
            </Field>
            {uploading ? (
              <p className="text-[13px] text-ink-muted">Uploading offer letter...</p>
            ) : (
              <FileDropzone
                compact
                label={offer ? 'Replace offer letter' : 'Upload offer letter'}
                onSelect={uploadOffer}
              />
            )}
          </div>
        )}
      </section>
    </>
  )
}

function Row({ label, value }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-surface-line/70 pb-1.5
      last:border-0 last:pb-0">
      <dt className="text-ink-muted">{label}</dt>
      <dd className="text-right text-ink-body">{value}</dd>
    </div>
  )
}
