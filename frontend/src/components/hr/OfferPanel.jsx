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
import { DocumentFieldEditor } from './DocumentFieldEditor'
import { DocumentFieldPreview } from './DocumentFieldPreview'

/**
 * Offer letter management for one candidate. HR can prepare it at any time; the
 * backend keeps it invisible to the candidate until their documents are approved.
 * Signing is the last step of onboarding, so a signed offer is final - the
 * upload controls disappear rather than silently failing server-side.
 */
export function OfferPanel({ candidate, offer, onChanged }) {
  const toast = useToast()
  const [notes, setNotes] = useState(offer?.notes || '')
  const [uploading, setUploading] = useState(false)
  const [sending, setSending] = useState(false)
  const [viewing, setViewing] = useState(null)
  const [preparing, setPreparing] = useState(false)

  const uploadOffer = async (file) => {
    setUploading(true)
    try {
      await hrService.uploadOffer(candidate.id, file, notes)
      toast.success('Offer letter uploaded', 'Review it below, then send it to the candidate.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not upload the offer letter')
    } finally {
      setUploading(false)
    }
  }

  const sendOffer = async () => {
    setSending(true)
    try {
      await hrService.sendOffer(candidate.id)
      toast.success('Offer letter sent', 'The candidate can now review it and sign.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not send the offer letter')
    } finally {
      setSending(false)
    }
  }

  const offerMeta = offerStatusMeta(offer?.status)
  const documentsApproved = candidate.stage !== 'docs_pending'
  const offerAccepted = offer?.status === 'accepted'
  const isDraft = offer?.status === 'draft'
  const fieldCount = offer?.fields?.length || 0

  // Once signed, the answers are baked into the PDF, so the plain viewer shows
  // the real artifact. Before that the field positions live only as metadata,
  // so the raw PDF would look empty - HR needs the overlay view instead.
  const showFieldPreview = Boolean(viewing) && !offerAccepted && fieldCount > 0

  return (
    <>
      <DocumentViewer
        open={Boolean(viewing) && !showFieldPreview}
        onClose={() => setViewing(null)}
        apiPath={viewing?.path}
        title={viewing?.title}
        filename={viewing?.filename}
      />

      <DocumentFieldPreview
        open={showFieldPreview}
        onClose={() => setViewing(null)}
        document={offer}
        title="Offer letter"
        onEdit={isDraft ? () => { setViewing(null); setPreparing(true) } : null}
      />

      {isDraft && (
        <DocumentFieldEditor
          open={preparing}
          onClose={() => setPreparing(false)}
          documentUrl={offer.downloadUrl}
          initialFields={offer.fields}
          onSave={(fields) => hrService.saveOfferFields(candidate.id, fields)}
          description="Drag a field from the left onto the letter. Drag to move, use the corner to resize."
          onSaved={() => {
            setPreparing(false)
            onChanged?.()
          }}
        />
      )}

      <section className={`cf-card p-5 ${offerAccepted ? 'cf-spine cf-spine-green' : 'cf-spine'}`}>
        <header className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-[15px] font-semibold text-ink">Offer letter</h3>
            <p className="mt-0.5 text-[12px] text-ink-muted">
              The final step - signing it completes this candidate&apos;s onboarding.
            </p>
          </div>
          <StatusPill label={offerMeta.label} tone={offerMeta.tone} />
        </header>

        {offer ? (
          <div className="mt-4 space-y-3">
            <div className="rounded border border-surface-line bg-surface-canvas px-3.5 py-3">
              <p className="truncate text-[13.5px] font-medium text-ink">{offer.filename}</p>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                {formatBytes(offer.sizeBytes)} &middot; {isDraft ? 'uploaded' : 'sent'}{' '}
                {formatDateTime(offer.sentAt)} by {offer.uploadedBy}
              </p>
            </div>
            <dl className="grid gap-2 text-[12.5px] sm:grid-cols-2 sm:gap-x-8">
              <Row
                label="Fields placed"
                value={fieldCount > 0 ? `${fieldCount} placed` : 'None placed yet'}
              />
              <Row
                label="Viewed by candidate"
                value={offer.viewedAt ? formatDateTime(offer.viewedAt) : 'Not yet'}
              />
              <Row
                label="Signed"
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
              {isDraft && (
                <Button variant="secondary" size="sm" onClick={() => setPreparing(true)}>
                  {fieldCount > 0 ? 'Edit fields' : 'Prepare for signature'}
                </Button>
              )}
              {isDraft && (
                <Button size="sm" onClick={sendOffer} disabled={sending || fieldCount === 0}>
                  {sending ? 'Sending...' : 'Send to candidate'}
                </Button>
              )}
              {!offerAccepted && !isDraft && (
                <span className="text-[12px] text-ink-muted">Uploading a new file replaces this one.</span>
              )}
            </div>
            {isDraft && (
              <p className="rounded border border-[#EBC88A] bg-[#FFF8EC] px-3.5 py-2.5
                text-[12.5px] leading-5 text-[#8A5A12]">
                {fieldCount === 0
                  ? 'Place at least one signature field before you can send this letter. ' +
                    'Click "Prepare for signature" above.'
                  : 'Not sent yet. The candidate cannot see this letter until you send it - review it ' +
                    'first, then send.'}{' '}
                Uploading a new file replaces this draft and clears any fields you placed.
              </p>
            )}
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
            Signed and locked. Onboarding is complete for this candidate.
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
