import { useState } from 'react'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'
import { Field, TextArea } from '../ui/Field'
import { DocumentViewer } from '../ui/DocumentViewer'
import { Modal } from '../ui/Modal'
import { StatusPill } from '../ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatBytes, formatDateTime } from '../../utils/format'
import { documentStatusMeta } from '../../utils/status'

/**
 * HR review surface for one candidate: open the file, verify it, or reject it
 * with a reason. All decisions go through the backend, which also decides when
 * the document stage is complete.
 */
export function DocumentReviewPanel({ documents = [], onChanged, readOnly = false }) {
  const toast = useToast()
  const [busyId, setBusyId] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState(null)
  const [viewing, setViewing] = useState(null)

  const verify = async (doc) => {
    setBusyId(doc.id)
    try {
      const updated = await hrService.verifyDocument(doc.id)
      toast.success('Document verified', `${doc.typeLabel} is now verified.`)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not verify the document')
    } finally {
      setBusyId(null)
    }
  }

  const submitRejection = async () => {
    if (reason.trim().length < 5) {
      setReasonError('Give the candidate a clear reason (at least 5 characters)')
      return
    }
    setBusyId(rejecting.id)
    try {
      const updated = await hrService.rejectDocument(rejecting.id, reason.trim())
      toast.success('Document rejected', 'The candidate can now upload a replacement.')
      setRejecting(null)
      setReason('')
      setReasonError(null)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not reject the document')
    } finally {
      setBusyId(null)
    }
  }

  if (!documents.length) {
    return (
      <EmptyState
        icon="document"
        title="No documents requested"
        message="This candidate has no document requirements configured."
      />
    )
  }

  return (
    <>
      {/* Rows rather than a fixed-width table, so the review actions stay
          reachable in both the full page and the narrower candidate drawer. */}
      <ul className="divide-y divide-surface-line">
        {documents.map((doc) => {
          const meta = documentStatusMeta(doc.status)
          const busy = busyId === doc.id
          return (
            <li key={doc.type} className="px-5 py-4 transition-colors hover:bg-brand-tint/25">
              <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
                <div className="min-w-[220px] flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[14px] font-medium text-ink">{doc.typeLabel}</span>
                    <StatusPill label={meta.label} tone={meta.tone} />
                    <span className="text-[10.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">
                      {doc.mandatory ? 'Mandatory' : 'Optional'}
                      {doc.version > 1 ? ` · v${doc.version}` : ''}
                    </span>
                    {doc.courseLabel && (
                      <span className="rounded-full bg-brand-tint px-2 py-0.5 text-[11px] font-medium text-brand">
                        {doc.courseLabel}
                      </span>
                    )}
                  </div>

                  {doc.filename ? (
                    <p className="mt-1 truncate text-[12.5px] text-ink-muted">
                      {doc.filename} &middot; {formatBytes(doc.sizeBytes)} &middot; uploaded{' '}
                      {formatDateTime(doc.uploadedAt)}
                    </p>
                  ) : (
                    <p className="mt-1 text-[12.5px] text-ink-muted">Not uploaded yet.</p>
                  )}

                  <p className="mt-0.5 text-[12.5px] text-ink-muted">
                    {doc.reviewedAt
                      ? `Reviewed by ${doc.reviewedBy} on ${formatDateTime(doc.reviewedAt)}`
                      : 'Not reviewed yet'}
                  </p>

                  {doc.status === 'rejected' && doc.rejectReason && (
                    <p className="mt-2 rounded bg-[#FFECEC] px-2.5 py-1.5 text-[12px] leading-5 text-[#8E1010]">
                      {doc.rejectReason}
                    </p>
                  )}
                </div>

                <div className="flex shrink-0 items-center gap-1.5">
                  {doc.downloadUrl && (
                    <Button variant="subtle" size="sm" onClick={() => setViewing(doc)}>
                      View
                    </Button>
                  )}
                  {!readOnly && doc.status === 'submitted' && (
                    <>
                      <Button variant="success" size="sm" loading={busy} onClick={() => verify(doc)}>
                        Verify
                      </Button>
                      <Button
                        variant="danger"
                        size="sm"
                        disabled={busy}
                        onClick={() => {
                          setRejecting(doc)
                          setReason('')
                          setReasonError(null)
                        }}
                      >
                        Reject
                      </Button>
                    </>
                  )}
                  {doc.status === 'pending' && (
                    <span className="text-[12px] text-ink-muted">Waiting on candidate</span>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      <Modal
        open={Boolean(rejecting)}
        onClose={() => setRejecting(null)}
        title={`Reject ${rejecting?.typeLabel || 'document'}`}
        description="The candidate sees this reason and can re-upload only this document."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRejecting(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={busyId === rejecting?.id} onClick={submitRejection}>
              Reject document
            </Button>
          </>
        }
      >
        <Field label="Rejection reason" htmlFor="reject-reason" required error={reasonError}
          hint="Be specific - this is the only guidance the candidate gets.">
          <TextArea
            id="reject-reason"
            rows={4}
            value={reason}
            error={reasonError}
            placeholder="The PAN card scan is cut off on the right edge. Please upload a full, clear copy."
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </Modal>
    </>
  )
}
