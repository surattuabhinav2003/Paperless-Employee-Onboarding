import { useState } from 'react'
import { Button } from '../ui/Button'
import { DocumentViewer } from '../ui/DocumentViewer'
import { FileDropzone } from '../ui/FileDropzone'
import { Field, TextInput } from '../ui/Field'
import { StatusPill } from '../ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatBytes, formatDateTime } from '../../utils/format'
import { bondStatusMeta, offerStatusMeta } from '../../utils/status'

/**
 * Offer and bond management for one candidate. HR can prepare both documents at
 * any time; the backend keeps them invisible to the candidate until their stage
 * unlocks them.
 */
export function OfferBondPanel({ candidate, offer, bond, onChanged }) {
  const toast = useToast()
  const [notes, setNotes] = useState(offer?.notes || '')
  const [version, setVersion] = useState(bond?.documentVersion || 'v1.0')
  const [uploading, setUploading] = useState(null)
  const [viewing, setViewing] = useState(null)

  const uploadOffer = async (file) => {
    setUploading('offer')
    try {
      await hrService.uploadOffer(candidate.id, file, notes)
      toast.success('Offer letter published', 'It unlocks for the candidate once documents are approved.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not upload the offer letter')
    } finally {
      setUploading(null)
    }
  }

  const uploadBond = async (file) => {
    setUploading('bond')
    try {
      await hrService.uploadBond(candidate.id, file, version)
      toast.success('Bond document published', 'It unlocks after the candidate accepts the offer.')
      onChanged?.()
    } catch (error) {
      toast.apiError(error, 'Could not upload the bond document')
    } finally {
      setUploading(null)
    }
  }

  const view = (path, title, filename) => setViewing({ path, title, filename })

  const download = async (path, filename) => {
    try {
      await hrService.downloadSecureFile(path, filename)
    } catch (error) {
      toast.apiError(error, 'Could not download the document')
    }
  }

  const offerMeta = offerStatusMeta(offer?.status)
  const bondMeta = bondStatusMeta(bond?.status)
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
    <div className="grid gap-5 lg:grid-cols-2">
      <section className="cf-card p-5">
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-[15px] font-semibold text-ink">Offer letter</h3>
          <StatusPill label={offerMeta.label} tone={offerMeta.tone} />
        </header>

        {offer ? (
          <div className="mt-4 space-y-3">
            <div className="rounded border border-surface-line bg-surface-offwhite/60 px-3.5 py-3">
              <p className="truncate text-[13.5px] font-medium text-ink">{offer.filename}</p>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                {formatBytes(offer.sizeBytes)} &middot; published {formatDateTime(offer.sentAt)} by{' '}
                {offer.uploadedBy}
              </p>
            </div>
            <dl className="grid gap-2 text-[12.5px]">
              <Row label="Viewed by candidate" value={offer.viewedAt ? formatDateTime(offer.viewedAt) : 'Not yet'} />
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
            <div className="flex flex-wrap gap-2">
              <Button variant="subtle" size="sm"
                onClick={() => view(offer.downloadUrl, 'Offer letter', offer.filename)}>
                View offer
              </Button>
              {!offerAccepted && (
                <span className="text-[12px] text-ink-muted">
                  Uploading a new file replaces this one.
                </span>
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

        {!offerAccepted && (
          <div className="mt-4 space-y-3">
            <Field label="Internal note (optional)" htmlFor={`offer-notes-${candidate.id}`}>
              <TextInput
                id={`offer-notes-${candidate.id}`}
                value={notes}
                placeholder="Compensation band, joining window, approvals"
                onChange={(event) => setNotes(event.target.value)}
              />
            </Field>
            {uploading === 'offer' ? (
              <p className="text-[13px] text-ink-muted">Uploading offer letter...</p>
            ) : (
              <FileDropzone compact label={offer ? 'Replace offer letter' : 'Upload offer letter'}
                onSelect={uploadOffer} />
            )}
          </div>
        )}
      </section>

      <section className="cf-card p-5">
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-[15px] font-semibold text-ink">Employment bond</h3>
          <StatusPill label={bondMeta.label} tone={bondMeta.tone} />
        </header>

        {bond ? (
          <div className="mt-4 space-y-3">
            <div className="rounded border border-surface-line bg-surface-offwhite/60 px-3.5 py-3">
              <p className="truncate text-[13.5px] font-medium text-ink">{bond.filename}</p>
              <p className="mt-0.5 text-[12px] text-ink-muted">
                Version {bond.documentVersion} &middot; {formatBytes(bond.sizeBytes)} &middot; uploaded{' '}
                {formatDateTime(bond.uploadedAt)}
              </p>
            </div>
            <dl className="grid gap-2 text-[12.5px]">
              <Row label="Signature request" value={bond.signatureRequestId || 'Not initiated'} mono />
              <Row label="Signature reference" value={bond.signatureRef || '--'} mono />
              <Row
                label="Signed"
                value={bond.signedAt ? `${formatDateTime(bond.signedAt)} · ${bond.signerName}` : 'Not yet'}
              />
              {bond.signerIp && <Row label="Signer IP" value={bond.signerIp} mono />}
              {bond.signedDocumentHash && (
                <Row label="Signed copy SHA-256" value={`${bond.signedDocumentHash.slice(0, 24)}...`} mono />
              )}
              {bond.failureReason && <Row label="Last failure" value={bond.failureReason} />}
            </dl>
            <div className="flex flex-wrap gap-2">
              <Button variant="subtle" size="sm"
                onClick={() => view(bond.downloadUrl, `Employment bond ${bond.documentVersion}`, bond.filename)}>
                View bond
              </Button>
              {bond.signedDownloadUrl && (
                <Button size="sm" onClick={() => download(bond.signedDownloadUrl, `signed-${bond.filename}`)}>
                  Download signed copy
                </Button>
              )}
            </div>
            {bond.auditTrail?.length > 0 && (
              <details className="rounded border border-surface-line bg-surface-offwhite/50 px-3 py-2">
                <summary className="cursor-pointer text-[12.5px] font-medium text-brand">
                  SignatureOne audit trail ({bond.auditTrail.length})
                </summary>
                <ol className="mt-2 space-y-2">
                  {bond.auditTrail.map((event, index) => (
                    <li key={index} className="text-[12px] text-ink-muted">
                      <span className="font-medium text-ink-body">{event.eventType}</span>{' '}
                      &middot; {formatDateTime(event.occurredAt)}
                      {event.actor ? ` · ${event.actor}` : ''}
                      {event.detail ? <span className="block">{event.detail}</span> : null}
                    </li>
                  ))}
                </ol>
              </details>
            )}
          </div>
        ) : (
          <p className="mt-3 text-[13px] leading-6 text-ink-muted">
            No bond document yet. Bond signing is always the final step - it unlocks only after the
            candidate accepts their offer.
          </p>
        )}

        {bond?.status !== 'signed' && (
          <div className="mt-4 space-y-3">
            <Field label="Document version" htmlFor={`bond-version-${candidate.id}`}
              hint="Recorded with the signature for audit purposes.">
              <TextInput
                id={`bond-version-${candidate.id}`}
                value={version}
                placeholder="v1.0"
                onChange={(event) => setVersion(event.target.value)}
              />
            </Field>
            {uploading === 'bond' ? (
              <p className="text-[13px] text-ink-muted">Uploading bond document...</p>
            ) : (
              <FileDropzone compact label={bond ? 'Replace bond document' : 'Upload bond document'}
                onSelect={uploadBond} />
            )}
          </div>
        )}
      </section>
    </div>
    </>
  )
}

function Row({ label, value, mono = false }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-surface-line/70 pb-1.5
      last:border-0 last:pb-0">
      <dt className="text-ink-muted">{label}</dt>
      <dd className={`text-right text-ink-body ${mono ? 'font-mono text-[11.5px]' : ''}`}>{value}</dd>
    </div>
  )
}
